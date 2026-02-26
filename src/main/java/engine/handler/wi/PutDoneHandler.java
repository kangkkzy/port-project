package engine.handler.wi;

import common.consts.BizTypeEnum;
import common.consts.DeviceStateEnum;
import common.consts.EventTypeEnum;
import common.util.BizTypeUtil;
import common.util.GisUtil;
import engine.SimEvent;
import engine.SimEventHandler;
import engine.SimulationEngine;
import lombok.extern.slf4j.Slf4j;
import engine.context.GlobalContext;
import model.entity.BaseDevice;
import model.entity.Container;
import model.entity.Point;
import model.entity.WorkInstruction;
import org.springframework.stereotype.Component;

/**
 * 放箱完成 (PUT_DONE)
 * 更新箱子位置：设备 -> 目标位/集卡
 */
@Component
@Slf4j
public class PutDoneHandler implements SimEventHandler {

    @Override
    public EventTypeEnum getType() {
        return EventTypeEnum.PUT_DONE;
    }

    @Override
    public void handle(SimEvent event, SimulationEngine engine, GlobalContext context) {
        String deviceId = event.getPrimarySubject("CRANE");
        BaseDevice device = context.getDevice(deviceId);
        if (device != null) {
            device.setState(DeviceStateEnum.IDLE);

            String wiRefNo = device.getCurrWiRefNo();
            if (wiRefNo == null) {
                log.warn("事件[PUT_DONE]: 设备 [{}] 无指令", deviceId);
                return;
            }
            WorkInstruction wi = context.getWorkInstructionMap().get(wiRefNo);
            if (wi != null) {
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

                    // 2. 中转放箱 (放到集卡上)
                } else if (isFetchDevice && wi.getCarryCheId() != null) {
                    BaseDevice truck = context.getDevice(wi.getCarryCheId());

                    // 物理距离校验
                    if (truck != null) {
                        double dist = GisUtil.getDistance(
                                new Point(device.getPosX(), device.getPosY()),
                                new Point(truck.getPosX(), truck.getPosY())
                        );
                        if (dist > 5.0) {
                            log.error("严重错误: 设备 [{}] 距集卡 [{}] 过远 ({:.2f}m)，无法放箱。指令: {}",
                                    deviceId, truck.getId(), dist, wiRefNo);
                            return;
                        }
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
        }
    }
}

