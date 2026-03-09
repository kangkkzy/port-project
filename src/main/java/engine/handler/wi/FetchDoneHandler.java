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
 * 抓箱完成 (FETCH_DONE)
 * 更新箱子位置：地面/集卡 -> 设备
 * 同时更新 YardBlock 三维数组
 *
 * DES架构下的物理校验逻辑：
 * - 区分"绝对接触作业"与"跨距伸缩作业"
 * - QC/ASC 通过小车/悬臂与集卡进行跨距作业，无需完全重合
 */
@Component
@Slf4j
public class FetchDoneHandler implements SimEventHandler {

    private static final double DEFAULT_PROXIMITY_THRESHOLD = 5.0;  // 默认接触距离阈值
    private static final double QC_TRUCK_Y_OFFSET = 60.0;           // QC轨道(140)与集卡道路(200)的偏移

    // 堆场位置解析正则：YARD_A_01_02_03 表示 箱区_贝位_排号_层号
    private static final Pattern YARD_POS_PATTERN = Pattern.compile("YARD_(\\w+)_(\\d+)_(\\d+)_(\\d+)");

    private final MapDataService mapDataService;

    @Autowired
    public FetchDoneHandler(MapDataService mapDataService) {
        this.mapDataService = mapDataService;
    }

    @Override
    public EventTypeEnum getType() {
        return EventTypeEnum.FETCH_DONE;
    }

    @Override
    public void handle(SimEvent event, SimulationEngine engine, GlobalContext context) {
        String deviceId = event.getPrimarySubject("CRANE");
        BaseDevice device = context.getDevice(deviceId);
        if (device == null) {
            log.error("严重错误: 事件[FETCH_DONE]: 设备 [{}] 不存在，触发熔断暂停", deviceId);
            throw new BusinessException("设备 [" + deviceId + "] 不存在，FETCH_DONE事件无法处理");
        }

        // === 任务5：无WI手动测试模式兼容降级 ===
        String wiRefNo = device.getCurrWiRefNo();
        if (wiRefNo == null) {
            log.warn("设备 [{}] 执行 FETCH_DONE 事件，但无绑定作业指令(WI)。判定为手动测试模式，执行状态机降级释放。", deviceId);
            device.setState(DeviceStateEnum.IDLE);
            return;
        }

        // 作业完成，释放状态（延迟到降级检查之后）
        device.setState(DeviceStateEnum.IDLE);
        if (wiRefNo == null) {
            log.error("严重错误: 事件[FETCH_DONE]: 设备 [{}] 无作业指令，触发熔断暂停", deviceId);
            throw new BusinessException("设备 [" + deviceId + "] 无作业指令，FETCH_DONE事件无法处理");
        }

        WorkInstruction wi = context.getWorkInstructionMap().get(wiRefNo);
        if (wi == null) {
            log.error("严重错误: 事件[FETCH_DONE]: 作业指令 [{}] 不存在，触发熔断暂停", wiRefNo);
            throw new BusinessException("作业指令 [" + wiRefNo + "] 不存在，FETCH_DONE事件无法处理");
        }

        BizTypeEnum bizType = wi.getMoveKind();
        boolean isFetchDevice = wi.getFetchCheId() != null && device.getId().equals(wi.getFetchCheId());
        boolean isPutDevice = wi.getPutCheId() != null && device.getId().equals(wi.getPutCheId());
        boolean allowedFetch = isFetchDevice;

        // 特例：如果是放箱设备，且集卡已到位，也允许抓取（中转场景）
        if (!allowedFetch && isPutDevice && wi.getCarryCheId() != null && wi.getContainerId() != null) {
            Container c = context.getContainerMap().get(wi.getContainerId());
            if (c != null && wi.getCarryCheId().equals(c.getCurrentPos())) {
                allowedFetch = true;
            }
        }

        if (!allowedFetch) {
            if (BizTypeUtil.requiresFetchDevice(bizType)) {
                if (!isPutDevice || wi.getCarryCheId() == null) {
                    log.error("严重错误: 事件[FETCH_DONE]: 设备 [{}] 与指令 [{}] 抓箱设备不匹配，触发熔断暂停", deviceId, wiRefNo);
                    throw new BusinessException(String.format("设备 [%s] 与指令 [%s] 抓箱设备不匹配", deviceId, wiRefNo));
                }
            }
            return;
        }

        // 物理距离校验：区分绝对接触作业与跨距伸缩作业
        if (wi.getCarryCheId() != null) {
            Container c = context.getContainerMap().get(wi.getContainerId());
            if (c != null && c.getCurrentPos().equals(wi.getCarryCheId())) {
                BaseDevice truck = context.getDevice(wi.getCarryCheId());
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
                        log.error("严重错误: 事件[FETCH_DONE]: 设备 [{}] 与集卡 [{}] 不在交接区域内，无法抓箱。设备坐标: ({}, {}), 集卡坐标: ({}, {}), 区域: {}",
                                deviceId, truck.getId(), device.getPosX(), device.getPosY(), truck.getPosX(), truck.getPosY(), zoneName);
                        throw new BusinessException(String.format("设备 [%s] 与集卡 [%s] 不在交接区域内，无法抓箱", deviceId, truck.getId()));
                    }

                    // 根据设备类型选择校验方式
                    double allowedSpan = getAllowedOperationSpan(device, truck);
                    double actualDist = GisUtil.getDistance(
                            new Point(device.getPosX(), device.getPosY()),
                            new Point(truck.getPosX(), truck.getPosY())
                    );

                    if (actualDist > allowedSpan) {
                        log.error("严重错误: 设备 [{}] 距集卡 [{}] 过远 ({:.2f}m > 允许跨度 {:.2f}m)，无法抓箱，触发熔断暂停。指令: {}",
                                deviceId, truck.getId(), actualDist, allowedSpan, wiRefNo);
                        throw new BusinessException(String.format("设备 [%s] 距集卡 [%s] 过远 (%.2fm > %.2fm)，无法抓箱",
                                deviceId, truck.getId(), actualDist, allowedSpan));
                    }

                    log.info("[FETCH_DONE] 设备 [{}] 与集卡 [{}] 距离 {:.2f}m（允许跨度 {:.2f}m），作业合法",
                            deviceId, truck.getId(), actualDist, allowedSpan);
                }
            }
        }

        // 更新箱子位置
        if (wi.getContainerId() != null) {
            Container container = context.getContainerMap().get(wi.getContainerId());
            if (container != null) {
                String oldPos = container.getCurrentPos();

                // 如果箱子原来在堆场，从堆场三维数组中移除
                if (oldPos != null && oldPos.startsWith("YARD_")) {
                    removeContainerFromYard(context, oldPos, container);
                }

                // 更新箱子位置到设备上
                container.setCurrentPos(device.getId());
                log.info("[Time: {}] [FETCH_DONE] 设备 [{}] 抓取箱 [{}]。位置: {} -> {}",
                        context.getSimTime(), deviceId, container.getContainerId(), oldPos, device.getId());
            } else {
                log.warn("[FETCH_DONE] 箱号 {} 未找到", wi.getContainerId());
            }
        }
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
     * 获取设备允许的作业跨度
     *
     * DES架构设计：
     * - 绝对接触作业（如堆场内地面作业）：距离 < 5米
     * - 跨距伸缩作业（如QC/ASC与集卡）：允许较大的垂直/水平偏移
     */
    private double getAllowedOperationSpan(BaseDevice crane, BaseDevice truck) {
        DeviceTypeEnum craneType = crane.getType();

        if (craneType == DeviceTypeEnum.QC) {
            // 岸桥(QC)与集卡的跨距作业
            // QC 在水平轨道 Y=140，集卡在车道 Y=200
            // 允许 Y 方向偏移 60 米，X 方向相近即可
            return QC_TRUCK_Y_OFFSET + DEFAULT_PROXIMITY_THRESHOLD;
        }
        else if (craneType == DeviceTypeEnum.ASC) {
            // 龙门吊(ASC)与集卡的跨距作业
            // ASC 在垂直轨道 X=175/425/675，集卡在车道 Y=200
            // 允许 X 方向接近轨道，Y 方向在车道上
            return 100.0; // 近似认为 X 偏差 < 100 且 Y 在车道上即可
        }

        // 默认使用绝对接触阈值
        return DEFAULT_PROXIMITY_THRESHOLD;
    }
}


