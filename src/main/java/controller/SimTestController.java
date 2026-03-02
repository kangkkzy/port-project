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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 仿真测试场景接口 - 用于演示和调试
 *
 * 提供完整的业务流程测试场景，可通过前端按钮触发
 */
@RestController
@RequestMapping("/sim/test") // 修改了这里：增加了 /sim 前缀
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

        // 检查设备是否存在
        Truck truck = ctx.getTruckMap().get("TRUCK_01");
        if (truck == null) {
            return Result.error("请先加载包含 TRUCK_01 的测试场景");
        }

        // 清空设备状态
        truck.setState(DeviceStateEnum.IDLE);
        truck.setCurrentTargetPos(null);
        truck.setRemainingMoveTargets(new ArrayList<>());

        // 构建任务列表
        List<String> wiList = new ArrayList<>();
        wiList.add("WI_TEST_001");
        truck.setNotDoneWiList(wiList);

        // ======== 调度事件链 ========
        long baseTime = ctx.getSimTime();

        // 事件1: 集卡从当前位置出发，驶向道路 (175, 200)
        SimEvent event1 = createMoveEvent(baseTime, "TRUCK_01", 175.0, 200.0, 20.0);

        // 事件2: 到达道路后继续前往桥吊 (400, 200)
        SimEvent event2 = createMoveEvent(baseTime + 100, "TRUCK_01", 400.0, 200.0, 20.0);

        // 事件3: 到达桥吊下 (400, 140)
        SimEvent event3 = createMoveEvent(baseTime + 200, "TRUCK_01", 400.0, 140.0, 15.0);

        // 事件4: 卸箱操作 (模拟)
        SimEvent event4 = createCraneOperateEvent(baseTime + 300, "QC_01", "TRUCK_01", "PUT_DONE", 5000);

        // 事件5: 集卡返回道路 (400, 200)
        SimEvent event5 = createMoveEvent(baseTime + 400, "TRUCK_01", 400.0, 200.0, 20.0);

        // 事件6: 前往堆场B道路 (675, 200)
        SimEvent event6 = createMoveEvent(baseTime + 500, "TRUCK_01", 675.0, 200.0, 20.0);

        // 事件7: 到达堆场B (675, 300)
        SimEvent event7 = createMoveEvent(baseTime + 600, "TRUCK_01", 675.0, 300.0, 15.0);

        // 事件8: 装货操作 (模拟)
        SimEvent event8 = createCraneOperateEvent(baseTime + 700, "ASC_01", "TRUCK_01", "FETCH_DONE", 5000);

        return Result.success("已调度集卡完整业务流程测试，共8个事件");
    }

    /**
     * 执行桥吊QC业务流程测试
     * 场景：QC_01 从当前位置移动到目标位置，执行抓箱/放箱操作
     */
    @PostMapping("/qc-operation")
    public Result testQcOperation() {
        GlobalContext ctx = GlobalContext.getInstance();

        QcDevice qc = ctx.getQcMap().get("QC_01");
        if (qc == null) {
            return Result.error("请先加载包含 QC_01 的测试场景");
        }

        long baseTime = ctx.getSimTime();

        // 事件1: QC 移动到新位置
        SimEvent event1 = createCraneMoveEvent(baseTime, "QC_01", "MOVE_HORIZONTAL", 100.0, 10.0);

        // 事件2: 执行放箱操作
        SimEvent event2 = createCraneOperateEvent(baseTime + 100, "QC_01", null, "PUT_DONE", 5000);

        // 事件3: QC 移动到另一个位置
        SimEvent event3 = createCraneMoveEvent(baseTime + 200, "QC_01", "MOVE_HORIZONTAL", -50.0, 10.0);

        // 事件4: 执行抓箱操作
        SimEvent event4 = createCraneOperateEvent(baseTime + 300, "QC_01", null, "FETCH_DONE", 5000);

        return Result.success("已调度QC桥吊业务流程测试，共4个事件");
    }

    /**
     * 执行龙门吊ASC业务流程测试
     * 场景：ASC_01 执行堆场内的箱子搬运
     */
    @PostMapping("/asc-operation")
    public Result testAscOperation() {
        GlobalContext ctx = GlobalContext.getInstance();

        AscDevice asc = ctx.getAscMap().get("ASC_01");
        if (asc == null) {
            return Result.error("请先加载包含 ASC_01 的测试场景");
        }

        long baseTime = ctx.getSimTime();

        // 事件1: ASC 移动到新位置
        SimEvent event1 = createCraneMoveEvent(baseTime, "ASC_01", "MOVE_VERTICAL", 50.0, 8.0);

        // 事件2: 执行抓箱操作
        SimEvent event2 = createCraneOperateEvent(baseTime + 100, "ASC_01", null, "FETCH_DONE", 3000);

        // 事件3: ASC 移动到目标位置
        SimEvent event3 = createCraneMoveEvent(baseTime + 200, "ASC_01", "MOVE_VERTICAL", -30.0, 8.0);

        // 事件4: 执行放箱操作
        SimEvent event4 = createCraneOperateEvent(baseTime + 300, "ASC_01", null, "PUT_DONE", 3000);

        return Result.success("已调度ASC龙门吊业务流程测试，共4个事件");
    }

    // ==================== 辅助方法 ====================

    /**
     * 创建集卡移动事件
     */
    private SimEvent createMoveEvent(long time, String truckId, double targetX, double targetY, double speed) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("speed", speed);
        payload.put("target", new Point(targetX, targetY));

        SimEvent event = engine.scheduleEvent(null, time, EventTypeEnum.CMD_MOVE, payload);
        event.addSubject("TRUCK", truckId);
        return event;
    }

    /**
     * 创建起重机移动事件
     */
    private SimEvent createCraneMoveEvent(long time, String craneId, String moveType, double distance, double speed) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("moveType", moveType);
        payload.put("distance", distance);
        payload.put("speed", speed);

        SimEvent event = engine.scheduleEvent(null, time, EventTypeEnum.CMD_CRANE_MOVE, payload);
        event.addSubject("CRANE", craneId);
        return event;
    }

    /**
     * 创建起重机操作事件
     */
    private SimEvent createCraneOperateEvent(long time, String craneId, String truckId, String action, int durationMs) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("action", action);
        payload.put("durationMS", durationMs);

        SimEvent event = engine.scheduleEvent(null, time, EventTypeEnum.CMD_CRANE_OP, payload);
        event.addSubject("CRANE", craneId);
        if (truckId != null) {
            event.addSubject("TRUCK", truckId);
        }
        return event;
    }
}