package controller;

import common.Result;
import common.consts.BizTypeEnum;
import common.consts.DeviceStateEnum;
import common.consts.DeviceTypeEnum;
import common.consts.EventTypeEnum;
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
 * 仿真测试场景接口 - 使用 TimelineBuilder 链式构建安全时间轴
 *
 * 核心设计：虚拟坐标沙盘
 * - 在 TimelineBuilder 内部维护虚拟坐标，用于计算时间轴
 * - 不修改 GlobalContext 中的真实坐标（由引擎在事件执行时更新）
 * - 避免"过早坐标变异"导致的物理校验失败
 */
@RestController
@RequestMapping("/sim/test")
public class SimTestController {

    private final SimulationEngine engine;

    // 地图配置参数
    private double truckSpeed = 5.0;
    private double qcSpeed = 10.0;
    private double ascSpeed = 8.0;
    private double qcRailY = 140.0;        // QC轨道Y坐标
    private double truckRoadY = 200.0;      // 集卡道路Y坐标
    private double ascRailX1 = 175.0;
    private double ascRailX2 = 425.0;
    private double ascRailX3 = 675.0;

    // 操作耗时配置
    private int fetchPutDuration = 3000;    // 抓放箱操作耗时(ms)
    private int assignDuration = 500;       // 指派任务耗时(ms)
    private int safetyGap = 100;           // 安全时间间隔(ms)

    // QC物理约束：Y轴基础距离60米，X轴最大移动25米（保证直线距离≤65米）
    // √(60² + 25²) = √(3600 + 625) = √4225 = 65米（刚好满足）
    private double qcMaxHorizontalMove = 20.0;  // QC横向移动距离（保守值）

    public SimTestController(SimulationEngine engine) {
        this.engine = engine;
    }

    /**
     * 时间轴构建器 - 链式构建安全的操作序列
     *
     * 核心设计：虚拟坐标沙盘
     * - 仅在虚拟坐标 Map 中推演设备未来位置
     * - 不修改 GlobalContext 中的真实设备坐标
     * - 避免物理校验器看到"未来坐标"导致误判
     */
    private class TimelineBuilder {
        private long currentTime;
        private final GlobalContext ctx;

        // 虚拟坐标沙盘 - 仅用于时间推演，不影响真实引擎
        private final Map<String, Point> virtualTruckPos = new HashMap<>();
        private final Map<String, Point> virtualCranePos = new HashMap<>();

        public TimelineBuilder(long startTime, GlobalContext ctx) {
            this.currentTime = startTime;
            this.ctx = ctx;

            // 初始化虚拟坐标为当前真实坐标
            initVirtualPositions();
        }

        /**
         * 初始化虚拟坐标为当前真实坐标
         */
        private void initVirtualPositions() {
            // 集卡
            for (Truck truck : ctx.getTruckMap().values()) {
                virtualTruckPos.put(truck.getId(), new Point(truck.getPosX(), truck.getPosY()));
            }
            // QC
            for (QcDevice qc : ctx.getQcMap().values()) {
                virtualCranePos.put(qc.getId(), new Point(qc.getPosX(), qc.getPosY()));
            }
            // ASC
            for (AscDevice asc : ctx.getAscMap().values()) {
                virtualCranePos.put(asc.getId(), new Point(asc.getPosX(), asc.getPosY()));
            }
        }

        // ==================== 基础时间控制 ====================

        /**
         * 等待指定时间
         */
        public TimelineBuilder wait(int ms) {
            this.currentTime += ms;
            return this;
        }

        // ==================== 设备操作指令 ====================

        /**
         * 调度指派任务指令（耗时 + 安全间隔）
         */
        public TimelineBuilder assign(String deviceId, String wiRefNo) {
            Map<String, Object> payload = new HashMap<>();
            payload.put("wiRefNo", wiRefNo);
            engine.scheduleEvent(null, currentTime, EventTypeEnum.CMD_ASSIGN_TASK, payload)
                    .addSubject("DEVICE", deviceId);

            this.currentTime += assignDuration + safetyGap;
            return this;
        }

        /**
         * 调度集卡移动指令
         * - 从虚拟坐标计算距离和时间
         * - 在虚拟坐标中更新位置
         * - 不修改真实 GlobalContext 坐标
         */
        public TimelineBuilder moveTruck(String truckId, double targetX, double targetY, double speed) {
            // 从虚拟坐标获取起点
            Point startPos = virtualTruckPos.get(truckId);
            if (startPos == null) {
                startPos = new Point(0.0, truckRoadY);
                virtualTruckPos.put(truckId, startPos);
            }

            // 计算距离和耗时（基于虚拟坐标）
            double distance = Math.hypot(targetX - startPos.getX(), targetY - startPos.getY());
            long moveTimeMs = (long) ((distance / speed) * 1000);

            // 调度移动指令（在currentTime时刻）
            MoveCommandReq payload = new MoveCommandReq();
            payload.setTruckId(truckId);
            payload.setTargetPoint(new Point(targetX, targetY));
            payload.setSpeed(speed);
            payload.setEnforcePathValidation(true);
            engine.scheduleEvent(null, currentTime, EventTypeEnum.CMD_MOVE, payload)
                    .addSubject("TRUCK", truckId);

            // 更新虚拟坐标（供后续操作计算距离使用）- 不触碰真实坐标！
            virtualTruckPos.put(truckId, new Point(targetX, targetY));

            // 自动推进时间
            this.currentTime += moveTimeMs + safetyGap;
            return this;
        }

        /**
         * 调度吊机移动指令
         * - 基于虚拟坐标计算距离和时间
         * - 更新虚拟坐标
         */
        public TimelineBuilder moveCrane(String craneId, String moveType, double distance, double speed) {
            // 从虚拟坐标获取起点
            Point startPos = virtualCranePos.get(craneId);
            if (startPos == null) {
                startPos = new Point(0.0, qcRailY);
                virtualCranePos.put(craneId, startPos);
            }

            // 计算移动耗时
            long moveTimeMs = (long) ((Math.abs(distance) / speed) * 1000);

            // 调度移动指令
            CraneMoveReq payload = new CraneMoveReq();
            payload.setCraneId(craneId);
            payload.setMoveType(DeviceStateEnum.valueOf(moveType));
            payload.setDistance(distance);
            payload.setSpeed(speed);
            engine.scheduleEvent(null, currentTime, EventTypeEnum.CMD_CRANE_MOVE, payload)
                    .addSubject("CRANE", craneId);

            // 更新虚拟坐标（不触碰真实GlobalContext！）
            double newX = startPos.getX();
            double newY = startPos.getY();
            if ("MOVE_HORIZONTAL".equals(moveType)) {
                newX += distance;
            } else if ("MOVE_VERTICAL".equals(moveType)) {
                newY += distance;
            }
            virtualCranePos.put(craneId, new Point(newX, newY));

            // 自动推进时间
            this.currentTime += moveTimeMs + safetyGap;
            return this;
        }

        /**
         * 调度抓箱操作
         */
        public TimelineBuilder fetch(String craneId, int durationMs) {
            CraneOperationReq payload = new CraneOperationReq();
            payload.setCraneId(craneId);
            payload.setAction(EventTypeEnum.FETCH_DONE);
            payload.setDurationMS(durationMs);
            engine.scheduleEvent(null, currentTime, EventTypeEnum.CMD_CRANE_OP, payload)
                    .addSubject("CRANE", craneId);

            this.currentTime += durationMs + safetyGap;
            return this;
        }

        /**
         * 调度放箱操作
         */
        public TimelineBuilder put(String craneId, int durationMs) {
            CraneOperationReq payload = new CraneOperationReq();
            payload.setCraneId(craneId);
            payload.setAction(EventTypeEnum.PUT_DONE);
            payload.setDurationMS(durationMs);
            engine.scheduleEvent(null, currentTime, EventTypeEnum.CMD_CRANE_OP, payload)
                    .addSubject("CRANE", craneId);

            this.currentTime += durationMs + safetyGap;
            return this;
        }

        public long build() {
            return currentTime;
        }
    }

    // ==================== 环境初始化 ====================

    private void ensureTestEnvironment() {
        GlobalContext ctx = GlobalContext.getInstance();

        // 初始化集卡
        Truck truck = ctx.getTruckMap().get("TRUCK_01");
        if (truck == null) {
            truck = new Truck();
            truck.setId("TRUCK_01");
            truck.setType(DeviceTypeEnum.ELECTRIC_TRUCK);
            truck.setPosX(0.0);
            truck.setPosY(truckRoadY);
            truck.setState(DeviceStateEnum.IDLE);
            truck.setSpeed(truckSpeed);
            truck.setPowerLevel(100.0);
            truck.setConsumeRate(0.01);
            ctx.getTruckMap().put("TRUCK_01", truck);
        }

        // 初始化QC (posY = qcRailY = 140)
        QcDevice qc = ctx.getQcMap().get("QC_01");
        if (qc == null) {
            qc = new QcDevice();
            qc.setId("QC_01");
            qc.setType(DeviceTypeEnum.QC);
            qc.setPosX(0.0);
            qc.setPosY(qcRailY);
            qc.setState(DeviceStateEnum.IDLE);
            qc.setSpeed(qcSpeed);
            ctx.getQcMap().put("QC_01", qc);
        }

        // 初始化ASC (posY = truckRoadY = 200，与集卡同一水平线)
        AscDevice asc = ctx.getAscMap().get("ASC_01");
        if (asc == null) {
            asc = new AscDevice();
            asc.setId("ASC_01");
            asc.setType(DeviceTypeEnum.ASC);
            asc.setPosX(ascRailX1);
            asc.setPosY(truckRoadY);  // ASC在Y=200与集卡交接
            asc.setState(DeviceStateEnum.IDLE);
            asc.setSpeed(ascSpeed);
            ctx.getAscMap().put("ASC_01", asc);
        }
    }

    private void resetDevicesToInitialState() {
        GlobalContext ctx = GlobalContext.getInstance();

        Truck truck = ctx.getTruckMap().get("TRUCK_01");
        if (truck != null) {
            truck.setPosX(0.0);
            truck.setPosY(truckRoadY);
            truck.setState(DeviceStateEnum.IDLE);
            truck.setCurrWiRefNo(null);
            truck.setCurrentTargetPos(null);
            truck.setRemainingMoveTargets(new ArrayList<>());
        }

        QcDevice qc = ctx.getQcMap().get("QC_01");
        if (qc != null) {
            qc.setPosX(0.0);
            qc.setPosY(qcRailY);
            qc.setState(DeviceStateEnum.IDLE);
            qc.setCurrWiRefNo(null);
            qc.setCurrentTargetPos(null);
        }

        AscDevice asc = ctx.getAscMap().get("ASC_01");
        if (asc != null) {
            asc.setPosX(ascRailX1);
            asc.setPosY(truckRoadY);  // ASC在Y=200与集卡交接
            asc.setState(DeviceStateEnum.IDLE);
            asc.setCurrWiRefNo(null);
            asc.setCurrentTargetPos(null);
        }
    }

    // ==================== 测试接口实现 ====================

    @PostMapping("/truck-delivery")
    public Result testTruckDelivery() {
        GlobalContext ctx = GlobalContext.getInstance();

        ensureTestEnvironment();
        resetDevicesToInitialState();

        // 创建作业指令
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

        Container container = new Container();
        container.setContainerId("CONT_DSCH");
        container.setCurrentPos("VESSEL_01");
        container.setPosX(0.0);    // 船边位置
        container.setPosY(170.0);    // 船边与集卡之间
        ctx.getContainerMap().put("CONT_DSCH", container);

        // 使用 TimelineBuilder（传入 ctx 用于初始化虚拟坐标）
        TimelineBuilder timeline = new TimelineBuilder(ctx.getSimTime(), ctx);

        timeline.wait(1000);

        // === 阶段1: QC装货到集卡 ===
        timeline.assign("QC_01", "WI_QC_DSCH")
                .moveCrane("QC_01", "MOVE_HORIZONTAL", -20.0, qcSpeed)
                .fetch("QC_01", fetchPutDuration)
                .moveCrane("QC_01", "MOVE_HORIZONTAL", 20.0, qcSpeed)
                .put("QC_01", fetchPutDuration);

        // === 阶段2: 集卡移动到ASC下方 ===
        timeline.moveTruck("TRUCK_01", ascRailX1, truckRoadY, truckSpeed);

        // === 阶段3: ASC从集卡抓箱放到堆场 ===
        timeline.assign("ASC_01", "WI_ASC_DSCH")
                .fetch("ASC_01", fetchPutDuration)
                .moveCrane("ASC_01", "MOVE_VERTICAL", 30.0, ascSpeed)
                .put("ASC_01", fetchPutDuration);

        return Result.success("已调度集卡完整业务流程测试(DSCH)");
    }

    @PostMapping("/qc-loading")
    public Result testQcLoading() {
        GlobalContext ctx = GlobalContext.getInstance();

        ensureTestEnvironment();
        resetDevicesToInitialState();

        // 设置初始位置
        ctx.getQcMap().get("QC_01").setPosX(50.0);
        ctx.getTruckMap().get("TRUCK_01").setPosX(50.0);

        WorkInstruction wi = new WorkInstruction();
        wi.setWiRefNo("WI_QC_LOAD");
        wi.setContainerId("CONT_LOAD");
        wi.setMoveKind(BizTypeEnum.LOAD);
        wi.setFetchCheId("QC_01");
        wi.setCarryCheId("TRUCK_01");
        wi.setFromPos("TRUCK_01");
        wi.setToPos("VESSEL_01");
        ctx.getWorkInstructionMap().put("WI_QC_LOAD", wi);

        Container container = new Container();
        container.setContainerId("CONT_LOAD");
        container.setCurrentPos("TRUCK_01");
        container.setPosX(50.0);   // 集卡当前X坐标
        container.setPosY(truckRoadY);  // 集卡道路Y坐标
        ctx.getContainerMap().put("CONT_LOAD", container);

        TimelineBuilder timeline = new TimelineBuilder(ctx.getSimTime(), ctx);
        timeline.wait(1000);

        timeline.assign("QC_01", "WI_QC_LOAD")
                .fetch("QC_01", fetchPutDuration)
                .moveCrane("QC_01", "MOVE_HORIZONTAL", -20.0, qcSpeed)
                .put("QC_01", fetchPutDuration);

        return Result.success("已调度QC装船业务流程测试(LOAD)");
    }

    @PostMapping("/asc-unloading")
    public Result testAscUnloading() {
        GlobalContext ctx = GlobalContext.getInstance();

        ensureTestEnvironment();
        resetDevicesToInitialState();

        // 集卡在ASC轨道下方
        ctx.getTruckMap().get("TRUCK_01").setPosX(ascRailX1);

        WorkInstruction wi = new WorkInstruction();
        wi.setWiRefNo("WI_ASC_DLVR");
        wi.setContainerId("CONT_DLVR");
        wi.setMoveKind(BizTypeEnum.DLVR);
        wi.setFetchCheId("ASC_01");
        wi.setCarryCheId("TRUCK_01");
        wi.setFromPos("TRUCK_01");
        wi.setToPos("YARD_B");
        ctx.getWorkInstructionMap().put("WI_ASC_DLVR", wi);

        Container container = new Container();
        container.setContainerId("CONT_DLVR");
        container.setCurrentPos("TRUCK_01");
        container.setPosX(ascRailX1);  // ASC轨道X坐标
        container.setPosY(truckRoadY);  // 集卡道路Y坐标
        ctx.getContainerMap().put("CONT_DLVR", container);

        TimelineBuilder timeline = new TimelineBuilder(ctx.getSimTime(), ctx);
        timeline.wait(1000);

        timeline.assign("ASC_01", "WI_ASC_DLVR")
                .fetch("ASC_01", fetchPutDuration)
                .moveCrane("ASC_01", "MOVE_VERTICAL", 30.0, ascSpeed)
                .put("ASC_01", fetchPutDuration);

        return Result.success("已调度ASC卸箱业务流程测试(DLVR)");
    }

    @PostMapping("/full-loading")
    public Result testFullLoading() {
        GlobalContext ctx = GlobalContext.getInstance();

        ensureTestEnvironment();
        resetDevicesToInitialState();

        // 设置初始位置
        ctx.getTruckMap().get("TRUCK_01").setPosX(ascRailX1);
        ctx.getQcMap().get("QC_01").setPosX(80.0);

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

        Container container = new Container();
        container.setContainerId("CONT_LOAD");
        container.setCurrentPos("YARD_A");
        container.setPosX(ascRailX1);  // ASC轨道X坐标
        container.setPosY(truckRoadY);  // 集卡道路Y坐标
        ctx.getContainerMap().put("CONT_LOAD", container);

        TimelineBuilder timeline = new TimelineBuilder(ctx.getSimTime(), ctx);
        timeline.wait(1000);

        // === 阶段1: ASC装货到集卡 ===
        timeline.assign("ASC_01", "WI_LOAD_FULL")
                .fetch("ASC_01", fetchPutDuration)
                .put("ASC_01", fetchPutDuration);

        // === 阶段2: 集卡移动到QC ===
        timeline.moveTruck("TRUCK_01", 80.0, truckRoadY, truckSpeed);

        // === 阶段3: QC装船 ===
        timeline.assign("QC_01", "WI_LOAD_FULL")
                .fetch("QC_01", fetchPutDuration)
                .moveCrane("QC_01", "MOVE_HORIZONTAL", -20.0, qcSpeed)
                .put("QC_01", fetchPutDuration);

        return Result.success("已调度完整装船业务流程测试(LOAD)");
    }

    // ==================== 扩展业务测试接口 ====================

    /**
     * 场内移箱 (YARD_SHIFT)
     * ASC 从堆场 A 位置抓起箱子，移动到堆场 B 位置放下
     */
    @PostMapping("/yard-shift")
    public Result testYardShift() {
        GlobalContext ctx = GlobalContext.getInstance();

        ensureTestEnvironment();
        resetDevicesToInitialState();

        // 箱子在 YARD_A (X=175, Y=230)
        Container container = new Container();
        container.setContainerId("CONT_SHIFT");
        container.setCurrentPos("YARD_A");
        container.setPosX(ascRailX1);
        container.setPosY(230.0);
        ctx.getContainerMap().put("CONT_SHIFT", container);

        WorkInstruction wi = new WorkInstruction();
        wi.setWiRefNo("WI_YARD_SHIFT");
        wi.setContainerId("CONT_SHIFT");
        wi.setMoveKind(BizTypeEnum.YARD_SHIFT);
        wi.setFetchCheId("ASC_01");
        wi.setFromPos("YARD_A");
        wi.setToPos("YARD_B");
        ctx.getWorkInstructionMap().put("WI_YARD_SHIFT", wi);

        // ASC 已经在 Y=200 位置
        ctx.getAscMap().get("ASC_01").setPosY(200.0);

        TimelineBuilder timeline = new TimelineBuilder(ctx.getSimTime(), ctx);
        timeline.wait(1000);

        timeline.assign("ASC_01", "WI_YARD_SHIFT")
                .fetch("ASC_01", fetchPutDuration)
                .moveCrane("ASC_01", "MOVE_VERTICAL", 50.0, ascSpeed)  // 移到 YARD_B
                .put("ASC_01", fetchPutDuration);

        return Result.success("已调度 YARD_SHIFT 移箱测试");
    }

    /**
     * 外集卡收箱 (RECV) - 进场落箱
     * 外集卡从大门开到 ASC 交接位，ASC 从集卡抓箱放入堆场
     */
    @PostMapping("/recv")
    public Result testRecv() {
        GlobalContext ctx = GlobalContext.getInstance();

        ensureTestEnvironment();
        resetDevicesToInitialState();

        // 箱子在集卡上，集卡在 X=300 (大门位置)
        ctx.getTruckMap().get("TRUCK_01").setPosX(300.0);

        Container container = new Container();
        container.setContainerId("CONT_RECV");
        container.setCurrentPos("TRUCK_01");
        container.setPosX(300.0);
        container.setPosY(truckRoadY);
        ctx.getContainerMap().put("CONT_RECV", container);

        WorkInstruction wi = new WorkInstruction();
        wi.setWiRefNo("WI_RECV");
        wi.setContainerId("CONT_RECV");
        wi.setMoveKind(BizTypeEnum.RECV);
        wi.setFetchCheId("ASC_01");
        wi.setCarryCheId("TRUCK_01");
        wi.setFromPos("TRUCK_01");
        wi.setToPos("YARD_B");
        ctx.getWorkInstructionMap().put("WI_RECV", wi);

        // ASC 初始位置
        ctx.getAscMap().get("ASC_01").setPosX(ascRailX1);
        ctx.getAscMap().get("ASC_01").setPosY(truckRoadY);

        TimelineBuilder timeline = new TimelineBuilder(ctx.getSimTime(), ctx);
        timeline.wait(1000);

        // 集卡从大门开到 ASC 交接位
        timeline.moveTruck("TRUCK_01", ascRailX1, truckRoadY, truckSpeed)
                .assign("ASC_01", "WI_RECV")
                .fetch("ASC_01", fetchPutDuration)
                .moveCrane("ASC_01", "MOVE_VERTICAL", 30.0, ascSpeed)
                .put("ASC_01", fetchPutDuration);

        return Result.success("已调度 RECV 收箱测试");
    }

    /**
     * 直进 (DIRECT_IN) - 外集卡直接装船
     * 外集卡从大门直接开到 QC 下方，QC 直接抓箱装船（不经过堆场）
     */
    @PostMapping("/direct-in")
    public Result testDirectIn() {
        GlobalContext ctx = GlobalContext.getInstance();

        ensureTestEnvironment();
        resetDevicesToInitialState();

        // 箱子在集卡上，集卡在 X=300 (大门位置)
        ctx.getTruckMap().get("TRUCK_01").setPosX(300.0);

        Container container = new Container();
        container.setContainerId("CONT_DIRECT_IN");
        container.setCurrentPos("TRUCK_01");
        container.setPosX(300.0);
        container.setPosY(truckRoadY);
        ctx.getContainerMap().put("CONT_DIRECT_IN", container);

        WorkInstruction wi = new WorkInstruction();
        wi.setWiRefNo("WI_DIRECT_IN");
        wi.setContainerId("CONT_DIRECT_IN");
        wi.setMoveKind(BizTypeEnum.DIRECT_IN);
        wi.setFetchCheId("QC_01");
        wi.setCarryCheId("TRUCK_01");
        wi.setFromPos("TRUCK_01");
        wi.setToPos("VESSEL_01");
        ctx.getWorkInstructionMap().put("WI_DIRECT_IN", wi);

        TimelineBuilder timeline = new TimelineBuilder(ctx.getSimTime(), ctx);
        timeline.wait(1000);

        // 集卡直接开到 QC 下方
        timeline.moveTruck("TRUCK_01", 80.0, truckRoadY, truckSpeed)
                .assign("QC_01", "WI_DIRECT_IN")
                .fetch("QC_01", fetchPutDuration)
                .moveCrane("QC_01", "MOVE_HORIZONTAL", -20.0, qcSpeed)
                .put("QC_01", fetchPutDuration);

        return Result.success("已调度 DIRECT_IN 直进测试");
    }

    /**
     * 直提 (DIRECT_OUT) - 卸船直接上外集卡出大门
     * QC 从船上抓箱放到集卡，集卡直接开出大门
     */
    @PostMapping("/direct-out")
    public Result testDirectOut() {
        GlobalContext ctx = GlobalContext.getInstance();

        ensureTestEnvironment();
        resetDevicesToInitialState();

        // 箱子在船上
        Container container = new Container();
        container.setContainerId("CONT_DIRECT_OUT");
        container.setCurrentPos("VESSEL_01");
        container.setPosX(0.0);
        container.setPosY(170.0);
        ctx.getContainerMap().put("CONT_DIRECT_OUT", container);

        WorkInstruction wi = new WorkInstruction();
        wi.setWiRefNo("WI_DIRECT_OUT");
        wi.setContainerId("CONT_DIRECT_OUT");
        wi.setMoveKind(BizTypeEnum.DIRECT_OUT);
        wi.setFetchCheId("QC_01");
        wi.setCarryCheId("TRUCK_01");
        wi.setFromPos("VESSEL_01");
        wi.setToPos("TRUCK_01");
        ctx.getWorkInstructionMap().put("WI_DIRECT_OUT", wi);

        // 集卡初始在大门外
        ctx.getTruckMap().get("TRUCK_01").setPosX(300.0);

        TimelineBuilder timeline = new TimelineBuilder(ctx.getSimTime(), ctx);
        timeline.wait(1000);

        // 集卡开进到 QC 下方
        timeline.moveTruck("TRUCK_01", 80.0, truckRoadY, truckSpeed)
                // QC 抓箱
                .assign("QC_01", "WI_DIRECT_OUT")
                .moveCrane("QC_01", "MOVE_HORIZONTAL", -20.0, qcSpeed)
                .fetch("QC_01", fetchPutDuration)
                .moveCrane("QC_01", "MOVE_HORIZONTAL", 20.0, qcSpeed)
                .put("QC_01", fetchPutDuration)
                // 集卡拉着箱子开出大门
                .moveTruck("TRUCK_01", 400.0, truckRoadY, truckSpeed);

        return Result.success("已调度 DIRECT_OUT 直提测试");
    }

    // ==================== QC/ASC 双向移动测试 ====================

    /**
     * QC 水平+垂直双向移动测试
     * 演示 QC 沿轨道水平移动，然后垂直移动到集卡道路进行交接
     */
    @PostMapping("/qc-horizontal-vertical")
    public Result testQcHorizontalVertical() {
        GlobalContext ctx = GlobalContext.getInstance();

        ensureTestEnvironment();
        resetDevicesToInitialState();

        // 集卡在 QC 交接区等待 (X=300)
        ctx.getTruckMap().get("TRUCK_01").setPosX(300.0);
        ctx.getTruckMap().get("TRUCK_01").setPosY(truckRoadY);

        // QC 初始在 X=100
        ctx.getQcMap().get("QC_01").setPosX(100.0);
        ctx.getQcMap().get("QC_01").setPosY(qcRailY);

        TimelineBuilder timeline = new TimelineBuilder(ctx.getSimTime(), ctx);
        timeline.wait(1000);

        // === 阶段1: QC 水平移动到交接区上方 (X=300) ===
        timeline.moveCrane("QC_01", "MOVE_HORIZONTAL", 200.0, qcSpeed);

        // === 阶段2: QC 垂直移动下降到集卡道路 (Y: 140 -> 200) ===
        // QC 从轨道位置 Y=140 垂直向下移动 60 米到集卡道路 Y=200 进行交接
        timeline.moveCrane("QC_01", "MOVE_VERTICAL", 60.0, qcSpeed);

        // === 阶段3: QC 垂直上升回到轨道 (Y: 200 -> 140) ===
        timeline.moveCrane("QC_01", "MOVE_VERTICAL", -60.0, qcSpeed);

        // === 阶段4: QC 水平移动到新位置 (X=500) ===
        timeline.moveCrane("QC_01", "MOVE_HORIZONTAL", 200.0, qcSpeed);

        return Result.success("已调度 QC 水平+垂直双向移动测试");
    }

    /**
     * ASC 水平+垂直双向移动测试
     * 演示 ASC 沿轨道垂直移动，然后水平移动到集卡道路进行交接
     */
    @PostMapping("/asc-horizontal-vertical")
    public Result testAscHorizontalVertical() {
        GlobalContext ctx = GlobalContext.getInstance();

        ensureTestEnvironment();
        resetDevicesToInitialState();

        // 集卡在 ASC 交接区等待 (X=175 - 第一个ASC轨道)
        ctx.getTruckMap().get("TRUCK_01").setPosX(175.0);
        ctx.getTruckMap().get("TRUCK_01").setPosY(truckRoadY);

        // ASC 初始在 X=175, Y=300 (堆场内部)
        ctx.getAscMap().get("ASC_01").setPosX(ascRailX1);
        ctx.getAscMap().get("ASC_01").setPosY(300.0);

        TimelineBuilder timeline = new TimelineBuilder(ctx.getSimTime(), ctx);
        timeline.wait(1000);

        // === 阶段1: ASC 垂直移动到交接位 (Y: 300 -> 200) ===
        // ASC 从堆场内部垂直向上移动到集卡道路 Y=200 进行交接
        timeline.moveCrane("ASC_01", "MOVE_VERTICAL", -100.0, ascSpeed);

        // === 阶段2: ASC 水平移动到相邻轨道 (X: 175 -> 425) ===
        // ASC 水平跨越到第二个轨道
        timeline.moveCrane("ASC_01", "MOVE_HORIZONTAL", 250.0, ascSpeed);

        // === 阶段3: ASC 垂直移动到新轨道的交接位 (Y: 200 -> 250) ===
        timeline.moveCrane("ASC_01", "MOVE_VERTICAL", 50.0, ascSpeed);

        // === 阶段4: ASC 水平移动返回原轨道 (X: 425 -> 175) ===
        timeline.moveCrane("ASC_01", "MOVE_HORIZONTAL", -250.0, ascSpeed);

        return Result.success("已调度 ASC 水平+垂直双向移动测试");
    }
}
