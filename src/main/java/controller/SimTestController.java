package controller;

import common.Result;
import common.consts.BizTypeEnum;
import common.consts.DeviceStateEnum;
import common.consts.DeviceTypeEnum;
import common.consts.EventTypeEnum;
import common.consts.WiStatusEnum;
import engine.SimulationEngine;
import engine.context.GlobalContext;
import model.entity.*;
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
 * 仿真测试场景接口
 * 包含单场景测试与进出口生命周期任务链测试
 * 严格遵循 map-config.json 坐标，避免海域扎堆
 */
@RestController
@RequestMapping("/sim/test")
public class SimTestController {

    private final SimulationEngine engine;

    public SimTestController(SimulationEngine engine) {
        this.engine = engine;
    }

    private GlobalContext resetAndGetContext() {
        GlobalContext ctx = GlobalContext.getInstance();
        ctx.clearAll();
        engine.reset();
        ctx.setSimTime(0L);
        return ctx;
    }

    // ================== 🌟 核心升级：完整生命周期任务链 ==================
    @PostMapping("/task-chain")
    public Result testTaskChain() {
        resetAndGetContext();

        double qcX = 200.0, qcY = 140.0;             // 岸桥位置 (轨道y=140)
        double asc1X = 175.0, asc1Y = 300.0;         // 1号堆场龙门吊 (轨道x=175)
        double asc2X = 425.0, asc2Y = 300.0;         // 2号堆场龙门吊 (轨道x=425)
        double gateX = 500.0, gateY = 550.0;         // 闸口位置 (道路y=550)
        double speed = 10.0;                         // 集卡速度

        createQc("QC_01", qcX, qcY);
        createAsc("ASC_01", asc1X, asc1Y);
        createAsc("ASC_02", asc2X, asc2Y);

        createTruck("TRUCK_IN", qcX, qcY);
        createTruck("TRUCK_OUT", gateX, gateY);
        createContainer("CONT_CHAIN_01", "VESSEL_01");

        createWi("WI_01_DSCH", "CONT_CHAIN_01", BizTypeEnum.DSCH, "QC_01", "TRUCK_IN", "ASC_01", "VESSEL_01", "YARD_01");
        createWi("WI_02_SHIFT", "CONT_CHAIN_01", BizTypeEnum.YARD_SHIFT, "ASC_01", "TRUCK_IN", "ASC_02", "YARD_01", "YARD_02");
        createWi("WI_03_DLVR", "CONT_CHAIN_01", BizTypeEnum.DLVR, "ASC_02", "TRUCK_OUT", null, "YARD_02", "GATE_01");

        long t = 0;

        // 【环节一：DSCH 卸船 (Vessel -> Yard 1)】
        createAssignTaskEvent(t, "QC_01", "WI_01_DSCH");
        createCraneOperateEvent(t + 100, "QC_01", "FETCH_DONE", 1500);
        createCraneOperateEvent(t + 2000, "QC_01", "PUT_DONE", 1500);

        t += 4000;
        long arrTimeAsc1 = scheduleMove(t, "TRUCK_IN", qcX, qcY, asc1X, asc1Y, speed);

        t = arrTimeAsc1;
        createAssignTaskEvent(t, "ASC_01", "WI_01_DSCH");
        createCraneOperateEvent(t + 500, "ASC_01", "FETCH_DONE", 1500);
        createCraneOperateEvent(t + 2500, "ASC_01", "PUT_DONE", 1500);

        // 【环节二：YARD_SHIFT 场内移箱 (Yard 1 -> Yard 2)】
        t += 5000;
        createAssignTaskEvent(t, "ASC_01", "WI_02_SHIFT");
        createCraneOperateEvent(t + 500, "ASC_01", "FETCH_DONE", 1500);
        createCraneOperateEvent(t + 2500, "ASC_01", "PUT_DONE", 1500);

        t += 4500;
        long arrTimeAsc2 = scheduleMove(t, "TRUCK_IN", asc1X, asc1Y, asc2X, asc2Y, speed);

        t = arrTimeAsc2;
        createAssignTaskEvent(t, "ASC_02", "WI_02_SHIFT");
        createCraneOperateEvent(t + 500, "ASC_02", "FETCH_DONE", 1500);
        createCraneOperateEvent(t + 2500, "ASC_02", "PUT_DONE", 1500);

        // 【环节三：DLVR 外场提箱 (Yard 2 -> Gate)】
        t += 5000;
        long arrTimeOutTruck = scheduleMove(t, "TRUCK_OUT", gateX, gateY, asc2X, asc2Y, speed);

        t = arrTimeOutTruck;
        createAssignTaskEvent(t, "ASC_02", "WI_03_DLVR");
        createCraneOperateEvent(t + 500, "ASC_02", "FETCH_DONE", 1500);
        createCraneOperateEvent(t + 2500, "ASC_02", "PUT_DONE", 1500);

        t += 4500;
        scheduleMove(t, "TRUCK_OUT", asc2X, asc2Y, gateX, gateY, speed);

        return Result.success("✨ 进出口完整任务链已注入内核并排期完毕！");
    }

    // ================== 单一场景测试 ==================

    @PostMapping("/dsch")
    public Result testDsch() {
        resetAndGetContext();
        double qcX = 200.0, qcY = 140.0;
        double ascX = 175.0, ascY = 300.0;
        double speed = 10.0;

        createQc("QC_01", qcX, qcY);
        createAsc("ASC_01", ascX, ascY);
        createTruck("TRUCK_01", qcX, qcY);
        createContainer("CONT_01", "VESSEL_01");
        createWi("WI_DSCH", "CONT_01", BizTypeEnum.DSCH, "QC_01", "TRUCK_01", "ASC_01", "VESSEL_01", "YARD_01");

        createAssignTaskEvent(0, "QC_01", "WI_DSCH");
        createCraneOperateEvent(100, "QC_01", "FETCH_DONE", 1000);
        createCraneOperateEvent(2000, "QC_01", "PUT_DONE", 1000);

        long arrivalTime = scheduleMove(3500, "TRUCK_01", qcX, qcY, ascX, ascY, speed);

        createAssignTaskEvent(arrivalTime, "ASC_01", "WI_DSCH");
        createCraneOperateEvent(arrivalTime + 500, "ASC_01", "FETCH_DONE", 2000);
        createCraneOperateEvent(arrivalTime + 3000, "ASC_01", "PUT_DONE", 2000);

        return Result.success("已调度 DSCH(卸船) 测试");
    }

    @PostMapping("/load")
    public Result testLoad() {
        resetAndGetContext();
        double ascX = 425.0, ascY = 400.0;
        double qcX = 400.0, qcY = 140.0;
        double speed = 10.0;

        createAsc("ASC_01", ascX, ascY);
        createQc("QC_01", qcX, qcY);
        createTruck("TRUCK_01", ascX, ascY);
        createContainer("CONT_01", "YARD_01");
        createWi("WI_LOAD", "CONT_01", BizTypeEnum.LOAD, "ASC_01", "TRUCK_01", "QC_01", "YARD_01", "VESSEL_01");

        createAssignTaskEvent(0, "ASC_01", "WI_LOAD");
        createCraneOperateEvent(100, "ASC_01", "FETCH_DONE", 1000);
        createCraneOperateEvent(2000, "ASC_01", "PUT_DONE", 1000);

        long arrivalTime = scheduleMove(3500, "TRUCK_01", ascX, ascY, qcX, qcY, speed);

        createAssignTaskEvent(arrivalTime, "QC_01", "WI_LOAD");
        createCraneOperateEvent(arrivalTime + 500, "QC_01", "FETCH_DONE", 1000);
        createCraneOperateEvent(arrivalTime + 2000, "QC_01", "PUT_DONE", 1000);

        return Result.success("已调度 LOAD(装船) 测试");
    }

    @PostMapping("/yard-shift")
    public Result testYardShift() {
        resetAndGetContext();
        double asc1X = 175.0, ascY = 450.0;
        double asc2X = 675.0;
        double speed = 10.0;

        createAsc("ASC_01", asc1X, ascY);
        createAsc("ASC_02", asc2X, ascY);
        createTruck("TRUCK_01", asc1X, ascY);
        createContainer("CONT_01", "YARD_01");
        createWi("WI_SHIFT", "CONT_01", BizTypeEnum.YARD_SHIFT, "ASC_01", "TRUCK_01", "ASC_02", "YARD_01", "YARD_02");

        createAssignTaskEvent(0, "ASC_01", "WI_SHIFT");
        createCraneOperateEvent(100, "ASC_01", "FETCH_DONE", 1000);
        createCraneOperateEvent(2000, "ASC_01", "PUT_DONE", 1000);

        long arrivalTime = scheduleMove(3500, "TRUCK_01", asc1X, ascY, asc2X, ascY, speed);

        createAssignTaskEvent(arrivalTime, "ASC_02", "WI_SHIFT");
        createCraneOperateEvent(arrivalTime + 500, "ASC_02", "FETCH_DONE", 1000);
        createCraneOperateEvent(arrivalTime + 2000, "ASC_02", "PUT_DONE", 1000);

        return Result.success("已调度 YARD_SHIFT(场内移箱) 测试");
    }

    @PostMapping("/dlvr")
    public Result testDlvr() {
        resetAndGetContext();
        double ascX = 675.0, ascY = 350.0;
        createAsc("ASC_01", ascX, ascY);
        createTruck("TRUCK_01", ascX, ascY);
        createContainer("CONT_01", "YARD_01");
        createWi("WI_DLVR", "CONT_01", BizTypeEnum.DLVR, "ASC_01", "TRUCK_01", null, "YARD_01", "GATE_01");

        createAssignTaskEvent(0, "ASC_01", "WI_DLVR");
        createCraneOperateEvent(100, "ASC_01", "FETCH_DONE", 1000);
        createCraneOperateEvent(2000, "ASC_01", "PUT_DONE", 1000);
        return Result.success("已调度 DLVR(外场提箱) 测试");
    }

    @PostMapping("/recv")
    public Result testRecv() {
        resetAndGetContext();
        double ascX = 175.0, ascY = 250.0;
        createAsc("ASC_01", ascX, ascY);
        createTruck("TRUCK_01", ascX, ascY);
        createContainer("CONT_01", "TRUCK_01");
        createWi("WI_RECV", "CONT_01", BizTypeEnum.RECV, null, "TRUCK_01", "ASC_01", "GATE_01", "YARD_01");

        createAssignTaskEvent(0, "ASC_01", "WI_RECV");
        createCraneOperateEvent(100, "ASC_01", "FETCH_DONE", 1000);
        createCraneOperateEvent(2000, "ASC_01", "PUT_DONE", 1000);
        return Result.success("已调度 RECV(外场收箱) 测试");
    }

    @PostMapping("/direct-in")
    public Result testDirectIn() {
        resetAndGetContext();
        double qcX = 600.0, qcY = 140.0;
        createQc("QC_01", qcX, qcY);
        createTruck("TRUCK_01", qcX, qcY);
        createContainer("CONT_01", "TRUCK_01");
        createWi("WI_DIN", "CONT_01", BizTypeEnum.DIRECT_IN, null, "TRUCK_01", "QC_01", "GATE_01", "VESSEL_01");

        createAssignTaskEvent(0, "QC_01", "WI_DIN");
        createCraneOperateEvent(100, "QC_01", "FETCH_DONE", 1000);
        createCraneOperateEvent(2000, "QC_01", "PUT_DONE", 1000);
        return Result.success("已调度 DIRECT_IN(直进装船) 测试");
    }

    @PostMapping("/direct-out")
    public Result testDirectOut() {
        resetAndGetContext();
        double qcX = 200.0, qcY = 140.0;
        createQc("QC_01", qcX, qcY);
        createTruck("TRUCK_01", qcX, qcY);
        createContainer("CONT_01", "VESSEL_01");
        createWi("WI_DOUT", "CONT_01", BizTypeEnum.DIRECT_OUT, "QC_01", "TRUCK_01", null, "VESSEL_01", "GATE_01");

        createAssignTaskEvent(0, "QC_01", "WI_DOUT");
        createCraneOperateEvent(100, "QC_01", "FETCH_DONE", 1000);
        createCraneOperateEvent(2000, "QC_01", "PUT_DONE", 1000);
        return Result.success("已调度 DIRECT_OUT(直提卸船) 测试");
    }

    // ==================== 实体与事件辅助构建方法 ====================

    private long scheduleMove(long startTime, String truckId, double startX, double startY, double targetX, double targetY, double speed) {
        double dist = Math.sqrt(Math.pow(targetX - startX, 2) + Math.pow(targetY - startY, 2));
        long moveTime = (long) ((dist / speed) * 1000);

        MoveCommandReq payload = new MoveCommandReq();
        payload.setTruckId(truckId);
        payload.setTargetPoint(new Point(targetX, targetY));
        payload.setSpeed(speed);
        engine.scheduleEvent(null, startTime, EventTypeEnum.CMD_MOVE, payload).addSubject("TRUCK", truckId);

        return startTime + moveTime + 200;
    }

    private void createQc(String id, double x, double y) {
        QcDevice qc = new QcDevice(); qc.setId(id); qc.setType(DeviceTypeEnum.QC); qc.setState(DeviceStateEnum.IDLE); qc.setPosX(x); qc.setPosY(y);
        GlobalContext.getInstance().getQcMap().put(id, qc);
    }

    private void createAsc(String id, double x, double y) {
        AscDevice asc = new AscDevice(); asc.setId(id); asc.setType(DeviceTypeEnum.ASC); asc.setState(DeviceStateEnum.IDLE); asc.setPosX(x); asc.setPosY(y);
        GlobalContext.getInstance().getAscMap().put(id, asc);
    }

    private void createTruck(String id, double x, double y) {
        Truck truck = new Truck(); truck.setId(id); truck.setType(DeviceTypeEnum.ELECTRIC_TRUCK); truck.setState(DeviceStateEnum.IDLE); truck.setPosX(x); truck.setPosY(y); truck.setPowerLevel(100.0); truck.setRemainingMoveTargets(new ArrayList<>());
        GlobalContext.getInstance().getTruckMap().put(id, truck);
    }

    private void createContainer(String id, String pos) {
        Container container = new Container(); container.setContainerId(id); container.setCurrentPos(pos);
        GlobalContext.getInstance().getContainerMap().put(id, container);
    }

    private void createWi(String ref, String cid, BizTypeEnum biz, String fetch, String carry, String put, String from, String to) {
        WorkInstruction wi = new WorkInstruction(); wi.setWiRefNo(ref); wi.setContainerId(cid); wi.setMoveKind(biz); wi.setFetchCheId(fetch); wi.setCarryCheId(carry); wi.setPutCheId(put); wi.setFromPos(from); wi.setToPos(to); wi.setWiStatus(WiStatusEnum.EXECUTING.getCode());
        GlobalContext.getInstance().getWorkInstructionMap().put(ref, wi);
    }

    private void createCraneOperateEvent(long time, String id, String action, int duration) {
        CraneOperationReq payload = new CraneOperationReq(); payload.setCraneId(id); payload.setAction(EventTypeEnum.valueOf(action)); payload.setDurationMS(duration);
        engine.scheduleEvent(null, time, EventTypeEnum.CMD_CRANE_OP, payload).addSubject("CRANE", id);
    }

    private void createAssignTaskEvent(long time, String id, String wi) {
        Map<String, Object> payload = new HashMap<>(); payload.put("wiRefNo", wi);
        engine.scheduleEvent(null, time, EventTypeEnum.CMD_ASSIGN_TASK, payload).addSubject("DEVICE", id);
    }
}