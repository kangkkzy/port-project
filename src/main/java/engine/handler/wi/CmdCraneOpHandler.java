package engine.handler.wi;

import common.consts.DeviceStateEnum;
import common.consts.DeviceTypeEnum;
import common.consts.EventTypeEnum;
import common.exception.BusinessException;
import engine.SimEvent;
import engine.SimEventHandler;
import engine.SimulationEngine;
import engine.context.GlobalContext;
import model.dto.request.CraneOperationReq;
import model.entity.BaseDevice;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 吊具操作通用处理 (Pick/Set)
 */
@Component
public class CmdCraneOpHandler implements SimEventHandler {

    private static final Logger log = LoggerFactory.getLogger(CmdCraneOpHandler.class);

    /** 起重机与集卡协同作业允许的最大空间距离（米）。交接区放大后最大跨度约 70 米 */
    private static final double MAX_CRANE_TRUCK_DISTANCE = 80.0;

    @Override
    public EventTypeEnum getType() {
        return EventTypeEnum.CMD_CRANE_OP;
    }

    @Override
    public void handle(SimEvent event, SimulationEngine engine, GlobalContext context) {
        CraneOperationReq req = (CraneOperationReq) event.getData();
        String craneId = req.getCraneId();
        BaseDevice crane = context.getDevice(craneId);

        // ── 1. 执行者必须是起重机 ──
        if (crane == null) {
            throw new BusinessException(String.format("抓放作业异常：起重机 [%s] 不存在", craneId));
        }
        if (crane.getType() != DeviceTypeEnum.QC && crane.getType() != DeviceTypeEnum.ASC) {
            throw new BusinessException(String.format(
                    "抓放作业异常：执行者 [%s] 类型为 %s，必须是起重机(QC/ASC)，集卡不能执行此指令",
                    craneId, crane.getType()));
        }

        // ── 2. 起重机不得处于移动状态 ──
        if (crane.getState() == DeviceStateEnum.MOVING) {
            throw new BusinessException(String.format("逻辑错误：起重机 [%s] 正在移动中，无法执行抓/放箱！", craneId));
        }

        // ── 3. 校验目标集卡并检查空间距离（双端在场校验）──
        String targetTruckId = req.getTargetTruckId();
        if (targetTruckId != null && !targetTruckId.isEmpty()) {
            BaseDevice truck = context.getDevice(targetTruckId);
            if (truck == null) {
                throw new BusinessException(String.format("协同作业异常：目标集卡 [%s] 不存在！", targetTruckId));
            }
            // 集卡类型：INTERNAL_TRUCK / EXTERNAL_TRUCK / OIL_TRUCK / ELECTRIC_TRUCK 均以 _TRUCK 结尾
            if (truck.getType() == null || !truck.getType().name().endsWith("_TRUCK")) {
                throw new BusinessException(String.format(
                        "协同作业异常：[%s] 不是集卡，类型为 %s", targetTruckId, truck.getType()));
            }

            // ── 强制安全锁：集卡必须处于 IDLE 静止状态 ──
            if (truck.getState() != DeviceStateEnum.IDLE) {
                throw new BusinessException(String.format(
                        "安全违规：目标集卡 [%s] 状态为 %s。必须等待集卡完全停稳到达目标点后，起重机才能执行抓放作业！",
                        targetTruckId, truck.getState()));
            }

            double distance = Math.hypot(
                    crane.getPosX() - truck.getPosX(),
                    crane.getPosY() - truck.getPosY());
            if (distance > MAX_CRANE_TRUCK_DISTANCE) {
                log.error("协同物理违规: 起重机 [{}] 与集卡 [{}] 距离 {:.1f}m 超出交接区范围 {}m",
                        craneId, targetTruckId, distance, MAX_CRANE_TRUCK_DISTANCE);
                throw new BusinessException(String.format(
                        "协同物理违规：起重机 [%s] 与集卡 [%s] 距离 %.1f 米，超出交接区最大范围 %.0f 米，必须同处交接区才能作业！",
                        craneId, targetTruckId, distance, MAX_CRANE_TRUCK_DISTANCE));
            }
        }

        crane.setState(DeviceStateEnum.WORKING);

        SimEvent opEvent = engine.scheduleEvent(event.getEventId(),
                context.getSimTime() + req.getDurationMS(), req.getAction(), null);
        opEvent.addSubject("CRANE", craneId);
    }
}
