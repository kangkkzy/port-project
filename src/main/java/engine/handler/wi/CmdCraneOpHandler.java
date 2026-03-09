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

    /** 起重机与集卡协同作业允许的最大空间距离（米）。适当放宽到 100 米以容纳极值情况 */
    private static final double MAX_TRANSFER_DISTANCE = 100.0;

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
                        "安全违规：目标集卡 [%s] 状态为 %s，未停稳前严禁抓放箱！",
                        targetTruckId, truck.getState()));
            }

            // 改用曼哈顿距离判断（更符合正交移动特征）
            double distance = Math.abs(crane.getPosX() - truck.getPosX()) + Math.abs(crane.getPosY() - truck.getPosY());

            if (distance > MAX_TRANSFER_DISTANCE) {
                log.error("协同物理违规: 起重机 [{}] 与集卡 [{}] 距离 {:.1f}m 超出交接区范围 {}m. 坐标: 吊机({:.1f}, {:.1f}), 卡车({:.1f}, {:.1f})",
                        craneId, targetTruckId, distance, MAX_TRANSFER_DISTANCE,
                        crane.getPosX(), crane.getPosY(), truck.getPosX(), truck.getPosY());
                throw new BusinessException(String.format(
                        "物理违规：起重机 [%s] 与 集卡 [%s] 距离为 %.1f 米，超出交接作业极值 %.1f 米！当前坐标: 吊机(%.1f, %.1f), 卡车(%.1f, %.1f)",
                        craneId, targetTruckId, distance, MAX_TRANSFER_DISTANCE,
                        crane.getPosX(), crane.getPosY(), truck.getPosX(), truck.getPosY()));
            }

            log.info("[CMD_CRANE_OP] 起重机 [{}] 与集卡 [{}] 距离 {:.1f}m（允许范围 {:.1f}m），作业合法。坐标: 吊机({:.1f}, {:.1f}), 卡车({:.1f}, {:.1f})",
                    craneId, targetTruckId, distance, MAX_TRANSFER_DISTANCE,
                    crane.getPosX(), crane.getPosY(), truck.getPosX(), truck.getPosY());
        }

        crane.setState(DeviceStateEnum.WORKING);

        SimEvent opEvent = engine.scheduleEvent(event.getEventId(),
                context.getSimTime() + req.getDurationMS(), req.getAction(), null);
        opEvent.addSubject("CRANE", craneId);
    }
}

