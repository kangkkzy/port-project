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
 * 抓箱完成 (FETCH_DONE)
 * 更新箱子位置：地面/集卡 -> 设备
 */
@Component
@Slf4j
public class FetchDoneHandler implements SimEventHandler {

    @Override
    public EventTypeEnum getType() {
        return EventTypeEnum.FETCH_DONE;
    }

    @Override
    public void handle(SimEvent event, SimulationEngine engine, GlobalContext context) {
        String deviceId = event.getPrimarySubject("CRANE");
        BaseDevice device = context.getDevice(deviceId);
        if (device != null) {
            // 作业完成，释放状态
            device.setState(DeviceStateEnum.IDLE);

            String wiRefNo = device.getCurrWiRefNo();
            if (wiRefNo == null) {
                log.warn("事件[FETCH_DONE]: 设备 [{}] 未绑定作业指令，跳过。", deviceId);
                return;
            }
            WorkInstruction wi = context.getWorkInstructionMap().get(wiRefNo);
            if (wi != null) {
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
                            log.warn("事件[FETCH_DONE]: 设备 [{}] 与指令 [{}] 抓箱设备不匹配", deviceId, wiRefNo);
                        }
                    }
                    return;
                }

                // 物理距离校验：防止隔空抓箱
                if (wi.getCarryCheId() != null) {
                    Container c = context.getContainerMap().get(wi.getContainerId());
                    if (c != null && c.getCurrentPos().equals(wi.getCarryCheId())) {
                        BaseDevice truck = context.getDevice(wi.getCarryCheId());
                        if (truck != null) {
                            double dist = GisUtil.getDistance(
                                    new Point(device.getPosX(), device.getPosY()),
                                    new Point(truck.getPosX(), truck.getPosY())
                            );
                            if (dist > 5.0) {
                                log.error("严重错误: 设备 [{}] 距集卡 [{}] 过远 ({:.2f}m)，无法抓箱。指令: {}",
                                        deviceId, truck.getId(), dist, wiRefNo);
                                return;
                            }
                        }
                    }
                }

                // 更新位置
                if (wi.getContainerId() != null) {
                    Container container = context.getContainerMap().get(wi.getContainerId());
                    if (container != null) {
                        String oldPos = container.getCurrentPos();
                        container.setCurrentPos(device.getId());
                        log.info("[Time: {}] [FETCH_DONE] 设备 [{}] 抓取箱 [{}]。位置: {} -> {}",
                                context.getSimTime(), deviceId, container.getContainerId(), oldPos, device.getId());
                    } else {
                        log.warn("[FETCH_DONE] 箱号 {} 未找到", wi.getContainerId());
                    }
                }
            }
        }
    }
}


