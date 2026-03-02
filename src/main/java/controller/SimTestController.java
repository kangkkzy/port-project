package controller;

import common.Result;
import common.consts.DeviceStateEnum;
import common.consts.EventTypeEnum;
import engine.SimulationEngine;
import engine.SimEvent;
import engine.context.GlobalContext;
import model.entity.AscDevice;
import model.entity.Point;
import model.entity.QcDevice;
import model.entity.Truck;
import model.dto.request.CraneMoveReq;
import model.dto.request.CraneOperationReq;
import model.dto.request.MoveCommandReq;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;

/**
 * 仿真测试场景接口 - 用于演示和调试
 *
 * 提供完整的业务流程测试场景，可通过前端按钮触发
 */
@RestController
@RequestMapping("/sim/test")
public class SimTestController {

    private final SimulationEngine engine;

    public SimTestController(SimulationEngine engine) {
        this.engine = engine;
    }

    /**
     * 执行集卡完整业务流程测试
     * 场景：TRUCK_01 从堆场A装载箱子 → 运送到桥吊下 → 卸箱 → 返回堆场
     */
    @PostMapping("/truck-delivery")
    public Result testTruckDelivery() {
        GlobalContext ctx = GlobalContext.getInstance();

        Truck truck = ctx.getTruckMap().get("TRUCK_01");
        if (truck == null) {
            return Result.error("请先加载包含 TRUCK_01 的测试场景");
        }

        // 清空设备状态
        truck.setState(DeviceStateEnum.IDLE);
        truck.setCurrentTargetPos(null);
        truck.setRemainingMoveTargets(new ArrayList<>());

        List<String> wiList = new ArrayList<>();
        wiList.add("WI_TEST_001");
        truck.setNotDoneWiList(wiList);

        // ======== 调度事件链 ========
        long baseTime = ctx.getSimTime();

        // 1. 集卡出发 (125米, 速度20 -> 耗时约 6.25秒)
        SimEvent event1 = createMoveEvent(baseTime, "TRUCK_01", 175.0, 200.0, 20.0);

        // 2. 7秒后，继续前往桥吊 (225米, 速度20 -> 耗时约 11.25秒)
        SimEvent event2 = createMoveEvent(baseTime + 7000, "TRUCK_01", 400.0, 200.0, 20.0);

        // 3. 20秒后，拐弯到桥吊下 (60米, 速度15 -> 耗时 4秒)
        SimEvent event3 = createMoveEvent(baseTime + 20000, "TRUCK_01", 400.0, 140.0, 15.0);

        // 4. 25秒后，QC卸箱作业 (耗时 5秒)
        SimEvent event4 = createCraneOperateEvent(baseTime + 25000, "QC_01", "TRUCK_01", "PUT_DONE", 5000);

        // 5. 31秒后，集卡驶出回到主路 (60米, 速度20 -> 耗时 3秒)
        SimEvent event5 = createMoveEvent(baseTime + 31000, "TRUCK_01", 400.0, 200.0, 20.0);

        // 6. 35秒后，开往堆场B道路 (275米, 速度20 -> 耗时约 13.75秒)
        SimEvent event6 = createMoveEvent(baseTime + 35000, "TRUCK_01", 675.0, 200.0, 20.0);

        // 7. 50秒后，到达堆场B里面 (100米, 速度15 -> 耗时约 6.6秒)
        SimEvent event7 = createMoveEvent(baseTime + 50000, "TRUCK_01", 675.0, 300.0, 15.0);

        // 8. 58秒后，ASC装货作业 (耗时 5秒)
        SimEvent event8 = createCraneOperateEvent(baseTime + 58000, "ASC_01", "TRUCK_01", "FETCH_DONE", 5000);

        return Result.success("已调度集卡完整业务流程测试，预计仿真时间跨度60秒左右");
    }

    /**
     * 执行桥吊QC业务流程测试
     */
    @PostMapping("/qc-operation")
    public Result testQcOperation() {
        GlobalContext ctx = GlobalContext.getInstance();
        QcDevice qc = ctx.getQcMap().get("QC_01");
        if (qc == null) return Result.error("请先加载包含 QC_01 的测试场景");

        qc.setState(DeviceStateEnum.IDLE);

        long baseTime = ctx.getSimTime();

        // 1. 移动 (距离100, 速度10 -> 耗时 10秒)
        SimEvent event1 = createCraneMoveEvent(baseTime, "QC_01", "MOVE_HORIZONTAL", 100.0, 10.0);

        // 2. 11秒后，执行放箱操作 (耗时 5秒)
        SimEvent event2 = createCraneOperateEvent(baseTime + 11000, "QC_01", null, "PUT_DONE", 5000);

        // 3. 17秒后，反向移动 (距离50, 速度10 -> 耗时 5秒)
        SimEvent event3 = createCraneMoveEvent(baseTime + 17000, "QC_01", "MOVE_HORIZONTAL", -50.0, 10.0);

        // 4. 23秒后，执行抓箱操作 (耗时 5秒)
        SimEvent event4 = createCraneOperateEvent(baseTime + 23000, "QC_01", null, "FETCH_DONE", 5000);

        return Result.success("已调度QC桥吊业务流程测试，共4个事件");
    }

    /**
     * 执行龙门吊ASC业务流程测试
     */
    @PostMapping("/asc-operation")
    public Result testAscOperation() {
        GlobalContext ctx = GlobalContext.getInstance();
        AscDevice asc = ctx.getAscMap().get("ASC_01");
        if (asc == null) return Result.error("请先加载包含 ASC_01 的测试场景");

        asc.setState(DeviceStateEnum.IDLE);
        long baseTime = ctx.getSimTime();

        // 1. 移动 (距离50, 速度8 -> 耗时 6.25秒)
        SimEvent event1 = createCraneMoveEvent(baseTime, "ASC_01", "MOVE_VERTICAL", 50.0, 8.0);

        // 2. 7.5秒后，执行抓箱操作 (耗时 3秒)
        SimEvent event2 = createCraneOperateEvent(baseTime + 7500, "ASC_01", null, "FETCH_DONE", 3000);

        // 3. 11.5秒后，移动到目标位置 (距离30, 速度8 -> 耗时 3.75秒)
        SimEvent event3 = createCraneMoveEvent(baseTime + 11500, "ASC_01", "MOVE_VERTICAL", -30.0, 8.0);

        // 4. 16秒后，执行放箱操作 (耗时 3秒)
        SimEvent event4 = createCraneOperateEvent(baseTime + 16000, "ASC_01", null, "PUT_DONE", 3000);

        return Result.success("已调度ASC龙门吊业务流程测试，共4个事件");
    }

    // ==================== 辅助方法 ====================

    private SimEvent createMoveEvent(long time, String truckId, double targetX, double targetY, double speed) {
        MoveCommandReq payload = new MoveCommandReq();
        payload.setTruckId(truckId);
        payload.setTargetPoint(new Point(targetX, targetY));
        payload.setSpeed(speed);

        SimEvent event = engine.scheduleEvent(null, time, EventTypeEnum.CMD_MOVE, payload);
        event.addSubject("TRUCK", truckId);
        return event;
    }

    private SimEvent createCraneMoveEvent(long time, String craneId, String moveType, double distance, double speed) {
        CraneMoveReq payload = new CraneMoveReq();
        payload.setCraneId(craneId);
        payload.setMoveType(DeviceStateEnum.valueOf(moveType));
        payload.setDistance(distance);
        payload.setSpeed(speed);

        SimEvent event = engine.scheduleEvent(null, time, EventTypeEnum.CMD_CRANE_MOVE, payload);
        event.addSubject("CRANE", craneId);
        return event;
    }

    private SimEvent createCraneOperateEvent(long time, String craneId, String truckId, String action, int durationMs) {
        CraneOperationReq payload = new CraneOperationReq();
        payload.setCraneId(craneId);
        payload.setAction(EventTypeEnum.valueOf(action));
        payload.setDurationMS(durationMs);

        SimEvent event = engine.scheduleEvent(null, time, EventTypeEnum.CMD_CRANE_OP, payload);
        event.addSubject("CRANE", craneId);
        if (truckId != null) {
            event.addSubject("TRUCK", truckId);
        }
        return event;
    }
}