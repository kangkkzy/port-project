package controller;

import common.Result;
import common.consts.BizTypeEnum;
import common.consts.DeviceStateEnum;
import common.consts.EventTypeEnum;
import common.util.GisUtil;
import engine.SimulationEngine;
import engine.SimEvent;
import engine.context.GlobalContext;
import model.entity.AscDevice;
import model.entity.BaseDevice;
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
 * 关键规则（符合DES架构）：
 * 1. TRUCK只能在TRUCK_ROAD上移动 (Y=200, Y=550)
 * 2. ASC只能在ASC_RAIL垂直轨道上移动 (X=175, 425, 675)
 * 3. QC只能在QC_RAIL水平轨道上移动 (Y=140)
 * 4. 设备执行FETCH_DONE/PUT_DONE前必须先完成移动，状态为IDLE
 * 5. 每个FETCH_DONE/PUT_DONE之前必须先通过CMD_ASSIGN_TASK绑定作业指令
 * 6. 设备与集卡支持跨距作业：
 *    - QC与集卡：Y方向允许60米偏移 (QC轨道140 vs 集卡道路200)
 *    - ASC与集卡：X方向需要接近轨道位置
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
     * 1. 集卡从道路移动到QC下方 (Y=200 -> QC轨道X对应位置)
     * 2. QC从船上抓箱放到集卡 (QC在Y=140轨道X移动，集卡在Y=200车道)
     * 3. 集卡从QC下方移动到ASC下方
     * 4. ASC从集卡抓箱放到堆场 (ASC在X轨道移动，集卡在Y=200车道)
     *
     * DES架构说明：
     * - 外部算法（测试脚本）负责生成符合路网的轨迹点
     * - 集卡必须在TRUCK_ROAD (Y=200) 上行驶
     * - QC与集卡跨距作业：Y方向偏移60米 (140 -> 200)
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

        // 1. 初始化设备位置
        // QC在X=0, Y=140轨道
        qc.setPosX(0.0);
        qc.setPosY(0.0);  // QC实际位置是posX(在轨道上移动)，posY固定为0
        qc.setState(DeviceStateEnum.IDLE);
        qc.setCurrWiRefNo(null);
        qc.setCurrentTargetPos(null);

        // ASC在X=100 (对应轨道X=175附近), Y=0
        asc.setPosX(175.0);  // 对应ASC_RAIL X=175
        asc.setPosY(0.0);
        asc.setState(DeviceStateEnum.IDLE);
        asc.setCurrWiRefNo(null);
        asc.setCurrentTargetPos(null);

        // 集卡初始位置 - 在TRUCK_ROAD Y=200上
        truck.setPosX(0.0);
        truck.setPosY(200.0);  // 必须在合法道路 Y=200 上
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
        long currentTime = baseTime;

        // ======== 事件链 (动态时间计算) ========

        // === 阶段1: 集卡移动到QC下方 ===
        // 集卡从(0,200)移动到(0,200)（已在目标位置，使用轨迹点方式）
        // 轨迹：只有终点 (0,200)
        currentTime = createMoveEventDynamic(currentTime + 1000, "TRUCK_01", 0.0, 200.0, 20.0);

        // === 阶段2: QC装货到集卡 ===

        // 1. 指派任务给QC (固定偏移)
        currentTime = currentTime + 1000;
        createAssignTaskEvent(currentTime, "QC_01", "WI_QC_DSCH");

        // 2. QC从船上抓箱 - QC先移动到船边
        // QC在X=0，需要移动到X=-30(船边)，距离30米，速度10m/s
        currentTime = currentTime + 1000;
        currentTime = createCraneMoveEventDynamic(currentTime, 0, "QC_01", "MOVE_HORIZONTAL", 30.0, 10.0);

        // 3. QC抓箱 (操作耗时3000ms)
        currentTime = currentTime + 3000;
        createCraneOperateEvent(currentTime, "QC_01", "FETCH_DONE", 3000);

        // 4. QC移动回集卡上方 - 从X=-30回到X=0
        currentTime = currentTime + 500; // 操作间隔
        currentTime = createCraneMoveEventDynamic(currentTime, 0, "QC_01", "MOVE_HORIZONTAL", 30.0, 10.0);

        // 5. QC放箱到集卡 (操作耗时3000ms)
        // 集卡在(0,200)，QC在X=0，Y方向偏移60米(140->200)，在允许跨距内
        currentTime = currentTime + 3000;
        createCraneOperateEvent(currentTime, "QC_01", "PUT_DONE", 3000);

        // === 阶段3: 集卡移动到ASC下方 ===
        // 集卡从(0,200)移动到(175,200)
        // 轨迹点：先在Y=200道路直行
        currentTime = currentTime + 3000; // 等待QC作业完成
        currentTime = createMoveEventDynamic(currentTime + 1000, "TRUCK_01", 175.0, 200.0, 20.0);

        // === 阶段4: ASC从集卡抓箱 ===

        // 1. 指派任务给ASC (固定偏移)
        currentTime = currentTime + 1000;
        createAssignTaskEvent(currentTime, "ASC_01", "WI_ASC_DSCH");

        // 2. ASC从集卡抓箱 (操作耗时3000ms)
        // ASC在X=175，集卡在(175,200)，X相同，Y偏移200米在允许范围内
        currentTime = currentTime + 1000;
        currentTime = currentTime + 3000;
        createCraneOperateEvent(currentTime, "ASC_01", "FETCH_DONE", 3000);

        // 3. ASC移动到堆场 - ASC在Y=0，需要移动到Y=30(堆场)
        currentTime = currentTime + 500; // 操作间隔
        currentTime = createCraneMoveEventDynamic(currentTime, 0, "ASC_01", "MOVE_VERTICAL", 30.0, 8.0);

        // 4. ASC放箱到堆场 (操作耗时3000ms)
        currentTime = currentTime + 3000;
        createCraneOperateEvent(currentTime, "ASC_01", "PUT_DONE", 3000);

        return Result.success("已调度集卡完整业务流程测试(DSCH) - 使用动态时间计算");
    }

    /**
     * 执行桥吊QC装船业务流程测试 (LOAD)
     * 场景：集卡到达 -> QC从集卡抓箱放到船上
     *
     * 物理流程：
     * 1. 集卡在TRUCK_ROAD (Y=200) 等待
     * 2. QC从集卡抓箱 (跨距作业，Y方向偏移60米)
     * 3. QC移动到船边放箱
     */
    @PostMapping("/qc-loading")
    public Result testQcLoading() {
        GlobalContext ctx = GlobalContext.getInstance();
        QcDevice qc = ctx.getQcMap().get("QC_01");
        Truck truck = ctx.getTruckMap().get("TRUCK_01");

        if (qc == null) return Result.error("请先加载包含 QC_01 的测试场景");
        if (truck == null) return Result.error("请先加载包含 TRUCK_01 的测试场景");

        // 1. 初始化设备位置
        // QC在X=50, Y=140轨道
        qc.setPosX(50.0);
        qc.setPosY(0.0);  // QC位置由posX决定，posY固定
        qc.setState(DeviceStateEnum.IDLE);
        qc.setCurrWiRefNo(null);
        qc.setCurrentTargetPos(null);

        // 集卡在TRUCK_ROAD Y=200
        truck.setPosX(50.0);
        truck.setPosY(200.0);  // 必须在合法道路 Y=200 上
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
        long currentTime = baseTime;

        // 1. 指派任务给QC
        currentTime = currentTime + 1000;
        createAssignTaskEvent(currentTime, "QC_01", "WI_QC_LOAD");

        // 2. QC从集卡抓箱 (操作耗时3000ms)
        // 集卡在(50,200)，QC在X=50，Y方向偏移60米(140->200)，在允许跨距内
        currentTime = currentTime + 1000;
        createCraneOperateEvent(currentTime, "QC_01", "FETCH_DONE", 3000);

        // 3. QC移动到船边 - 从X=50移动到X=20(船边)，距离30米，速度10m/s
        // 动态计算耗时: 30 / 10 = 3000ms
        currentTime = currentTime + 3000;
        currentTime = createCraneMoveEventDynamic(currentTime, 0, "QC_01", "MOVE_HORIZONTAL", 30.0, 10.0);

        // 4. QC放箱到船上 (操作耗时3000ms)
        currentTime = currentTime + 3000;
        createCraneOperateEvent(currentTime, "QC_01", "PUT_DONE", 3000);

        return Result.success("已调度QC装船业务流程测试(LOAD) - 使用动态时间计算");
    }

    /**
     * 执行龙门吊ASC卸箱业务流程测试 (DLVR)
     * 场景：集卡到达 -> ASC从集卡抓箱放到堆场
     *
     * 物理流程：
     * 1. 集卡在TRUCK_ROAD (Y=200) 移动到ASC轨道X位置
     * 2. ASC从集卡抓箱 (跨距作业，X方向接近轨道)
     * 3. ASC移动到堆场放箱
     */
    @PostMapping("/asc-unloading")
    public Result testAscUnloading() {
        GlobalContext ctx = GlobalContext.getInstance();
        AscDevice asc = ctx.getAscMap().get("ASC_01");
        Truck truck = ctx.getTruckMap().get("TRUCK_01");

        if (asc == null) return Result.error("请先加载包含 ASC_01 的测试场景");
        if (truck == null) return Result.error("请先加载包含 TRUCK_01 的测试场景");

        // 1. 初始化设备位置
        // ASC在X=175 (对应ASC_RAIL)
        asc.setPosX(175.0);
        asc.setPosY(0.0);
        asc.setState(DeviceStateEnum.IDLE);
        asc.setCurrWiRefNo(null);
        asc.setCurrentTargetPos(null);

        // 集卡在TRUCK_ROAD Y=200
        truck.setPosX(175.0);
        truck.setPosY(200.0);  // 必须在合法道路 Y=200 上
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
        long currentTime = baseTime;

        // 1. 指派任务给ASC
        currentTime = currentTime + 1000;
        createAssignTaskEvent(currentTime, "ASC_01", "WI_ASC_DLVR");

        // 2. ASC从集卡抓箱 (操作耗时3000ms)
        // ASC在X=175，集卡在(175,200)，X相同，Y偏移在允许范围内
        currentTime = currentTime + 1000;
        createCraneOperateEvent(currentTime, "ASC_01", "FETCH_DONE", 3000);

        // 3. ASC移动到堆场 - 从Y=0移动到Y=25(堆场)
        // 动态计算耗时: 25 / 8 = 3125ms
        currentTime = currentTime + 3000;
        currentTime = createCraneMoveEventDynamic(currentTime, 0, "ASC_01", "MOVE_VERTICAL", 25.0, 8.0);

        // 4. ASC放箱到堆场 (操作耗时3000ms)
        currentTime = currentTime + 3000;
        createCraneOperateEvent(currentTime, "ASC_01", "PUT_DONE", 3000);

        return Result.success("已调度ASC卸箱业务流程测试(DLVR) - 使用动态时间计算");
    }

    /**
     * 执行完整装船流程测试 (LOAD)
     * 场景：ASC装货 -> 集卡移动 -> QC装船
     *
     * 物理流程：
     * 1. ASC在X=175轨道，集卡在Y=200道路
     * 2. ASC抓箱放到集卡 (跨距作业)
     * 3. 集卡移动到QC下方 (Y=200道路)
     * 4. QC从集卡抓箱 (跨距作业)
     * 5. QC移动到船边放箱
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
        // ASC在X=175轨道
        asc.setPosX(175.0);
        asc.setPosY(0.0);
        asc.setState(DeviceStateEnum.IDLE);
        asc.setCurrWiRefNo(null);
        asc.setCurrentTargetPos(null);

        // 集卡在TRUCK_ROAD Y=200
        truck.setPosX(175.0);
        truck.setPosY(200.0);  // 必须在合法道路 Y=200 上
        truck.setState(DeviceStateEnum.IDLE);
        truck.setCurrentTargetPos(null);
        truck.setRemainingMoveTargets(new ArrayList<>());

        // QC在X=80, Y=140轨道
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
        long currentTime = baseTime;

        // === 阶段1: ASC装货到集卡 ===

        // 1. 指派任务给ASC
        currentTime = currentTime + 1000;
        createAssignTaskEvent(currentTime, "ASC_01", "WI_LOAD_FULL");

        // 2. ASC从堆场抓箱 (操作耗时3000ms)
        currentTime = currentTime + 1000;
        createCraneOperateEvent(currentTime, "ASC_01", "FETCH_DONE", 3000);

        // 3. ASC放箱到集卡 (操作耗时3000ms)
        // ASC在X=175，集卡在(175,200)，X相同，Y偏移在允许范围内
        currentTime = currentTime + 3000;
        createCraneOperateEvent(currentTime, "ASC_01", "PUT_DONE", 3000);

        // === 阶段2: 集卡移动到QC ===

        // 集卡从(175,200)移动到(80,200)，在Y=200道路上
        // 动态计算耗时: |175-80| / 20 = 4.75s = 4750ms
        currentTime = currentTime + 3000;
        currentTime = createMoveEventDynamic(currentTime + 1000, "TRUCK_01", 80.0, 200.0, 20.0);

        // === 阶段3: QC装船 ===

        // 1. 指派任务给QC
        currentTime = currentTime + 1000;
        createAssignTaskEvent(currentTime, "QC_01", "WI_LOAD_FULL");

        // 2. QC从集卡抓箱 (操作耗时3000ms)
        // 集卡在(80,200)，QC在X=80，Y方向偏移60米(140->200)，在允许跨距内
        currentTime = currentTime + 1000;
        createCraneOperateEvent(currentTime, "QC_01", "FETCH_DONE", 3000);

        // 3. QC移动到船边 - 从X=80移动到X=30(船边)
        // 动态计算耗时: 50 / 10 = 5000ms
        currentTime = currentTime + 3000;
        currentTime = createCraneMoveEventDynamic(currentTime, 0, "QC_01", "MOVE_HORIZONTAL", 50.0, 10.0);

        // 4. QC放箱到船上 (操作耗时3000ms)
        currentTime = currentTime + 3000;
        createCraneOperateEvent(currentTime, "QC_01", "PUT_DONE", 3000);

        return Result.success("已调度完整装船业务流程测试(LOAD) - 使用动态时间计算");
    }

    // ==================== 辅助方法 ====================

    /**
     * 动态计算移动耗时（毫秒）
     * 根据起点、终点和速度计算实际移动时间
     */
    private long calculateMoveTime(BaseDevice device, double targetX, double targetY, double speed) {
        Point currentPos = new Point(device.getPosX(), device.getPosY());
        Point targetPos = new Point(targetX, targetY);
        double distance = GisUtil.getDistance(currentPos, targetPos);
        return (long) ((distance / speed) * 1000);
    }

    /**
     * 创建集卡移动事件 (简化模式 - 引擎自动计算关键点)
     * TRUCK只能在TRUCK_ROAD上移动 (Y=200 或 Y=550)
     * 动态计算耗时：基于设备当前位置和目标位置计算实际移动时间
     *
     * @param baseTime 基准时间（通常为当前仿真时间）
     * @param truckId 集卡ID
     * @param targetX 目标X坐标
     * @param targetY 目标Y坐标
     * @param speed 移动速度
     * @return 返回事件触发时间，供后续事件链使用
     */
    private long createMoveEventDynamic(long baseTime, String truckId, double targetX, double targetY, double speed) {
        GlobalContext ctx = GlobalContext.getInstance();
        Truck truck = ctx.getTruckMap().get(truckId);
        if (truck == null) {
            return baseTime;
        }

        // 动态计算移动耗时
        long moveTime = calculateMoveTime(truck, targetX, targetY, speed);
        long triggerTime = baseTime + moveTime;

        MoveCommandReq payload = new MoveCommandReq();
        payload.setTruckId(truckId);
        payload.setTargetPoint(new Point(targetX, targetY));
        payload.setSpeed(speed);
        payload.setEnforcePathValidation(true);

        SimEvent event = engine.scheduleEvent(null, triggerTime, EventTypeEnum.CMD_MOVE, payload);
        event.addSubject("TRUCK", truckId);

        return triggerTime;
    }

    /**
     * 创建集卡移动事件 (带初始偏移的动态模式)
     * 第一个事件使用固定偏移，后续事件动态计算
     *
     * @param baseTime 基准时间
     * @param offsetMs 初始偏移（毫秒）
     * @param truckId 集卡ID
     * @param targetX 目标X坐标
     * @param targetY 目标Y坐标
     * @param speed 移动速度
     * @return 返回实际触发时间
     */
    private long createMoveEventWithOffset(long baseTime, long offsetMs, String truckId, double targetX, double targetY, double speed) {
        GlobalContext ctx = GlobalContext.getInstance();
        Truck truck = ctx.getTruckMap().get(truckId);
        if (truck == null) {
            return baseTime + offsetMs;
        }

        // 动态计算移动耗时
        long moveTime = calculateMoveTime(truck, targetX, targetY, speed);
        long triggerTime = baseTime + offsetMs + moveTime;

        MoveCommandReq payload = new MoveCommandReq();
        payload.setTruckId(truckId);
        payload.setTargetPoint(new Point(targetX, targetY));
        payload.setSpeed(speed);
        payload.setEnforcePathValidation(true);

        SimEvent event = engine.scheduleEvent(null, triggerTime, EventTypeEnum.CMD_MOVE, payload);
        event.addSubject("TRUCK", truckId);

        return triggerTime;
    }

    /**
     * 创建集卡移动事件 (精确模式 - 外部算法提供轨迹点)
     * 轨迹点必须位于合法的TRUCK_ROAD上
     *
     * @param time 触发时间
     * @param truckId 集卡ID
     * @param targetX 目标X坐标
     * @param targetY 目标Y坐标
     * @param speed 移动速度
     * @param pathPoints 轨迹点列表（可null表示使用简化模式）
     */
    private SimEvent createMoveEventWithPath(long time, String truckId, double targetX, double targetY, double speed, java.util.List<Point> pathPoints) {
        MoveCommandReq payload = new MoveCommandReq();
        payload.setTruckId(truckId);
        payload.setTargetPoint(new Point(targetX, targetY));
        payload.setSpeed(speed);
        payload.setPathPoints(pathPoints);
        payload.setEnforcePathValidation(true);

        SimEvent event = engine.scheduleEvent(null, time, EventTypeEnum.CMD_MOVE, payload);
        event.addSubject("TRUCK", truckId);

        if (pathPoints != null && !pathPoints.isEmpty()) {
            System.out.println("[移动指令] 集卡 " + truckId + " 轨迹点数: " + pathPoints.size());
        }

        return event;
    }

    /**
     * 创建吊机移动事件 - 动态耗时计算
     * ASC只能在垂直方向(MOVE_VERTICAL)移动
     * QC只能在水平方向(MOVE_HORIZONTAL)移动
     *
     * @param baseTime 基准时间
     * @param offsetMs 初始偏移（毫秒）
     * @param craneId 吊机ID
     * @param moveType 移动类型 (MOVE_HORIZONTAL/MOVE_VERTICAL)
     * @param distance 移动距离
     * @param speed 移动速度
     * @return 返回实际触发时间
     */
    private long createCraneMoveEventDynamic(long baseTime, long offsetMs, String craneId, String moveType, double distance, double speed) {
        // 动态计算耗时：时间 = 距离 / 速度
        long moveTime = (long) ((distance / speed) * 1000);
        long triggerTime = baseTime + offsetMs + moveTime;

        CraneMoveReq payload = new CraneMoveReq();
        payload.setCraneId(craneId);
        payload.setMoveType(DeviceStateEnum.valueOf(moveType));
        payload.setDistance(distance);
        payload.setSpeed(speed);

        SimEvent event = engine.scheduleEvent(null, triggerTime, EventTypeEnum.CMD_CRANE_MOVE, payload);
        event.addSubject("CRANE", craneId);

        return triggerTime;
    }

    /**
     * 创建吊机移动事件（保留原接口以兼容）
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
    private long createCraneOperateEvent(long time, String craneId, String action, int durationMs) {
        CraneOperationReq payload = new CraneOperationReq();
        payload.setCraneId(craneId);
        payload.setAction(EventTypeEnum.valueOf(action));
        payload.setDurationMS(durationMs);

        SimEvent event = engine.scheduleEvent(null, time, EventTypeEnum.CMD_CRANE_OP, payload);
        event.addSubject("CRANE", craneId);

        // 返回实际触发时间 = 事件时间 + 操作耗时
        return time + durationMs;
    }

    /**
     * 创建指派任务事件
     */
    private long createAssignTaskEvent(long time, String deviceId, String wiRefNo) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("wiRefNo", wiRefNo);

        SimEvent event = engine.scheduleEvent(null, time, EventTypeEnum.CMD_ASSIGN_TASK, payload);
        event.addSubject("DEVICE", deviceId);

        // 指派任务几乎瞬时完成，返回原时间
        return time;
    }
}