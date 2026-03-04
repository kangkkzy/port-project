package engine.handler.wi;

import common.consts.BizTypeEnum;
import common.consts.DeviceStateEnum;
import common.consts.DeviceTypeEnum;
import common.consts.EventTypeEnum;
import common.exception.BusinessException;
import common.util.BizTypeUtil;
import common.util.GisUtil;
import engine.SimEvent;
import engine.SimEventHandler;
import engine.SimulationEngine;
import engine.context.GlobalContext;
import lombok.extern.slf4j.Slf4j;
import model.entity.BaseDevice;
import model.entity.Container;
import model.entity.Point;
import model.entity.WorkInstruction;
import model.entity.YardBlock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import service.algorithm.MapDataService;
import model.dto.config.TransferZoneDto;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 放箱完成 (PUT_DONE)
 * 更新箱子位置：设备 -> 目标位/集卡
 * 同时更新 YardBlock 三维数组
 *
 * DES架构下的物理校验逻辑：
 * - 区分"绝对接触作业"与"跨距伸缩作业"
 * - QC/ASC 通过小车/悬臂与集卡进行跨距作业，无需完全重合
 */
@Component
@Slf4j
public class PutDoneHandler implements SimEventHandler {

    private static final double DEFAULT_PROXIMITY_THRESHOLD = 5.0;  // 默认接触距离阈值
    private static final double QC_TRUCK_Y_OFFSET = 60.0;           // QC轨道(140)与集卡道路(200)的偏移

    // 堆场位置解析正则：YARD_A_01_02_03 表示 箱区_贝位_排号_层号
    private static final Pattern YARD_POS_PATTERN = Pattern.compile("YARD_(\\w+)_(\\d+)_(\\d+)_(\\d+)");

    private final MapDataService mapDataService;

    @Autowired
    public PutDoneHandler(MapDataService mapDataService) {
        this.mapDataService = mapDataService;
    }

    @Override
    public EventTypeEnum getType() {
        return EventTypeEnum.PUT_DONE;
    }

    @Override
    public void handle(SimEvent event, SimulationEngine engine, GlobalContext context) {
        String deviceId = event.getPrimarySubject("CRANE");
        BaseDevice device = context.getDevice(deviceId);
        if (device == null) {
            log.error("严重错误: 事件[PUT_DONE]: 设备 [{}] 不存在，触发熔断暂停", deviceId);
            throw new BusinessException("设备 [" + deviceId + "] 不存在，PUT_DONE事件无法处理");
        }

        device.setState(DeviceStateEnum.IDLE);

        String wiRefNo = device.getCurrWiRefNo();
        if (wiRefNo == null) {
            log.error("严重错误: 事件[PUT_DONE]: 设备 [{}] 无作业指令，触发熔断暂停", deviceId);
            throw new BusinessException("设备 [" + deviceId + "] 无作业指令，PUT_DONE事件无法处理");
        }

        WorkInstruction wi = context.getWorkInstructionMap().get(wiRefNo);
        if (wi == null) {
            log.error("严重错误: 事件[PUT_DONE]: 作业指令 [{}] 不存在，触发熔断暂停", wiRefNo);
            throw new BusinessException("作业指令 [" + wiRefNo + "] 不存在，PUT_DONE事件无法处理");
        }

        BizTypeEnum bizType = wi.getMoveKind();
        boolean isFetchDevice = wi.getFetchCheId() != null && device.getId().equals(wi.getFetchCheId());
        boolean isPutDevice = wi.getPutCheId() != null && device.getId().equals(wi.getPutCheId());

        // 1. 终点放箱
        if (isPutDevice) {
            if (wi.getContainerId() != null) {
                Container container = context.getContainerMap().get(wi.getContainerId());
                if (container != null && wi.getToPos() != null) {
                    String oldPos = container.getCurrentPos();

                    // 如果箱子原来在设备上（已抓取），需要处理
                    if (oldPos != null && oldPos.startsWith("YARD_")) {
                        // 箱子从另一个堆场位置移动
                        removeContainerFromYard(context, oldPos, container);
                    }

                    // 放箱到目标位置
                    container.setCurrentPos(wi.getToPos());

                    // 如果目标位置是堆场，放入堆场三维数组
                    if (wi.getToPos().startsWith("YARD_")) {
                        addContainerToYard(context, wi.getToPos(), container);
                    }

                    log.info("[Time: {}] [PUT_DONE] 设备 [{}] 放箱 [{}] 至最终位置 [{}]",
                            context.getSimTime(), deviceId, container.getContainerId(), wi.getToPos());
                }
            }
            SimEvent completeEvent = engine.scheduleEvent(event.getEventId(), context.getSimTime(), EventTypeEnum.WI_COMPLETE, null);
            completeEvent.addSubject("WI", device.getCurrWiRefNo());

            // 2. 中转放箱 (放到集卡上) - 支持跨距作业
        } else if (isFetchDevice && wi.getCarryCheId() != null) {
            BaseDevice truck = context.getDevice(wi.getCarryCheId());

            // 物理距离校验：区分绝对接触作业与跨距伸缩作业
            if (truck != null) {
                // 交接区域校验：QC/ASC 与集卡必须在同一交接区域内
                String zoneType = device.getType() == DeviceTypeEnum.QC ? "QC" : "ASC";
                boolean inValidZone = mapDataService.isTransferZoneValid(
                        zoneType,
                        device.getPosX(), device.getPosY(),
                        truck.getPosX(), truck.getPosY()
                );

                if (!inValidZone) {
                    TransferZoneDto zone = zoneType.equals("QC")
                            ? mapDataService.getTransferZoneForQc(device.getPosX())
                            : mapDataService.getTransferZoneForAsc(device.getPosX());
                    String zoneName = zone != null ? zone.getName() : "未知";
                    log.error("严重错误: 事件[PUT_DONE]: 设备 [{}] 与集卡 [{}] 不在交接区域内，无法放箱。设备坐标: ({}, {}), 集卡坐标: ({}, {}), 区域: {}",
                            deviceId, truck.getId(), device.getPosX(), device.getPosY(), truck.getPosX(), truck.getPosY(), zoneName);
                    throw new BusinessException(String.format("设备 [%s] 与集卡 [%s] 不在交接区域内，无法放箱", deviceId, truck.getId()));
                }

                double allowedSpan = getAllowedOperationSpan(device, truck);
                double actualDist = GisUtil.getDistance(
                        new Point(device.getPosX(), device.getPosY()),
                        new Point(truck.getPosX(), truck.getPosY())
                );

                if (actualDist > allowedSpan) {
                    log.error("严重错误: 设备 [{}] 距集卡 [{}] 过远 ({:.2f}m > 允许跨度 {:.2f}m)，无法放箱，触发熔断暂停。指令: {}",
                            deviceId, truck.getId(), actualDist, allowedSpan, wiRefNo);
                    throw new BusinessException(String.format("设备 [%s] 距集卡 [%s] 过远 (%.2fm > %.2fm)，无法放箱",
                            deviceId, truck.getId(), actualDist, allowedSpan));
                }

                log.info("[PUT_DONE] 设备 [{}] 与集卡 [{}] 距离 {:.2f}m（允许跨度 {:.2f}m），作业合法",
                        deviceId, truck.getId(), actualDist, allowedSpan);
            }

            if (wi.getContainerId() != null) {
                Container container = context.getContainerMap().get(wi.getContainerId());
                if (container != null) {
                    String oldPos = container.getCurrentPos();
                    container.setCurrentPos(wi.getCarryCheId());
                    log.info("[Time: {}] [PUT_DONE] 设备 [{}] 放箱 [{}] 至集卡 [{}] (中转). 从 [{}] 变更为 [{}]",
                            context.getSimTime(), deviceId, container.getContainerId(), wi.getCarryCheId(), oldPos, wi.getCarryCheId());
                }
            }
        } else if (BizTypeUtil.requiresPutDevice(bizType)) {
            log.warn("事件[PUT_DONE]: 设备 [{}] 既不是抓箱也不是放箱设备，指令: {}", deviceId, wiRefNo);
        }
    }

    /**
     * 获取设备允许的作业跨度
     */
    private double getAllowedOperationSpan(BaseDevice crane, BaseDevice truck) {
        DeviceTypeEnum craneType = crane.getType();

        if (craneType == DeviceTypeEnum.QC) {
            // 岸桥(QC)与集卡的跨距作业
            return QC_TRUCK_Y_OFFSET + DEFAULT_PROXIMITY_THRESHOLD;
        }
        else if (craneType == DeviceTypeEnum.ASC) {
            // 龙门吊(ASC)与集卡的跨距作业
            return 100.0;
        }

        return DEFAULT_PROXIMITY_THRESHOLD;
    }

    /**
     * 从堆场三维数组中移除箱子
     */
    private void removeContainerFromYard(GlobalContext context, String yardPos, Container container) {
        Matcher matcher = YARD_POS_PATTERN.matcher(yardPos);
        if (matcher.matches()) {
            String blockCode = matcher.group(1);
            int bay = Integer.parseInt(matcher.group(2));
            int row = Integer.parseInt(matcher.group(3));
            int tier = Integer.parseInt(matcher.group(4));

            YardBlock block = context.getYardBlockMap().get(blockCode);
            if (block != null) {
                Container removed = block.removeContainer(bay, row, tier);
                if (removed != null) {
                    log.debug("从堆场 {} 移除箱子 {}", yardPos, removed.getContainerId());
                }
            }
        }
    }

    /**
     * 将箱子放入堆场三维数组
     */
    private void addContainerToYard(GlobalContext context, String yardPos, Container container) {
        Matcher matcher = YARD_POS_PATTERN.matcher(yardPos);
        if (matcher.matches()) {
            String blockCode = matcher.group(1);
            int bay = Integer.parseInt(matcher.group(2));
            int row = Integer.parseInt(matcher.group(3));
            int tier = Integer.parseInt(matcher.group(4));

            YardBlock block = context.getYardBlockMap().get(blockCode);
            if (block != null) {
                boolean success = block.putContainer(bay, row, tier, container);
                if (success) {
                    log.debug("将箱子 {} 放入堆场 {}", container.getContainerId(), yardPos);
                } else {
                    log.warn("无法将箱子 {} 放入堆场 {}，位置可能已被占用", container.getContainerId(), yardPos);
                }
            }
        }
    }
}

