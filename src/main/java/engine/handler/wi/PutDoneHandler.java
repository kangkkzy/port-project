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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import service.algorithm.MapDataService;

/**
 * 放箱完成 (PUT_DONE)
 * 更新箱子位置：设备 -> 目标位/集卡
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
                    container.setCurrentPos(wi.getToPos());
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
}

