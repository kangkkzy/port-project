package controller;

import common.Result;
import common.consts.BizTypeEnum;
import common.consts.DeviceStateEnum;
import common.consts.DeviceTypeEnum;
import common.consts.EventTypeEnum;
import engine.SimulationEngine;
import engine.context.GlobalContext;
import model.dto.request.CraneMoveReq;
import model.dto.request.CraneOperationReq;
import model.dto.request.MoveCommandReq;
import model.entity.*;
import service.algorithm.MapDataService;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 仿真测试场景接口 - 修复与高级规则验证版
 * 严格按照 map-config.json 的坐标、物理轨道方向、交接区与安全业务规则构建
 */
@RestController
@RequestMapping("/sim/test")
public class SimTestController {

    private final SimulationEngine engine;
    private final MapDataService mapDataService;

    // ----- 严格基于 map-config.json 定义的坐标常量 -----
    // 泊位1区域
    private final double BERTH_1_X = 150.0;
    private final double QC_RAIL_Y = 120.0;           // QC只能在此Y轴水平移动
    private final double TRUCK_ROAD_SEA_Y = 150.0;    // 泊位侧集卡交接道路Y

    // 堆场A区域
    private final double ASC_RAIL_A_X = 175.0;        // ASC只能在此X轴垂直移动
    private final double TRUCK_ROAD_YARD_Y = 230.0;   // 堆场侧集卡交接道路Y
    private final double YARD_A_SLOT_Y = 300.0;       // 堆场内部的一个落箱位Y

    // 速度与耗时
    private final double truckSpeed = 10.0;
    private final double qcSpeed = 1.5;
    private final double ascSpeed = 2.0;
    private final int fetchPutDuration = 3000;
    private final int assignDuration = 500;
    private final int safetyGap = 100;

    public SimTestController(SimulationEngine engine, MapDataService mapDataService) {
        this.engine = engine;
        this.mapDataService = mapDataService;
    }

    private class TimelineBuilder {
        private long currentTime;
        private final GlobalContext ctx;

        public TimelineBuilder(long startTime, GlobalContext ctx) {
            this.currentTime = startTime;
            this.ctx = ctx;
        }

        public TimelineBuilder wait(int ms) {
            this.currentTime += ms;
            return this;
        }

        public TimelineBuilder assign(String deviceId, String wiRefNo) {
            Map<String, Object> payload = new HashMap<>();
            payload.put("wiRefNo", wiRefNo);
            engine.scheduleEvent(null, currentTime, EventTypeEnum.CMD_ASSIGN_TASK, payload)
                    .addSubject("DEVICE", deviceId);
            this.currentTime += assignDuration + safetyGap;
            return this;
        }

        public TimelineBuilder moveTruck(String truckId, double startX, double startY, double targetX, double targetY) {
            double distance = Math.hypot(targetX - startX, targetY - startY);
            long moveTimeMs = (long) ((distance / truckSpeed) * 1000);

            MoveCommandReq payload = new MoveCommandReq();
            payload.setTruckId(truckId);
            payload.setTargetPoint(new Point(targetX, targetY));
            payload.setSpeed(truckSpeed);
            payload.setEnforcePathValidation(true);

            engine.scheduleEvent(null, currentTime, EventTypeEnum.CMD_MOVE, payload)
                    .addSubject("TRUCK", truckId);
            this.currentTime += moveTimeMs + safetyGap;
            return this;
        }

        public TimelineBuilder moveCrane(String craneId, String moveType, double distance, double speed) {
            long moveTimeMs = (long) ((Math.abs(distance) / speed) * 1000);
            CraneMoveReq payload = new CraneMoveReq();
            payload.setCraneId(craneId);
            payload.setMoveType(DeviceStateEnum.valueOf(moveType)); // MOVE_HORIZONTAL 或 MOVE_VERTICAL
            payload.setDistance(distance);
            payload.setSpeed(speed);
            engine.scheduleEvent(null, currentTime, EventTypeEnum.CMD_CRANE_MOVE, payload)
                    .addSubject("CRANE", craneId);
            this.currentTime += moveTimeMs + safetyGap;
            return this;
        }

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
    }

    /**
     * 重置并初始化合法的测试环境
     */
    private void ensureAndResetTestEnvironment() {
        GlobalContext ctx = GlobalContext.getInstance();
        ctx.getWorkInstructionMap().clear();
        ctx.getContainerMap().clear();

        // 集卡停在 泊位1海侧道路等待
        Truck truck = ctx.getTruckMap().computeIfAbsent("TRUCK_01", k -> new Truck());
        truck.setId("TRUCK_01");
        truck.setType(DeviceTypeEnum.ELECTRIC_TRUCK);
        truck.setPosX(BERTH_1_X);  // X=150
        truck.setPosY(TRUCK_ROAD_SEA_Y); // Y=150
        truck.setState(DeviceStateEnum.IDLE);
        truck.setCurrWiRefNo(null);
        truck.setRemainingMoveTargets(new ArrayList<>());

        // QC 停在泊位1上方
        QcDevice qc = ctx.getQcMap().computeIfAbsent("QC_01", k -> new QcDevice());
        qc.setId("QC_01");
        qc.setType(DeviceTypeEnum.QC);
        qc.setPosX(BERTH_1_X); // X=150
        qc.setPosY(QC_RAIL_Y); // Y=120
        qc.setState(DeviceStateEnum.IDLE);
        qc.setCurrWiRefNo(null);

        // ASC 停在堆场A前端交接位
        AscDevice asc = ctx.getAscMap().computeIfAbsent("ASC_01", k -> new AscDevice());
        asc.setId("ASC_01");
        asc.setType(DeviceTypeEnum.ASC);
        asc.setPosX(ASC_RAIL_A_X); // X=175
        asc.setPosY(TRUCK_ROAD_YARD_Y); // Y=230
        asc.setState(DeviceStateEnum.IDLE);
        asc.setCurrWiRefNo(null);
    }

    // ==================== 基础联通与轨迹测试 ====================

    /**
     * 联通测试 1: 完整的卸船业务流 (DSCH)
     */
    @PostMapping("/truck-delivery")
    public Result testTruckDelivery() {
        GlobalContext ctx = GlobalContext.getInstance();
        ensureAndResetTestEnvironment();

        WorkInstruction wiQc = new WorkInstruction();
        wiQc.setWiRefNo("WI_QC_DSCH");
        wiQc.setMoveKind(BizTypeEnum.DSCH);
        wiQc.setFetchCheId("QC_01");
        wiQc.setCarryCheId("TRUCK_01");
        ctx.getWorkInstructionMap().put("WI_QC_DSCH", wiQc);

        WorkInstruction wiAsc = new WorkInstruction();
        wiAsc.setWiRefNo("WI_ASC_DSCH");
        wiAsc.setMoveKind(BizTypeEnum.DSCH);
        wiAsc.setFetchCheId("ASC_01");
        wiAsc.setCarryCheId("TRUCK_01");
        ctx.getWorkInstructionMap().put("WI_ASC_DSCH", wiAsc);

        TimelineBuilder timeline = new TimelineBuilder(ctx.getSimTime(), ctx);
        timeline.wait(1000);

        timeline.assign("QC_01", "WI_QC_DSCH")
                .fetch("QC_01", fetchPutDuration)
                .put("QC_01", fetchPutDuration);

        timeline.moveTruck("TRUCK_01", BERTH_1_X, TRUCK_ROAD_SEA_Y, ASC_RAIL_A_X, TRUCK_ROAD_YARD_Y);

        timeline.assign("ASC_01", "WI_ASC_DSCH")
                .fetch("ASC_01", fetchPutDuration)
                .moveCrane("ASC_01", "MOVE_VERTICAL", (YARD_A_SLOT_Y - TRUCK_ROAD_YARD_Y), ascSpeed)
                .put("ASC_01", fetchPutDuration);

        return Result.success("已调度完整的合法卸船业务流测试 (DSCH)");
    }

    /**
     * 联通测试 2: 轨道合法滑行测试
     */
    @PostMapping("/crane-legal-move")
    public Result testCraneLegalMove() {
        GlobalContext ctx = GlobalContext.getInstance();
        ensureAndResetTestEnvironment();
        TimelineBuilder timeline = new TimelineBuilder(ctx.getSimTime(), ctx);
        timeline.wait(1000);

        timeline.moveCrane("QC_01", "MOVE_HORIZONTAL", 50.0, qcSpeed);
        timeline.moveCrane("QC_01", "MOVE_HORIZONTAL", -50.0, qcSpeed);
        timeline.moveCrane("ASC_01", "MOVE_VERTICAL", 70.0, ascSpeed);
        timeline.moveCrane("ASC_01", "MOVE_VERTICAL", -70.0, ascSpeed);

        return Result.success("已调度合法轨道滑行测试（不越轨）");
    }

    // ==================== 异常拦截与物理约束测试 ====================

    /**
     * 高级测试 1: 堆场提箱受阻测试 (StowageValidator)
     */
    @PostMapping("/stowage-blocked-fetch")
    public Result testStowageBlockedFetch() {
        GlobalContext ctx = GlobalContext.getInstance();
        ensureAndResetTestEnvironment();

        WorkInstruction wi = new WorkInstruction();
        wi.setWiRefNo("WI_DLVR_BLOCKED");
        wi.setMoveKind(BizTypeEnum.DLVR);
        wi.setFetchCheId("ASC_01");
        wi.setCarryCheId("TRUCK_01");
        wi.setFromPos("YARD_A_BLOCKED_SLOT");
        wi.setToPos("GATE_OUT");
        ctx.getWorkInstructionMap().put("WI_DLVR_BLOCKED", wi);

        TimelineBuilder timeline = new TimelineBuilder(ctx.getSimTime(), ctx);
        timeline.wait(1000);
        timeline.assign("ASC_01", "WI_DLVR_BLOCKED");

        return Result.success("已调度堆场提箱受阻测试，请观察前端是否收到 '堆场提箱受阻' 异常预警");
    }

    /**
     * 高级测试 2: 船舶装卸顺序错误测试 (StowageValidator)
     */
    @PostMapping("/stowage-vessel-sequence")
    public Result testStowageVesselSequence() {
        GlobalContext ctx = GlobalContext.getInstance();
        ensureAndResetTestEnvironment();

        WorkInstruction wi = new WorkInstruction();
        wi.setWiRefNo("WI_DSCH_WRONG_SEQ");
        wi.setMoveKind(BizTypeEnum.DSCH);
        wi.setFetchCheId("QC_01");
        wi.setCarryCheId("TRUCK_01");
        wi.setFromPos("BAY_01_WRONG_TIER");
        wi.setToPos("YARD_A");
        ctx.getWorkInstructionMap().put("WI_DSCH_WRONG_SEQ", wi);

        TimelineBuilder timeline = new TimelineBuilder(ctx.getSimTime(), ctx);
        timeline.wait(1000);
        timeline.assign("QC_01", "WI_DSCH_WRONG_SEQ");

        return Result.success("已调度卸船顺序错误测试，请观察前端是否收到 '卸船顺序错误' 异常预警");
    }

    /**
     * 高级测试 3: 岸桥防碰撞预警测试 (SafetyAndSyncValidator)
     */
    @PostMapping("/qc-collision-warning")
    public Result testQcCollisionWarning() {
        GlobalContext ctx = GlobalContext.getInstance();
        ensureAndResetTestEnvironment();

        QcDevice qc2 = ctx.getQcMap().computeIfAbsent("QC_02", k -> new QcDevice());
        qc2.setId("QC_02");
        qc2.setType(DeviceTypeEnum.QC);
        qc2.setPosX(160.0); // 距离 QC_01 只有 10 米，违背防碰撞规则
        qc2.setPosY(QC_RAIL_Y);
        qc2.setState(DeviceStateEnum.IDLE);

        WorkInstruction wi = new WorkInstruction();
        wi.setWiRefNo("WI_QC_COLLISION");
        wi.setMoveKind(BizTypeEnum.LOAD);
        wi.setFetchCheId("QC_02");
        wi.setCarryCheId("TRUCK_01");
        wi.setFromPos("TRUCK_01");
        wi.setToPos("BAY_01");
        ctx.getWorkInstructionMap().put("WI_QC_COLLISION", wi);

        TimelineBuilder timeline = new TimelineBuilder(ctx.getSimTime(), ctx);
        timeline.wait(1000);
        timeline.assign("QC_02", "WI_QC_COLLISION");

        return Result.success("已调度岸桥防碰撞测试，请观察前端是否收到 '岸桥防碰撞预警' 异常");
    }

    /**
     * 高级测试 4: 集卡与岸桥协同超时测试 (SafetyAndSyncValidator)
     */
    @PostMapping("/qc-truck-sync-timeout")
    public Result testQcTruckSyncTimeout() {
        GlobalContext ctx = GlobalContext.getInstance();
        ensureAndResetTestEnvironment();

        Truck truck = ctx.getTruckMap().get("TRUCK_01");
        truck.setPosX(9999.0); // 集卡极远，无法按时协同

        WorkInstruction wi = new WorkInstruction();
        wi.setWiRefNo("WI_SYNC_TIMEOUT");
        wi.setMoveKind(BizTypeEnum.DSCH);
        wi.setFetchCheId("QC_01");
        wi.setCarryCheId("TRUCK_01");
        wi.setFromPos("BAY_01");
        wi.setToPos("TRUCK_01");
        ctx.getWorkInstructionMap().put("WI_SYNC_TIMEOUT", wi);

        TimelineBuilder timeline = new TimelineBuilder(ctx.getSimTime(), ctx);
        timeline.wait(1000);
        timeline.assign("QC_01", "WI_SYNC_TIMEOUT");

        return Result.success("已调度协同超时测试，请观察前端是否收到 '集卡协同超时' 异常");
    }

    /**
     * 高级测试 5: 集卡连续多点轨迹测试 (ArrivalHandler 联动)
     */
    @PostMapping("/continuous-trajectory-move")
    public Result testContinuousTrajectoryMove() {
        GlobalContext ctx = GlobalContext.getInstance();
        ensureAndResetTestEnvironment();

        Truck truck = ctx.getTruckMap().get("TRUCK_01");
        List<Point> trajectory = new ArrayList<>();
        trajectory.add(new Point(200.0, 150.0));
        trajectory.add(new Point(200.0, 230.0));
        trajectory.add(new Point(425.0, 230.0)); // 连续拐弯
        truck.setRemainingMoveTargets(trajectory);

        TimelineBuilder timeline = new TimelineBuilder(ctx.getSimTime(), ctx);
        timeline.wait(1000);

        timeline.moveTruck("TRUCK_01", BERTH_1_X, TRUCK_ROAD_SEA_Y, 200.0, 150.0);

        return Result.success("已调度连续多点轨迹移动测试，请观察前端集卡是否连续转弯");
    }
}