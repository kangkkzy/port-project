package engine.handler.movement;

import common.consts.DeviceStateEnum;
import common.consts.EventTypeEnum;
import common.exception.BusinessException;
import engine.SimEvent;
import engine.SimEventHandler;
import engine.SimulationEngine;
import engine.context.GlobalContext;
import model.dto.request.MoveCommandReq;
import model.entity.BaseDevice;
import model.entity.Point;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import service.algorithm.MapDataService;

import java.util.ArrayList;
import java.util.List;

/**
 * 集卡移动指令
 */
@Component
public class CmdMoveHandler implements SimEventHandler {

    private final MapDataService mapDataService;

    @Autowired
    public CmdMoveHandler(MapDataService mapDataService) {
        this.mapDataService = mapDataService;
    }

    @Override
    public EventTypeEnum getType() {
        return EventTypeEnum.CMD_MOVE;
    }

    @Override
    public void handle(SimEvent event, SimulationEngine engine, GlobalContext context) {
        String truckId = event.getPrimarySubject("TRUCK");
        BaseDevice device = context.getDevice(truckId);
        if (device == null) throw new BusinessException("移动指令异常: 设备不存在");

        if (device.getState() == DeviceStateEnum.WORKING || device.getState() == DeviceStateEnum.CHARGING) {
            throw new BusinessException(String.format("设备 %s 状态(%s)繁忙，无法执行移动", device.getId(), device.getState()));
        }

        // 🟢 修改点：直接将载荷转为 MoveCommandReq 对象
        MoveCommandReq payload = (MoveCommandReq) event.getData();
        Double speed = payload.getSpeed();
        if (speed == null || speed <= 0) {
            throw new BusinessException("移动参数非法: speed=" + speed);
        }

        Point target = payload.getTargetPoint();
        double startX = device.getPosX();
        double startY = device.getPosY();
        double endX = target.getX();
        double endY = target.getY();

        // 获取路径上的关键点
        String deviceType = device.getType().name();
        List<Double> keyPoints = mapDataService.getKeyPointsBetween(deviceType, startX, startY, endX, endY);

        // 将关键点转换为路径坐标点列表
        List<Point> remainingTargets = new ArrayList<>();
        boolean isHorizontal = Math.abs(endX - startX) > Math.abs(endY - startY);

        for (Double keyPoint : keyPoints) {
            if (isHorizontal) {
                remainingTargets.add(new Point(keyPoint, endY));
            } else {
                remainingTargets.add(new Point(endX, keyPoint));
            }
        }
        // 添加最终目标
        remainingTargets.add(target);

        // 将剩余目标列表存入设备
        device.setRemainingMoveTargets(remainingTargets);

        // 设置第一段的速度和目标
        device.setSpeed(speed);
        device.setCurrentTargetPos(remainingTargets.get(0));

        // 调度MOVE_START事件
        SimEvent moveStart = engine.scheduleEvent(event.getEventId(), context.getSimTime(), EventTypeEnum.MOVE_START, null);
        moveStart.addSubject("TRUCK", truckId);
    }
}