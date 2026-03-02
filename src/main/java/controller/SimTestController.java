package controller;

import common.Result;
import common.consts.BizTypeEnum;
import common.consts.DeviceStateEnum;
import common.consts.EventTypeEnum;
import engine.SimulationEngine;
import engine.SimEvent;
import engine.context.GlobalContext;
import model.entity.AscDevice;
import model.entity.Container;
import model.entity.Point;
import model.entity.QcDevice;
import model.entity.Truck;
import model.entity.WorkInstruction;
import model.dto.request.CraneMoveReq;
import model.dto.request.CraneOperationReq;
import model.dto.request.MoveCommandReq;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/**
 * 仿真测试场景接口 - 用于演示和调试
 *
 * 业务流程测试场景
 *
 * 关键规则：
 * 1. TRUCK只能在道路网上移动
 * 2. ASC只能在垂直轨道上移动 (Y轴方向)
 * 3. QC只能在水平轨道上移动 (X轴方向)
 * 4. 设备执行FETCH_DONE/PUT_DONE前必须先完成移动，状态为IDLE
 * 5. 每个FETCH_DONE/PUT_DONE之前必须先通过CMD_ASSIGN_TASK绑定作业指令
 * 6. 设备与集卡距离必须小于5米才能执行抓/放箱
 */
@RestController
@RequestMapping("/sim/test")
public class SimTestController {

    private final SimulationEngine engine;

    public SimTestController(SimulationEngine engine) {
        this.engine = engine;
    }

    /**
     * 执行集卡完整业务流程测试 (DSCH卸船)
     * 场景：集卡到达 -> QC装货 -> 集卡移动 -> ASC卸货
     *
     * 物理流程：
     * 1. 集卡从道路移动到QC下方
     * 2. QC从船上抓箱放到集卡 (QC在轨道X移动，集卡在道路Y=5)
     * 3. 集卡从QC下方移动到ASC下方
     * 4. ASC从集卡抓箱放到堆场 (ASC在轨道Y移动，集卡在道路)
     */
    @PostMapping("/truck-delivery")
    public Result testTruckDelivery() {
        GlobalContext ctx = GlobalContext.getInstance();

        Truck truck = ctx.getTruckMap().get("TRUCK_01");
        QcDevice qc = ctx.getQcMap().get("QC_01");
        AscDevice asc = ctx.getAscMap().get("ASC_01");

        if (truck == null) return Result.error("请先加载包含 TRUCK_01 的测试场景");
        if (qc == null) return Result.error("请先加载包含 QC_01 的测试场景");
        if (asc == null) return Result.error("请先加载包含 ASC_01 的测试场景");

        // 1. 初始化设备位置 - QC在X=0, ASC在Y=0，集卡在道路网上
        qc.setPosX(0.0);
        qc.setPosY(0.0);
        qc.setState(DeviceStateEnum.IDLE);
        qc.setCurrWiRefNo(null);
        qc.setCurrentTargetPos(null);

        asc.setPosX(100.0);
        asc.setPosY(0.0);
        asc.setState(DeviceStateEnum.IDLE);
        asc.setCurrWiRefNo(null);
        asc.setCurrentTargetPos(null);

        // 集卡初始位置
        truck.setPosX(-50.0);
        truck.setPosY(5.0);
        truck.setState(DeviceStateEnum.IDLE);
        truck.setCurrentTargetPos(null);
        truck.setRemainingMoveTargets(new ArrayList<>());

        // 2. 创建作业指令 (DSCH流程)
        WorkInstruction wiQc = new WorkInstruction();
        wiQc.setWiRefNo("WI_QC_DSCH");
        wiQc.setContainerId("CONT_DSCH");
        wiQc.setMoveKind(BizTypeEnum.DSCH);
        wiQc.setFetchCheId("QC_01");
        wiQc.setCarryCheId("TRUCK_01");
        wiQc.setFromPos("VESSEL_01");
        wiQc.setToPos("TRUCK_01");
        ctx.getWorkInstructionMap().put("WI_QC_DSCH", wiQc);

        WorkInstruction wiAsc = new WorkInstruction();
        wiAsc.setWiRefNo("WI_ASC_DSCH");
        wiAsc.setContainerId("CONT_DSCH");
        wiAsc.setMoveKind(BizTypeEnum.DSCH);
        wiAsc.setFetchCheId("ASC_01");
        wiAsc.setCarryCheId("TRUCK_01");
        wiAsc.setFromPos("TRUCK_01");
        wiAsc.setToPos("YARD_B");
        ctx.getWorkInstructionMap().put("WI_ASC_DSCH", wiAsc);

        // 3. 创建集装箱
        Container container = new Container();
        container.setContainerId("CONT_DSCH");
        container.setCurrentPos("VESSEL_01");
        ctx.getContainerMap().put("CONT_DSCH", container);

        long baseTime = ctx.getSimTime();

        // ======== 事件链 ========

        // === 阶段1: 集卡移动到QC下方 ===
        // 集卡从(-50,5)移动到(0,5)，距离50米，速度20m/s，耗时2.5秒=2500ms
        createMoveEvent(baseTime + 1000, "TRUCK_01", 0.0, 5.0, 20.0);
        // 集卡到达时间: 1000 + 2500 = 3500

        // === 阶段2: QC装货到集卡 ===

        // 1. 指派任务给QC
        createAssignTaskEvent(baseTime + 3600, "QC_01", "WI_QC_DSCH");

        // 2. QC从船上抓箱 - QC先移动到船边
        // QC在X=0，需要移动到X=-30(船边)，距离30米，速度10m/s，耗时3秒=3000ms
        createCraneMoveEvent(baseTime + 3700, "QC_01", "MOVE_HORIZONTAL", -30.0, 10.0);
        // QC到达时间: 3700 + 3000 = 6700

        // 3. QC抓箱 - 必须等QC移动完成(6700+)
        createCraneOperateEvent(baseTime + 6800, "QC_01", "FETCH_DONE", 3000);
        // FETCH_DONE完成时间: 6800 + 3000 = 9800

        // 4. QC移动回集卡上方 - 从X=-30回到X=0，距离30米
        createCraneMoveEvent(baseTime + 9900, "QC_01", "MOVE_HORIZONTAL", 30.0, 10.0);
        // QC到达时间: 9900 + 3000 = 12900

        // 5. QC放箱到集卡 - 集卡在(0,5)，QC在X=0，集卡距QC 5米
        createCraneOperateEvent(baseTime + 13000, "QC_01", "PUT_DONE", 3000);
        // PUT_DONE完成时间: 13000 + 3000 = 16000

        // === 阶段3: 集卡移动到ASC下方 ===
        // 集卡从(0,5)移动到(100,5)，距离100米，速度20m/s，耗时5秒=5000ms
        createMoveEvent(baseTime + 16500, "TRUCK_01", 100.0, 5.0, 20.0);
        // 集卡到达时间: 16500 + 5000 = 21500

        // === 阶段4: ASC从集卡抓箱 ===

        // 1. 指派任务给ASC
        createAssignTaskEvent(baseTime + 22000, "ASC_01", "WI_ASC_DSCH");

        // 2. ASC从集卡抓箱 - ASC在X=100，集卡在(100,5)，ASC距集卡5米
        createCraneOperateEvent(baseTime + 22500, "ASC_01", "FETCH_DONE", 3000);
        // FETCH_DONE完成时间: 22500 + 3000 = 25500

        // 3. ASC移动到堆场 - ASC在Y=0，需要移动到Y=30(堆场)，距离30米，速度8m/s
        createCraneMoveEvent(baseTime + 26000, "ASC_01", "MOVE_VERTICAL", 30.0, 8.0);
        // ASC到达时间: 26000 + 3750 = 29750

        // 4. ASC放箱到堆场
        createCraneOperateEvent(baseTime + 30000, "ASC_01", "PUT_DONE", 3000);

        return Result.success("已调度集卡完整业务流程测试(DSCH)");
    }

    /**
     * 执行桥吊QC装船业务流程测试 (LOAD)
     * 场景：集卡到达 -> QC从集卡抓箱放到船上
     */
    @PostMapping("/qc-loading")
    public Result testQcLoading() {
        GlobalContext ctx = GlobalContext.getInstance();
        QcDevice qc = ctx.getQcMap().get("QC_01");
        Truck truck = ctx.getTruckMap().get("TRUCK_01");

        if (qc == null) return Result.error("请先加载包含 QC_01 的测试场景");
        if (truck == null) return Result.error("请先加载包含 TRUCK_01 的测试场景");

        // 1. 初始化设备位置
        qc.setPosX(50.0);
        qc.setPosY(0.0);
        qc.setState(DeviceStateEnum.IDLE);
        qc.setCurrWiRefNo(null);
        qc.setCurrentTargetPos(null);

        truck.setPosX(50.0);
        truck.setPosY(5.0);
        truck.setState(DeviceStateEnum.IDLE);
        truck.setCurrentTargetPos(null);
        truck.setRemainingMoveTargets(new ArrayList<>());

        // 2. 创建作业指令
        WorkInstruction wi = new WorkInstruction();
        wi.setWiRefNo("WI_QC_LOAD");
        wi.setContainerId("CONT_LOAD");
        wi.setMoveKind(BizTypeEnum.LOAD);
        wi.setFetchCheId("QC_01");
        wi.setCarryCheId("TRUCK_01");
        wi.setFromPos("TRUCK_01");
        wi.setToPos("VESSEL_01");
        ctx.getWorkInstructionMap().put("WI_QC_LOAD", wi);

        // 3. 创建集装箱
        Container container = new Container();
        container.setContainerId("CONT_LOAD");
        container.setCurrentPos("TRUCK_01");
        ctx.getContainerMap().put("CONT_LOAD", container);

        long baseTime = ctx.getSimTime();

        // 1. 指派任务给QC
        createAssignTaskEvent(baseTime + 1000, "QC_01", "WI_QC_LOAD");

        // 2. QC从集卡抓箱 - 集卡在(50,5)，QC在(50,0)，距离5米
        createCraneOperateEvent(baseTime + 2000, "QC_01", "FETCH_DONE", 3000);
        // 完成时间: 2000 + 3000 = 5000

        // 3. QC移动到船边 - 从X=50移动到X=20(船边)，距离30米，速度10m/s
        createCraneMoveEvent(baseTime + 5100, "QC_01", "MOVE_HORIZONTAL", -30.0, 10.0);
        // 到达时间: 5100 + 3000 = 8100

        // 4. QC放箱到船上
        createCraneOperateEvent(baseTime + 8200, "QC_01", "PUT_DONE", 3000);

        return Result.success("已调度QC装船业务流程测试(LOAD)");
    }

    /**
     * 执行龙门吊ASC卸箱业务流程测试 (DLVR)
     * 场景：集卡到达 -> ASC从集卡抓箱放到堆场
     */
    @PostMapping("/asc-unloading")
    public Result testAscUnloading() {
        GlobalContext ctx = GlobalContext.getInstance();
        AscDevice asc = ctx.getAscMap().get("ASC_01");
        Truck truck = ctx.getTruckMap().get("TRUCK_01");

        if (asc == null) return Result.error("请先加载包含 ASC_01 的测试场景");
        if (truck == null) return Result.error("请先加载包含 TRUCK_01 的测试场景");

        // 1. 初始化设备位置
        asc.setPosX(30.0);
        asc.setPosY(0.0);
        asc.setState(DeviceStateEnum.IDLE);
        asc.setCurrWiRefNo(null);
        asc.setCurrentTargetPos(null);

        truck.setPosX(30.0);
        truck.setPosY(5.0);
        truck.setState(DeviceStateEnum.IDLE);
        truck.setCurrentTargetPos(null);
        truck.setRemainingMoveTargets(new ArrayList<>());

        // 2. 创建作业指令
        WorkInstruction wi = new WorkInstruction();
        wi.setWiRefNo("WI_ASC_DLVR");
        wi.setContainerId("CONT_DLVR");
        wi.setMoveKind(BizTypeEnum.DLVR);
        wi.setFetchCheId("ASC_01");
        wi.setCarryCheId("TRUCK_01");
        wi.setFromPos("TRUCK_01");
        wi.setToPos("YARD_B");
        ctx.getWorkInstructionMap().put("WI_ASC_DLVR", wi);

        // 3. 创建集装箱
        Container container = new Container();
        container.setContainerId("CONT_DLVR");
        container.setCurrentPos("TRUCK_01");
        ctx.getContainerMap().put("CONT_DLVR", container);

        long baseTime = ctx.getSimTime();

        // 1. 指派任务给ASC
        createAssignTaskEvent(baseTime + 1000, "ASC_01", "WI_ASC_DLVR");

        // 2. ASC从集卡抓箱 - 集卡在(30,5)，ASC在(30,0)，距离5米
        createCraneOperateEvent(baseTime + 2000, "ASC_01", "FETCH_DONE", 3000);
        // 完成时间: 2000 + 3000 = 5000

        // 3. ASC移动到堆场 - 从Y=0移动到Y=25(堆场)，距离25米，速度8m/s
        createCraneMoveEvent(baseTime + 5100, "ASC_01", "MOVE_VERTICAL", 25.0, 8.0);
        // 到达时间: 5100 + 3125 = 8225

        // 4. ASC放箱到堆场
        createCraneOperateEvent(baseTime + 8300, "ASC_01", "PUT_DONE", 3000);

        return Result.success("已调度ASC卸箱业务流程测试(DLVR)");
    }

    /**
     * 执行完整装船流程测试 (LOAD)
     * 场景：ASC装货 -> 集卡移动 -> QC装船
     */
    @PostMapping("/full-loading")
    public Result testFullLoading() {
        GlobalContext ctx = GlobalContext.getInstance();
        AscDevice asc = ctx.getAscMap().get("ASC_01");
        QcDevice qc = ctx.getQcMap().get("QC_01");
        Truck truck = ctx.getTruckMap().get("TRUCK_01");

        if (asc == null) return Result.error("请先加载包含 ASC_01 的测试场景");
        if (qc == null) return Result.error("请先加载包含 QC_01 的测试场景");
        if (truck == null) return Result.error("请先加载包含 TRUCK_01 的测试场景");

        // 1. 初始化设备位置
        asc.setPosX(0.0);
        asc.setPosY(0.0);
        asc.setState(DeviceStateEnum.IDLE);
        asc.setCurrWiRefNo(null);
        asc.setCurrentTargetPos(null);

        truck.setPosX(0.0);
        truck.setPosY(5.0);
        truck.setState(DeviceStateEnum.IDLE);
        truck.setCurrentTargetPos(null);
        truck.setRemainingMoveTargets(new ArrayList<>());

        qc.setPosX(80.0);
        qc.setPosY(0.0);
        qc.setState(DeviceStateEnum.IDLE);
        qc.setCurrWiRefNo(null);
        qc.setCurrentTargetPos(null);

        // 2. 创建作业指令
        WorkInstruction wi = new WorkInstruction();
        wi.setWiRefNo("WI_LOAD_FULL");
        wi.setContainerId("CONT_LOAD");
        wi.setMoveKind(BizTypeEnum.LOAD);
        wi.setFetchCheId("ASC_01");
        wi.setCarryCheId("TRUCK_01");
        wi.setPutCheId("QC_01");
        wi.setFromPos("YARD_A");
        wi.setToPos("VESSEL_01");
        ctx.getWorkInstructionMap().put("WI_LOAD_FULL", wi);

        // 3. 创建集装箱
        Container container = new Container();
        container.setContainerId("CONT_LOAD");
        container.setCurrentPos("YARD_A");
        ctx.getContainerMap().put("CONT_LOAD", container);

        long baseTime = ctx.getSimTime();

        // === 阶段1: ASC装货到集卡 ===

        // 1. 指派任务给ASC
        createAssignTaskEvent(baseTime + 1000, "ASC_01", "WI_LOAD_FULL");

        // 2. ASC从堆场抓箱 - 箱在YARD_A(集卡位置)
        createCraneOperateEvent(baseTime + 2000, "ASC_01", "FETCH_DONE", 3000);
        // 完成时间: 2000 + 3000 = 5000

        // 3. ASC放箱到集卡 - ASC在(0,0)，集卡在(0,5)
        createCraneOperateEvent(baseTime + 5100, "ASC_01", "PUT_DONE", 3000);
        // 完成时间: 5100 + 3000 = 8100

        // === 阶段2: 集卡移动到QC ===

        // 集卡从(0,5)移动到(80,5)，距离80米，速度20m/s，耗时4秒
        createMoveEvent(baseTime + 8200, "TRUCK_01", 80.0, 5.0, 20.0);
        // 到达时间: 8200 + 4000 = 12200

        // === 阶段3: QC装船 ===

        // 1. 指派任务给QC
        createAssignTaskEvent(baseTime + 12500, "QC_01", "WI_LOAD_FULL");

        // 2. QC从集卡抓箱 - 集卡在(80,5)，QC在(80,0)
        createCraneOperateEvent(baseTime + 13000, "QC_01", "FETCH_DONE", 3000);
        // 完成时间: 13000 + 3000 = 16000

        // 3. QC移动到船边 - 从X=80移动到X=30(船边)，距离50米
        createCraneMoveEvent(baseTime + 16500, "QC_01", "MOVE_HORIZONTAL", -50.0, 10.0);
        // 到达时间: 16500 + 5000 = 21500

        // 4. QC放箱到船上
        createCraneOperateEvent(baseTime + 22000, "QC_01", "PUT_DONE", 3000);

        return Result.success("已调度完整装船业务流程测试(LOAD)");
    }

    // ==================== 辅助方法 ====================

    /**
     * 创建集卡移动事件 (TRUCK只能在道路网上移动)
     */
    private SimEvent createMoveEvent(long time, String truckId, double targetX, double targetY, double speed) {
        MoveCommandReq payload = new MoveCommandReq();
        payload.setTruckId(truckId);
        payload.setTargetPoint(new Point(targetX, targetY));
        payload.setSpeed(speed);

        SimEvent event = engine.scheduleEvent(null, time, EventTypeEnum.CMD_MOVE, payload);
        event.addSubject("TRUCK", truckId);
        return event;
    }

    /**
     * 创建吊机移动事件
     * ASC只能在垂直方向(MOVE_VERTICAL)移动
     * QC只能在水平方向(MOVE_HORIZONTAL)移动
     */
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

    /**
     * 创建吊机操作事件 (FETCH_DONE/PUT_DONE)
     */
    private SimEvent createCraneOperateEvent(long time, String craneId, String action, int durationMs) {
        CraneOperationReq payload = new CraneOperationReq();
        payload.setCraneId(craneId);
        payload.setAction(EventTypeEnum.valueOf(action));
        payload.setDurationMS(durationMs);

        SimEvent event = engine.scheduleEvent(null, time, EventTypeEnum.CMD_CRANE_OP, payload);
        event.addSubject("CRANE", craneId);
        return event;
    }

    /**
     * 创建指派任务事件
     */
    private SimEvent createAssignTaskEvent(long time, String deviceId, String wiRefNo) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("wiRefNo", wiRefNo);

        SimEvent event = engine.scheduleEvent(null, time, EventTypeEnum.CMD_ASSIGN_TASK, payload);
        event.addSubject("DEVICE", deviceId);
        return event;
    }
}