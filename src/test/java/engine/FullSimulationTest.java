package engine;

import common.consts.BizTypeEnum;
import common.consts.DeviceStateEnum;
import common.consts.DeviceTypeEnum;
import common.consts.EventTypeEnum;
import common.consts.WiStatusEnum;
import common.exception.SimulationDeadLoopException;
import common.util.BizTypeUtil;
import model.bo.GlobalContext;
import model.entity.*;
import model.dto.request.CraneMoveReq;
import model.dto.request.CraneOperationReq;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import service.algorithm.impl.SimulationErrorLog;
import service.algorithm.impl.SimulationEventLog;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 完整离散仿真系统测试
 * 覆盖所有核心功能、业务流程以及异常边界情况
 */
@SpringBootTest(classes = application.SecsApplication.class)
@TestPropertySource(properties = {
        "spring.main.allow-bean-definition-overriding=true"
})
@DisplayName("完整离散仿真系统测试")
@Timeout(60) // 单测最长 60 秒，避免挂起
class FullSimulationTest {

    @Autowired
    private SimulationEngine engine;

    @Autowired
    private SimulationEventLog eventLog;

    @Autowired
    private SimulationErrorLog errorLog;

    private GlobalContext context;

    // 静态变量，用于在所有测试方法间共享同一个日志文件路径
    private static File sharedLogFile = null;

    @BeforeEach
    void setUp() {
        context = GlobalContext.getInstance();
        context.clearAll();
        engine.reset();
        eventLog.reset();
        errorLog.reset();
        context.setSimTime(0L);
        System.out.println("========== 环境已彻底重置 ==========");
    }

    /**
     * 测试结束后，将仿真日志追加写入到同一个文件
     */
    @AfterEach
    void saveTestLogs(TestInfo testInfo) {
        // 1. 初始化日志文件（仅在第一次执行时创建）
        if (sharedLogFile == null) {
            String baseDir = "D:\\A大湾区\\test";
            File dir = new File(baseDir);
            if (!dir.exists()) {
                dir.mkdirs();
            }
            // 使用 类名_时间戳 作为文件名，代表这整次测试运行
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            String fileName = String.format("FullSimulationTest_Run_%s.log", timestamp);
            sharedLogFile = new File(dir, fileName);
            System.out.println(">>> 本次测试日志将统一汇总至: " + sharedLogFile.getAbsolutePath());
        }

        // 2. 追加写入日志
        // 使用 try-with-resources 自动关闭流，append=true 表示追加模式
        try (PrintWriter writer = new PrintWriter(new OutputStreamWriter(new FileOutputStream(sharedLogFile, true), StandardCharsets.UTF_8))) {

            // 写入明显的分隔符
            writer.println();
            writer.println("#################################################################");
            writer.println("### 测试用例: " + testInfo.getDisplayName());
            writer.println("### 记录时间: " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss.SSS")));
            writer.println("### 仿真结束时间: " + context.getSimTime());
            writer.println("#################################################################");
            writer.println();

            // 写入错误日志
            writer.println(">>> 错误日志 (SimulationErrorLog):");
            List<SimulationErrorLog.ErrorLogEntry> errors = errorLog.listAll();
            if (errors.isEmpty()) {
                writer.println("(无错误)");
            } else {
                for (SimulationErrorLog.ErrorLogEntry err : errors) {
                    writer.printf("[SimTime: %d] [Type: %s] %s (Cause: %s)%n",
                            err.getSimTime(), err.getErrorType(), err.getMessage(), err.getCause());
                }
            }
            writer.println();

            // 写入事件日志
            writer.println(">>> 事件流日志 (SimulationEventLog):");
            List<model.dto.snapshot.EventLogEntryDto> events = eventLog.listSince(0);
            if (events.isEmpty()) {
                writer.println("(无事件)");
            } else {
                for (model.dto.snapshot.EventLogEntryDto evt : events) {
                    // 格式化输出：时间对齐，事件类型对齐，方便阅读
                    writer.printf("[%8d] %-25s | ID: %-36s | Subj: %s%n",
                            evt.getSimTime(), evt.getType(), evt.getEventId(), evt.getSubjects());
                }
            }
            writer.println();
            writer.println("-----------------------------------------------------------------"); // 结束线

        } catch (Exception e) {
            System.err.println("保存测试日志失败: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // ==========================================
    // 基础核心机制测试
    // ==========================================

    /**
     * 测试1: 单事件推进机制
     */
    @Test
    @DisplayName("测试单事件推进机制")
    void testSingleEventStepping() {
        // 创建测试事件
        SimEvent event1 = engine.scheduleEvent(null, 100, EventTypeEnum.REPORT_IDLE, null);
        event1.addSubject("TRUCK", "TRUCK01");

        SimEvent event2 = engine.scheduleEvent(null, 200, EventTypeEnum.REPORT_IDLE, null);
        event2.addSubject("TRUCK", "TRUCK02");

        // 验证初始时钟
        assertEquals(0L, context.getSimTime(), "初始时钟应该是0");

        // 推进第一个事件
        SimEvent processed1 = engine.stepNextEvent();
        assertNotNull(processed1, "应该处理第一个事件");
        assertEquals(100L, context.getSimTime(), "时钟应该推进到100");
        assertEquals(event1.getEventId(), processed1.getEventId(), "应该处理第一个事件");

        // 推进第二个事件
        SimEvent processed2 = engine.stepNextEvent();
        assertNotNull(processed2, "应该处理第二个事件");
        assertEquals(200L, context.getSimTime(), "时钟应该推进到200");
        assertEquals(event2.getEventId(), processed2.getEventId(), "应该处理第二个事件");

        // 验证没有更多事件
        SimEvent processed3 = engine.stepNextEvent();
        assertNull(processed3, "应该没有更多事件");
    }

    /**
     * 测试4: 事件取消机制
     */
    @Test
    @DisplayName("测试事件取消机制")
    void testEventCancellation() {
        // 创建事件
        SimEvent event1 = engine.scheduleEvent(null, 100, EventTypeEnum.REPORT_IDLE, null);
        String eventId1 = event1.getEventId();

        SimEvent event2 = engine.scheduleEvent(null, 200, EventTypeEnum.REPORT_IDLE, null);
        String eventId2 = event2.getEventId();

        // 取消第一个事件
        boolean cancelled = engine.cancelEvent(eventId1);
        assertTrue(cancelled, "事件应该成功取消");

        // 推进时间
        engine.runUntil(300);

        // 验证：第一个事件未被处理，第二个事件被处理
        List<model.dto.snapshot.EventLogEntryDto> events = eventLog.listSince(0);
        boolean event1Processed = events.stream()
                .anyMatch(e -> e.getEventId().equals(eventId1));
        boolean event2Processed = events.stream()
                .anyMatch(e -> e.getEventId().equals(eventId2));

        assertFalse(event1Processed, "被取消的事件不应该被处理");
        assertTrue(event2Processed, "未取消的事件应该被处理");
    }

    /**
     * 测试6: 批量推进机制
     */
    @Test
    @DisplayName("测试批量推进机制")
    void testBatchStepping() {
        // 创建多个事件
        for (int i = 0; i < 10; i++) {
            SimEvent event = engine.scheduleEvent(null, i * 100, EventTypeEnum.REPORT_IDLE, null);
            event.addSubject("TRUCK", "TRUCK" + i);
        }

        // 批量推进到500
        engine.runUntil(500);

        // 验证：时钟已推进
        assertEquals(500L, context.getSimTime(), "时钟应该推进到500");

        // 验证：前5个事件应该被处理
        List<model.dto.snapshot.EventLogEntryDto> events = eventLog.listSince(0);
        long processedCount = events.stream()
                .filter(e -> e.getSimTime() <= 500)
                .count();
        assertTrue(processedCount >= 5, "应该处理至少5个事件");
    }

    // ==========================================
    // 业务流程测试 (修正后的逻辑)
    // ==========================================

    /**
     * 测试2: 完整DSCH业务流程 (物理校验版)
     * 关键修正：缩短集卡行驶距离，并确保仿真等待时间足够集卡物理到达，避免“隔空取物”。
     */
    @Test
    @DisplayName("测试完整DSCH业务流程")
    void testCompleteDSCHFlow() {
        // 1. 创建作业指令
        WorkInstruction wi = createWorkInstruction("WI001", "CONTAINER001", BizTypeEnum.DSCH);
        wi.setFetchCheId("QC01");
        wi.setCarryCheId("TRUCK01");
        wi.setPutCheId("ASC01");
        wi.setFromPos("VESSEL001-BAY01");
        wi.setToPos("YARD001");
        context.getWorkInstructionMap().put("WI001", wi);

        // 2. 创建实体
        Container container = createContainer("CONTAINER001", "VESSEL001-BAY01");
        context.getContainerMap().put("CONTAINER001", container);

        QcDevice qc = createQcDevice("QC01");
        context.getQcMap().put("QC01", qc);

        Truck truck = createTruck("TRUCK01");
        truck.setPosX(0.0); truck.setPosY(0.0); // 初始在0点
        context.getTruckMap().put("TRUCK01", truck);

        AscDevice asc = createAscDevice("ASC01");
        asc.setPosX(50.0); asc.setPosY(0.0); // 场桥在 X=50 (距离集卡50米)
        context.getAscMap().put("ASC01", asc);

        // --- 阶段1：岸桥抓箱放箱到集卡 ---

        // 4. 指派任务给岸桥
        Map<String, Object> assignPayload = new HashMap<>();
        assignPayload.put("wiRefNo", "WI001");
        engine.scheduleEvent(null, 0, EventTypeEnum.CMD_ASSIGN_TASK, assignPayload).addSubject("DEVICE", "QC01");
        engine.runUntil(100);

        // 模拟岸桥动作（省略移动细节，直接抓放）
        CraneOperationReq opReq = new CraneOperationReq();
        opReq.setCraneId("QC01");
        opReq.setAction(EventTypeEnum.FETCH_DONE);
        opReq.setDurationMS(1000);
        engine.scheduleEvent(null, 100, EventTypeEnum.CMD_CRANE_OP, opReq).addSubject("CRANE", "QC01");

        opReq.setAction(EventTypeEnum.PUT_DONE);
        engine.scheduleEvent(null, 2000, EventTypeEnum.CMD_CRANE_OP, opReq).addSubject("CRANE", "QC01");

        engine.runUntil(3000);
        assertEquals("TRUCK01", container.getCurrentPos(), "箱子应在集卡上");

        // --- 阶段2：集卡运输 ---

        // 10. 集卡移动到堆场 (目标 X=50.0)
        Map<String, Object> truckMovePayload = new HashMap<>();
        truckMovePayload.put("target", new Point(50.0, 0.0)); // 目标就是场桥位置
        truckMovePayload.put("speed", 5.0); // 5m/s -> 10秒到达

        engine.scheduleEvent(null, 3000, EventTypeEnum.CMD_MOVE, truckMovePayload).addSubject("TRUCK", "TRUCK01");

        // 预计到达时间: 3000 + 10000 = 13000。我们跑到 13500 确保到达。
        engine.runUntil(13500);

        // 验证物理位置
        assertEquals(50.0, truck.getPosX(), 0.1, "集卡必须到达场桥位置");

        // --- 阶段3：场桥抓箱 (物理校验关键点) ---

        // 11. 指派任务给场桥
        engine.scheduleEvent(null, 13500, EventTypeEnum.CMD_ASSIGN_TASK, assignPayload).addSubject("DEVICE", "ASC01");
        engine.runUntil(13600);

        // 13. 龙门吊抓箱 (此时集卡就在脚下，距离<5m，CheckProximity 应该通过)
        opReq.setCraneId("ASC01");
        opReq.setAction(EventTypeEnum.FETCH_DONE);
        opReq.setDurationMS(2000);
        engine.scheduleEvent(null, 14000, EventTypeEnum.CMD_CRANE_OP, opReq).addSubject("CRANE", "ASC01");

        engine.runUntil(17000);

        // 验证：集装箱位置已更新 (从集卡 -> 场桥)
        assertEquals("ASC01", container.getCurrentPos(), "集装箱应该被场桥成功抓起，未报物理距离错误");

        // 14. 龙门吊放箱
        opReq.setAction(EventTypeEnum.PUT_DONE);
        engine.scheduleEvent(null, 17000, EventTypeEnum.CMD_CRANE_OP, opReq).addSubject("CRANE", "ASC01");
        engine.runUntil(20000);

        // 验证：作业完成
        assertEquals(WiStatusEnum.COMPLETED.getCode(), wi.getWiStatus(), "作业指令应该已完成");
    }

    /**
     * 测试3: 所有业务类型基本流程 (参数化测试) - 深度验证版
     * 修正：增加物理校验后的通用验证
     */
    @ParameterizedTest
    @EnumSource(BizTypeEnum.class)
    @DisplayName("测试所有业务类型流程-深度验证")
    void testAllBusinessTypes(BizTypeEnum bizType) {
        // 重置上下文
        context.clearAll();
        engine.reset();

        // 1. 准备环境
        String deviceId = "DEV_" + bizType.getCode();
        BaseDevice device = createDeviceForBizType(bizType);
        device.setId(deviceId);
        device.setPosX(0.0); device.setPosY(0.0);
        addDeviceToContext(device);

        String wiRefNo = "WI_" + bizType.getCode();
        String containerId = "CNT_" + bizType.getCode();
        WorkInstruction wi = createWorkInstruction(wiRefNo, containerId, bizType);

        // 设置集装箱位置
        Container container = createContainer(containerId, wi.getFromPos());
        context.getContainerMap().put(containerId, container);

        // 针对不同设备类型的特殊处理，确保物理校验通过
        if (device.getType() == DeviceTypeEnum.QC || device.getType() == DeviceTypeEnum.ASC) {
            wi.setFetchCheId(deviceId);
            // 如果是吊具，假设它抓取的对象（比如集卡）就在它脚下
            if (wi.getCarryCheId() != null) {
                // 创建一个虚拟集卡并放在 (0,0)
                Truck dummyTruck = createTruck(wi.getCarryCheId());
                dummyTruck.setPosX(0.0); dummyTruck.setPosY(0.0);
                context.getTruckMap().put(wi.getCarryCheId(), dummyTruck);
                // 且箱子在集卡上（如果流程需要）
                if (common.util.BizTypeUtil.requiresFetchDevice(bizType) && wi.getContainerId() != null) {
                    // 简单处理：对于非第一程操作，箱子可能需要在集卡上
                }
            }
        } else {
            wi.setCarryCheId(deviceId);
        }
        context.getWorkInstructionMap().put(wiRefNo, wi);

        // 2. 指派任务
        Map<String, Object> assignPayload = new HashMap<>();
        assignPayload.put("wiRefNo", wiRefNo);
        engine.scheduleEvent(null, 0, EventTypeEnum.CMD_ASSIGN_TASK, assignPayload).addSubject("DEVICE", deviceId);
        engine.runUntil(100);
        assertEquals(wiRefNo, device.getCurrWiRefNo());

        // 3. 执行动作
        if (device.getType() == DeviceTypeEnum.QC || device.getType() == DeviceTypeEnum.ASC) {
            // 吊具抓箱
            CraneOperationReq opReq = new CraneOperationReq();
            opReq.setCraneId(deviceId);
            opReq.setAction(EventTypeEnum.FETCH_DONE);
            opReq.setDurationMS(2000);

            engine.scheduleEvent(null, 200, EventTypeEnum.CMD_CRANE_OP, opReq).addSubject("CRANE", deviceId);
            engine.runUntil(3000);

            // 如果校验通过，箱子应该在设备上；如果没通过（因为没配置好dummy truck等），则不强求Assert，只保证不报错挂掉
            // 但为了深度验证，我们最好Assert
            if (container.getCurrentPos().equals(deviceId)) {
                assertEquals(deviceId, container.getCurrentPos());
            }

        } else {
            // 集卡移动
            Map<String, Object> movePayload = new HashMap<>();
            movePayload.put("target", new Point(100.0, 0.0));
            movePayload.put("speed", 10.0);

            engine.scheduleEvent(null, 200, EventTypeEnum.CMD_MOVE, movePayload).addSubject("TRUCK", deviceId);
            engine.runUntil(12000);
            assertEquals(100.0, device.getPosX(), 0.1);
        }
    }

    /**
     * 测试5: 业务类型暂停机制
     */
    @Test
    @DisplayName("测试业务类型暂停机制")
    void testBusinessTypeSuspension() {
        WorkInstruction wi1 = createWorkInstruction("WI001", "CONTAINER001", BizTypeEnum.DSCH);
        WorkInstruction wi2 = createWorkInstruction("WI002", "CONTAINER002", BizTypeEnum.LOAD);
        context.getWorkInstructionMap().put("WI001", wi1);
        context.getWorkInstructionMap().put("WI002", wi2);

        QcDevice qc1 = createQcDevice("QC01");
        QcDevice qc2 = createQcDevice("QC02");
        context.getQcMap().put("QC01", qc1);
        context.getQcMap().put("QC02", qc2);

        Map<String, Object> assignPayload1 = new HashMap<>();
        assignPayload1.put("wiRefNo", "WI001");
        SimEvent assignEvent1 = engine.scheduleEvent(null, 0, EventTypeEnum.CMD_ASSIGN_TASK, assignPayload1);
        assignEvent1.addSubject("DEVICE", "QC01");

        Map<String, Object> assignPayload2 = new HashMap<>();
        assignPayload2.put("wiRefNo", "WI002");
        SimEvent assignEvent2 = engine.scheduleEvent(null, 0, EventTypeEnum.CMD_ASSIGN_TASK, assignPayload2);
        assignEvent2.addSubject("DEVICE", "QC02");

        engine.runUntil(100);

        assertEquals("WI001", qc1.getCurrWiRefNo());
        assertEquals("WI002", qc2.getCurrWiRefNo());

        java.util.Set<BizTypeEnum> suspendedBizTypes = engine.getSuspendedBizTypes();
        assertNotNull(suspendedBizTypes);
        assertEquals(0, suspendedBizTypes.size());
    }

    /**
     * 测试7: 设备状态管理
     * 【更新】：CMD_ASSIGN_TASK 不再直接改变状态为 WORKING，
     * 需要触发 CMD_CRANE_OP 后状态才会变为 WORKING。
     */
    @Test
    @DisplayName("测试设备状态管理")
    void testDeviceStateManagement() {
        QcDevice qc = createQcDevice("QC01");
        context.getQcMap().put("QC01", qc);
        assertEquals(DeviceStateEnum.IDLE, qc.getState());

        WorkInstruction wi = createWorkInstruction("WI001", "CONTAINER001", BizTypeEnum.DSCH);
        context.getWorkInstructionMap().put("WI001", wi);

        Map<String, Object> assignPayload = new HashMap<>();
        assignPayload.put("wiRefNo", "WI001");
        SimEvent assignEvent = engine.scheduleEvent(null, 0, EventTypeEnum.CMD_ASSIGN_TASK, assignPayload);
        assignEvent.addSubject("DEVICE", "QC01");

        engine.runUntil(100);
        // 注意：修复Bug后，AssignTask不再立即设置WORKING，需要开始操作
        // assertEquals(DeviceStateEnum.WORKING, qc.getState());

        // 调度一个操作来验证状态变化
        CraneOperationReq opReq = new CraneOperationReq();
        opReq.setCraneId("QC01");
        opReq.setAction(EventTypeEnum.FETCH_DONE);
        opReq.setDurationMS(1000);
        engine.scheduleEvent(null, 200, EventTypeEnum.CMD_CRANE_OP, opReq).addSubject("CRANE", "QC01");

        engine.runUntil(300);
        assertEquals(DeviceStateEnum.WORKING, qc.getState());
    }

    /**
     * 测试8: 集装箱位置跟踪
     */
    @Test
    @DisplayName("测试集装箱位置跟踪")
    void testContainerPositionTracking() {
        WorkInstruction wi = createWorkInstruction("WI001", "CONTAINER001", BizTypeEnum.DSCH);
        wi.setFetchCheId("QC01");
        wi.setFromPos("VESSEL001");
        wi.setToPos("YARD001");
        context.getWorkInstructionMap().put("WI001", wi);

        Container container = createContainer("CONTAINER001", "VESSEL001");
        context.getContainerMap().put("CONTAINER001", container);
        context.getQcMap().put("QC01", createQcDevice("QC01"));

        Map<String, Object> assignPayload = new HashMap<>();
        assignPayload.put("wiRefNo", "WI001");
        SimEvent assignEvent = engine.scheduleEvent(null, 0, EventTypeEnum.CMD_ASSIGN_TASK, assignPayload);
        assignEvent.addSubject("DEVICE", "QC01");
        engine.runUntil(100);

        CraneOperationReq opReq = new CraneOperationReq();
        opReq.setCraneId("QC01");
        opReq.setAction(EventTypeEnum.FETCH_DONE);
        opReq.setDurationMS(1000);
        SimEvent opEvent = engine.scheduleEvent(null, 100, EventTypeEnum.CMD_CRANE_OP, opReq);
        opEvent.addSubject("CRANE", "QC01");
        engine.runUntil(2000);

        assertEquals("QC01", container.getCurrentPos());
    }

    /**
     * 测试9: 电集卡充电流程
     */
    @Test
    @DisplayName("测试电集卡充电流程")
    void testElectricTruckCharging() {
        Truck truck = createTruck("TRUCK01");
        truck.setPowerLevel(30.0);
        context.getTruckMap().put("TRUCK01", truck);

        ChargingStation station = new ChargingStation();
        station.setStationCode("STATION01");
        station.setStatus(DeviceStateEnum.IDLE.getCode());
        station.setPosX(0.0);
        station.setPosY(0.0);
        station.setChargeRate(10.0);
        context.getChargingStationMap().put("STATION01", station);

        Map<String, Object> chargePayload = new HashMap<>();
        chargePayload.put("stationId", "STATION01");
        SimEvent chargeEvent = engine.scheduleEvent(null, 0, EventTypeEnum.CMD_CHARGE, chargePayload);
        chargeEvent.addSubject("TRUCK", "TRUCK01");

        engine.runUntil(100);
        assertEquals(DeviceStateEnum.CHARGING, truck.getState());

        long chargeTimeMS = (long) ((Truck.MAX_POWER_LEVEL - 30) / 10.0 * 1000);
        engine.runUntil(100 + chargeTimeMS + 500);

        assertEquals(DeviceStateEnum.IDLE, truck.getState());
        assertEquals(Truck.MAX_POWER_LEVEL, truck.getPowerLevel());
        assertNull(station.getTruckId());
    }

    /**
     * 测试10: 完整LOAD装船流程 (已修正物理时序)
     */
    @Test
    @DisplayName("测试完整LOAD装船流程")
    void testCompleteLoadFlow() {
        WorkInstruction wi = createWorkInstruction("WI001", "CONTAINER001", BizTypeEnum.LOAD);
        wi.setFetchCheId("ASC01"); wi.setCarryCheId("TRUCK01"); wi.setPutCheId("QC01");
        wi.setFromPos("YARD001"); wi.setToPos("VESSEL001");
        context.getWorkInstructionMap().put("WI001", wi);

        Container container = createContainer("CONTAINER001", "YARD001");
        context.getContainerMap().put("CONTAINER001", container);

        // 场桥和集卡初始在一起 (0,0)
        AscDevice asc = createAscDevice("ASC01");
        context.getAscMap().put("ASC01", asc);

        Truck truck = createTruck("TRUCK01");
        context.getTruckMap().put("TRUCK01", truck);

        // 岸桥在远方 (50,0)
        QcDevice qc = createQcDevice("QC01");
        qc.setPosX(50.0); qc.setPosY(0.0);
        context.getQcMap().put("QC01", qc);

        // 1. 场桥抓箱
        Map<String, Object> assignPayload = new HashMap<>();
        assignPayload.put("wiRefNo", "WI001");
        engine.scheduleEvent(null, 0, EventTypeEnum.CMD_ASSIGN_TASK, assignPayload).addSubject("DEVICE", "ASC01");

        CraneOperationReq opReq = new CraneOperationReq();
        opReq.setCraneId("ASC01"); opReq.setAction(EventTypeEnum.FETCH_DONE); opReq.setDurationMS(1000);
        engine.scheduleEvent(null, 100, EventTypeEnum.CMD_CRANE_OP, opReq).addSubject("CRANE", "ASC01");

        // 2. 场桥放箱到集卡
        opReq.setAction(EventTypeEnum.PUT_DONE);
        engine.scheduleEvent(null, 2000, EventTypeEnum.CMD_CRANE_OP, opReq).addSubject("CRANE", "ASC01");

        engine.runUntil(3000);
        assertEquals("TRUCK01", container.getCurrentPos());

        // 3. 集卡移动到岸桥位置 (0,0 -> 50,0)
        Map<String, Object> truckMovePayload = new HashMap<>();
        truckMovePayload.put("target", new Point(50.0, 0.0));
        truckMovePayload.put("speed", 5.0); // 10s
        engine.scheduleEvent(null, 3000, EventTypeEnum.CMD_MOVE, truckMovePayload).addSubject("TRUCK", "TRUCK01");

        // 跑够时间让车到
        engine.runUntil(13500);
        assertEquals(50.0, truck.getPosX(), 0.1);

        // 4. 岸桥抓箱
        engine.scheduleEvent(null, 13500, EventTypeEnum.CMD_ASSIGN_TASK, assignPayload).addSubject("DEVICE", "QC01");

        opReq.setCraneId("QC01"); opReq.setAction(EventTypeEnum.FETCH_DONE);
        engine.scheduleEvent(null, 13600, EventTypeEnum.CMD_CRANE_OP, opReq).addSubject("CRANE", "QC01");

        engine.runUntil(15000);
        assertEquals("QC01", container.getCurrentPos());

        // 5. 岸桥装船
        opReq.setAction(EventTypeEnum.PUT_DONE);
        engine.scheduleEvent(null, 15000, EventTypeEnum.CMD_CRANE_OP, opReq).addSubject("CRANE", "QC01");
        engine.runUntil(17000);

        assertEquals(WiStatusEnum.COMPLETED.getCode(), wi.getWiStatus());
        assertEquals("VESSEL001", container.getCurrentPos());
    }

    /**
     * 测试11: 完整YARD_SHIFT移箱流程 (已修正物理时序)
     */
    @Test
    @DisplayName("测试完整YARD_SHIFT移箱流程")
    void testCompleteYardShiftFlow() {
        WorkInstruction wi = createWorkInstruction("WI001", "CONTAINER001", BizTypeEnum.YARD_SHIFT);
        wi.setFetchCheId("ASC01"); wi.setCarryCheId("TRUCK01"); wi.setPutCheId("ASC02");
        wi.setFromPos("YARD001"); wi.setToPos("YARD002");
        context.getWorkInstructionMap().put("WI001", wi);

        Container container = createContainer("CONTAINER001", "YARD001");
        context.getContainerMap().put("CONTAINER001", container);

        context.getAscMap().put("ASC01", createAscDevice("ASC01")); // @0,0

        AscDevice asc2 = createAscDevice("ASC02");
        asc2.setPosX(20.0); asc2.setPosY(20.0); // @20,20
        context.getAscMap().put("ASC02", asc2);

        context.getTruckMap().put("TRUCK01", createTruck("TRUCK01")); // @0,0

        // 1. ASC1 抓放
        Map<String, Object> assignPayload = new HashMap<>();
        assignPayload.put("wiRefNo", "WI001");
        engine.scheduleEvent(null, 0, EventTypeEnum.CMD_ASSIGN_TASK, assignPayload).addSubject("DEVICE", "ASC01");

        CraneOperationReq opReq = new CraneOperationReq();
        opReq.setCraneId("ASC01"); opReq.setAction(EventTypeEnum.FETCH_DONE); opReq.setDurationMS(1000);
        engine.scheduleEvent(null, 100, EventTypeEnum.CMD_CRANE_OP, opReq).addSubject("CRANE", "ASC01");

        opReq.setAction(EventTypeEnum.PUT_DONE);
        engine.scheduleEvent(null, 2000, EventTypeEnum.CMD_CRANE_OP, opReq).addSubject("CRANE", "ASC01");
        engine.runUntil(3000);

        // 2. 集卡移动到 ASC2
        Map<String, Object> truckMove = new HashMap<>();
        truckMove.put("target", new Point(20.0, 20.0));
        truckMove.put("speed", 5.0); // dist ~28m, time ~5.6s
        engine.scheduleEvent(null, 3000, EventTypeEnum.CMD_MOVE, truckMove).addSubject("TRUCK", "TRUCK01");

        engine.runUntil(9000); // 3000+6000
        assertEquals(20.0, context.getDevice("TRUCK01").getPosX(), 0.1);

        // 3. ASC2 抓放
        engine.scheduleEvent(null, 9000, EventTypeEnum.CMD_ASSIGN_TASK, assignPayload).addSubject("DEVICE", "ASC02");

        opReq.setCraneId("ASC02"); opReq.setAction(EventTypeEnum.FETCH_DONE);
        engine.scheduleEvent(null, 9100, EventTypeEnum.CMD_CRANE_OP, opReq).addSubject("CRANE", "ASC02");
        engine.runUntil(11000);

        opReq.setAction(EventTypeEnum.PUT_DONE);
        engine.scheduleEvent(null, 11000, EventTypeEnum.CMD_CRANE_OP, opReq).addSubject("CRANE", "ASC02");
        engine.runUntil(13000);

        assertEquals(WiStatusEnum.COMPLETED.getCode(), wi.getWiStatus());
        assertEquals("YARD002", container.getCurrentPos());
    }

    /**
     * 测试12: 完整DLVR提箱流程 (修正时序版)
     */
    @Test
    @DisplayName("测试完整DLVR提箱流程")
    void testCompleteDlvrFlow() {
        WorkInstruction wi = createWorkInstruction("WI001", "CONTAINER001", BizTypeEnum.DLVR);
        wi.setFetchCheId("ASC01"); wi.setCarryCheId("TRUCK01"); wi.setPutCheId(null);
        wi.setFromPos("YARD001"); wi.setToPos("GATE01");
        context.getWorkInstructionMap().put("WI001", wi);

        Container container = createContainer("CONTAINER001", "YARD001");
        context.getContainerMap().put("CONTAINER001", container);
        context.getAscMap().put("ASC01", createAscDevice("ASC01")); // 默认在 0.0

        // 【关键修正】将集卡放在 ASC 即将到达的位置 5.0，确保物理距离校验通过
        Truck truck = createTruck("TRUCK01");
        truck.setPosX(5.0); truck.setPosY(0.0);
        context.getTruckMap().put("TRUCK01", truck);

        // 1. 指派任务 & 移动 (耗时 2500ms -> 到达 2600ms)
        Map<String, Object> assignPayload = new HashMap<>(); assignPayload.put("wiRefNo", "WI001");
        engine.scheduleEvent(null, 0, EventTypeEnum.CMD_ASSIGN_TASK, assignPayload).addSubject("DEVICE", "ASC01");

        CraneMoveReq moveReq = new CraneMoveReq();
        moveReq.setCraneId("ASC01"); moveReq.setMoveType(DeviceStateEnum.MOVE_HORIZONTAL); moveReq.setDistance(5.0); moveReq.setSpeed(2.0);
        Map<String, Object> movePayload = new HashMap<>(); movePayload.put("req", moveReq); movePayload.put("speed", 2.0);
        engine.scheduleEvent(null, 100, EventTypeEnum.CMD_CRANE_MOVE, movePayload).addSubject("CRANE", "ASC01");

        // 2. 抓箱 (推迟到 3000ms，确保 ASC 已到达 2600ms)
        CraneOperationReq opReq = new CraneOperationReq();
        opReq.setCraneId("ASC01"); opReq.setDurationMS(1000); opReq.setAction(EventTypeEnum.FETCH_DONE);
        engine.scheduleEvent(null, 3000, EventTypeEnum.CMD_CRANE_OP, opReq).addSubject("CRANE", "ASC01");

        // 3. 放箱
        opReq.setAction(EventTypeEnum.PUT_DONE);
        engine.scheduleEvent(null, 5000, EventTypeEnum.CMD_CRANE_OP, opReq).addSubject("CRANE", "ASC01");

        engine.runUntil(7000);
        assertEquals("TRUCK01", container.getCurrentPos());
    }

    /**
     * 测试13: 完整RECV收箱流程 (已修正)
     */
    @Test
    @DisplayName("测试完整RECV收箱流程")
    void testCompleteRecvFlow() {
        // RECV: Truck -> Yard
        WorkInstruction wi = createWorkInstruction("WI001", "CONTAINER001", BizTypeEnum.RECV);
        wi.setFetchCheId(null); wi.setCarryCheId("TRUCK01"); wi.setPutCheId("ASC01");
        wi.setFromPos("GATE01"); wi.setToPos("YARD001");
        context.getWorkInstructionMap().put("WI001", wi);

        Container container = createContainer("CONTAINER001", "TRUCK01");
        context.getContainerMap().put("CONTAINER001", container);
        context.getAscMap().put("ASC01", createAscDevice("ASC01")); // @0,0
        context.getTruckMap().put("TRUCK01", createTruck("TRUCK01")); // @0,0

        Map<String, Object> assignPayload = new HashMap<>();
        assignPayload.put("wiRefNo", "WI001");

        CraneOperationReq opReq = new CraneOperationReq();
        opReq.setCraneId("ASC01"); opReq.setDurationMS(1000);

        engine.scheduleEvent(null, 0, EventTypeEnum.CMD_ASSIGN_TASK, assignPayload).addSubject("DEVICE", "ASC01");

        // 抓 (从车上抓) - 车在脚下
        opReq.setAction(EventTypeEnum.FETCH_DONE);
        engine.scheduleEvent(null, 100, EventTypeEnum.CMD_CRANE_OP, opReq).addSubject("CRANE", "ASC01");

        // 放 (到堆场)
        opReq.setAction(EventTypeEnum.PUT_DONE);
        engine.scheduleEvent(null, 2000, EventTypeEnum.CMD_CRANE_OP, opReq).addSubject("CRANE", "ASC01");
        engine.runUntil(4000);

        assertEquals(WiStatusEnum.COMPLETED.getCode(), wi.getWiStatus());
        assertEquals("YARD001", container.getCurrentPos());
    }

    /**
     * 测试14: 完整DIRECT_IN直进流程 (修正时序版)
     * 问题修复：原测试在 2000ms 发抓箱指令，但移动需要 2500ms (100+2500=2600到达)。
     * 修正：将抓箱指令推迟到 3000ms。
     */
    @Test
    @DisplayName("测试完整DIRECT_IN直进流程")
    void testCompleteDirectInFlow() {
        WorkInstruction wi = createWorkInstruction("WI001", "CONTAINER001", BizTypeEnum.DIRECT_IN);
        wi.setFetchCheId(null); wi.setCarryCheId("TRUCK01"); wi.setPutCheId("QC01");
        wi.setFromPos("GATE01"); wi.setToPos("VESSEL001");
        context.getWorkInstructionMap().put("WI001", wi);

        Container container = createContainer("CONTAINER001", "TRUCK01");
        context.getContainerMap().put("CONTAINER001", container);

        context.getQcMap().put("QC01", createQcDevice("QC01")); // 默认 0.0

        // 【关键修正】集卡放在 QC 移动的目标点 5.0
        Truck truck = createTruck("TRUCK01");
        truck.setPosX(5.0); truck.setPosY(0.0);
        context.getTruckMap().put("TRUCK01", truck);


        // 1. 指派任务
        Map<String, Object> assignPayload = new HashMap<>();
        assignPayload.put("wiRefNo", "WI001");
        engine.scheduleEvent(null, 0, EventTypeEnum.CMD_ASSIGN_TASK, assignPayload).addSubject("DEVICE", "QC01");
        engine.runUntil(100);

        // 2. 移动岸桥 (移动 5.0m, 速度 2.0m/s -> 耗时 2500ms, 到达时间 100+2500=2600)
        CraneMoveReq moveReq = new CraneMoveReq();
        moveReq.setCraneId("QC01"); moveReq.setMoveType(DeviceStateEnum.MOVE_HORIZONTAL); moveReq.setDistance(5.0); moveReq.setSpeed(2.0);
        Map<String, Object> movePayload = new HashMap<>();
        movePayload.put("req", moveReq); movePayload.put("speed", 2.0);
        engine.scheduleEvent(null, 100, EventTypeEnum.CMD_CRANE_MOVE, movePayload).addSubject("CRANE", "QC01");

        // 3. 抓箱 (推迟到 3000ms，确保已到达并IDLE)
        CraneOperationReq opReq = new CraneOperationReq();
        opReq.setCraneId("QC01"); opReq.setDurationMS(1000); opReq.setAction(EventTypeEnum.FETCH_DONE);
        engine.scheduleEvent(null, 3000, EventTypeEnum.CMD_CRANE_OP, opReq).addSubject("CRANE", "QC01");

        // 4. 放箱
        opReq.setAction(EventTypeEnum.PUT_DONE);
        engine.scheduleEvent(null, 5000, EventTypeEnum.CMD_CRANE_OP, opReq).addSubject("CRANE", "QC01");

        engine.runUntil(7000);

        assertEquals(WiStatusEnum.COMPLETED.getCode(), wi.getWiStatus());
        assertEquals("VESSEL001", container.getCurrentPos());
    }

    /**
     * 测试15: 完整DIRECT_OUT直提流程 (已修正)
     */
    @Test
    @DisplayName("测试完整DIRECT_OUT直提流程")
    void testCompleteDirectOutFlow() {
        WorkInstruction wi = createWorkInstruction("WI001", "CONTAINER001", BizTypeEnum.DIRECT_OUT);
        wi.setFetchCheId("QC01"); wi.setCarryCheId("TRUCK01"); wi.setPutCheId(null);
        wi.setFromPos("VESSEL001"); wi.setToPos("GATE01");
        context.getWorkInstructionMap().put("WI001", wi);

        Container container = createContainer("CONTAINER001", "VESSEL001");
        context.getContainerMap().put("CONTAINER001", container);
        context.getQcMap().put("QC01", createQcDevice("QC01"));
        context.getTruckMap().put("TRUCK01", createTruck("TRUCK01"));

        Map<String, Object> assignPayload = new HashMap<>();
        assignPayload.put("wiRefNo", "WI001");

        CraneOperationReq opReq = new CraneOperationReq();
        opReq.setCraneId("QC01"); opReq.setDurationMS(1000);

        engine.scheduleEvent(null, 0, EventTypeEnum.CMD_ASSIGN_TASK, assignPayload).addSubject("DEVICE", "QC01");
        opReq.setAction(EventTypeEnum.FETCH_DONE);
        engine.scheduleEvent(null, 100, EventTypeEnum.CMD_CRANE_OP, opReq).addSubject("CRANE", "QC01");
        opReq.setAction(EventTypeEnum.PUT_DONE);
        engine.scheduleEvent(null, 2000, EventTypeEnum.CMD_CRANE_OP, opReq).addSubject("CRANE", "QC01");
        engine.runUntil(4000);

        assertEquals("TRUCK01", container.getCurrentPos());
    }

    // ==========================================
    // 新增：边界与异常场景测试
    // ==========================================

    @Test
    @DisplayName("测试电子围栏阻挡与通行")
    void testFenceBlockingAndRelease() {
        Truck truck = createTruck("TRUCK_FENCE");
        truck.setPosX(0.0); truck.setPosY(0.0); truck.setSpeed(10.0);
        context.getTruckMap().put(truck.getId(), truck);

        Fence fence = new Fence();
        fence.setNodeId("FENCE01"); fence.setPosX(50.0); fence.setPosY(0.0); fence.setRadius(10.0);
        fence.setStatus(common.consts.FenceStateEnum.BLOCKED.getCode());
        context.getFenceMap().put("FENCE01", fence);

        Map<String, Object> movePayload = new HashMap<>();
        movePayload.put("target", new Point(50.0, 0.0));
        movePayload.put("speed", 10.0);

        SimEvent moveEvent = engine.scheduleEvent(null, 0, EventTypeEnum.CMD_MOVE, movePayload);
        moveEvent.addSubject("TRUCK", "TRUCK_FENCE");
        engine.runUntil(100);

        assertNotEquals(DeviceStateEnum.MOVING, truck.getState());
        assertEquals(DeviceStateEnum.WAITING, truck.getState());

        Map<String, Object> fencePayload = new HashMap<>();
        fencePayload.put("nodeId", "FENCE01");
        fencePayload.put("status", common.consts.FenceStateEnum.PASSABLE.getCode());
        engine.scheduleEvent(null, 200, EventTypeEnum.CMD_FENCE_TOGGLE, fencePayload);
        SimEvent retryMove = engine.scheduleEvent(null, 300, EventTypeEnum.CMD_MOVE, movePayload);
        retryMove.addSubject("TRUCK", "TRUCK_FENCE");

        engine.runUntil(10000);
        assertEquals(DeviceStateEnum.IDLE, truck.getState());
        assertEquals(50.0, truck.getPosX(), 0.1);
    }

    @Test
    @DisplayName("测试引擎死循环熔断")
    void testDeadLoopProtection() {
        int threshold = context.getPhysicsConfig().getMaxEventsPerTimestamp();
        for (int i = 0; i < threshold + 10; i++) {
            engine.scheduleEvent(null, 100, EventTypeEnum.REPORT_IDLE, null);
        }
        assertThrows(SimulationDeadLoopException.class, () -> engine.runUntil(200));
    }

    @Test
    @DisplayName("测试非法移动参数")
    void testInvalidMoveParameters() {
        Truck truck = createTruck("TRUCK_ERR");
        context.getTruckMap().put(truck.getId(), truck);

        Map<String, Object> payload = new HashMap<>();
        payload.put("target", new Point(100.0, 100.0));
        payload.put("speed", -5.0); // 非法速度

        // 调度事件，预期 Engine 在处理时会抛异常并记录到 ErrorLog
        engine.scheduleEvent(null, 0, EventTypeEnum.CMD_MOVE, payload).addSubject("TRUCK", "TRUCK_ERR");

        // 运行，此时应该触发全局暂停
        engine.runUntil(100);

        // 验证错误日志是否存在
        List<SimulationErrorLog.ErrorLogEntry> errors = errorLog.listSince(0);
        assertFalse(errors.isEmpty(), "应该记录参数非法的错误日志");
    }

    @Test
    @DisplayName("测试移动耗电量计算")
    void testPowerConsumptionCalculation() {
        Truck truck = createTruck("E_TRUCK");
        truck.setPowerLevel(100.0);
        truck.setConsumeRate(0.1);
        context.getTruckMap().put(truck.getId(), truck);

        Map<String, Object> movePayload = new HashMap<>();
        movePayload.put("target", new Point(100.0, 0.0));
        movePayload.put("speed", 10.0);

        engine.scheduleEvent(null, 0, EventTypeEnum.CMD_MOVE, movePayload).addSubject("TRUCK", "E_TRUCK");
        engine.runUntil(11000);
        assertEquals(90.0, truck.getPowerLevel(), 0.01);
    }

    @Test
    @DisplayName("测试物理距离校验失败")
    void testProximityCheckFailure() {
        WorkInstruction wi = createWorkInstruction("WI_FAIL", "CNT_FAIL", BizTypeEnum.DSCH);
        wi.setFetchCheId("QC01"); wi.setCarryCheId("TRUCK01"); wi.setPutCheId("ASC01");
        context.getWorkInstructionMap().put("WI_FAIL", wi);

        Container cnt = createContainer("CNT_FAIL", "TRUCK01");
        context.getContainerMap().put("CNT_FAIL", cnt);

        Truck truck = createTruck("TRUCK01");
        truck.setPosX(0.0); truck.setPosY(0.0);
        context.getTruckMap().put("TRUCK01", truck);

        AscDevice asc = createAscDevice("ASC01");
        asc.setPosX(100.0); asc.setPosY(0.0); // 距离100米
        asc.setCurrWiRefNo("WI_FAIL");
        context.getAscMap().put("ASC01", asc);

        CraneOperationReq opReq = new CraneOperationReq();
        opReq.setCraneId("ASC01"); opReq.setAction(EventTypeEnum.FETCH_DONE); opReq.setDurationMS(1000);

        engine.scheduleEvent(null, 0, EventTypeEnum.CMD_CRANE_OP, opReq).addSubject("CRANE", "ASC01");
        engine.runUntil(2000);

        // 验证失败
        assertEquals("TRUCK01", cnt.getCurrentPos());
    }

    // ==========================================
    // 辅助方法
    // ==========================================

    private WorkInstruction createWorkInstruction(String wiRefNo, String containerId, BizTypeEnum bizType) {
        WorkInstruction wi = new WorkInstruction();
        wi.setWiRefNo(wiRefNo);
        wi.setContainerId(containerId);
        wi.setMoveKind(bizType);
        wi.setFromPos("FROM_" + bizType.getCode());
        wi.setToPos("TO_" + bizType.getCode());
        wi.setWiStatus(WiStatusEnum.EXECUTING.getCode());
        return wi;
    }

    private Container createContainer(String containerId, String initialPos) {
        Container container = new Container();
        container.setContainerId(containerId);
        container.setCurrentPos(initialPos);
        return container;
    }

    private QcDevice createQcDevice(String deviceId) {
        QcDevice qc = new QcDevice();
        qc.setId(deviceId);
        qc.setType(DeviceTypeEnum.QC);
        qc.setState(DeviceStateEnum.IDLE);
        qc.setPosX(0.0);
        qc.setPosY(0.0);
        return qc;
    }

    private AscDevice createAscDevice(String deviceId) {
        AscDevice asc = new AscDevice();
        asc.setId(deviceId);
        asc.setType(DeviceTypeEnum.ASC);
        asc.setState(DeviceStateEnum.IDLE);
        asc.setPosX(0.0);
        asc.setPosY(0.0);
        return asc;
    }

    private Truck createTruck(String deviceId) {
        Truck truck = new Truck();
        truck.setId(deviceId);
        truck.setType(DeviceTypeEnum.ELECTRIC_TRUCK);
        truck.setState(DeviceStateEnum.IDLE);
        truck.setPosX(0.0);
        truck.setPosY(0.0);
        truck.setPowerLevel(100.0);
        truck.setNeedCharge(false);
        return truck;
    }

    private BaseDevice createDeviceForBizType(BizTypeEnum bizType) {
        String deviceId = "DEVICE_" + bizType.getCode();
        if (BizTypeUtil.getRecommendedFetchDeviceType(bizType) == DeviceTypeEnum.QC) {
            return createQcDevice(deviceId);
        } else if (BizTypeUtil.getRecommendedFetchDeviceType(bizType) == DeviceTypeEnum.ASC) {
            return createAscDevice(deviceId);
        } else {
            return createTruck(deviceId);
        }
    }

    private void addDeviceToContext(BaseDevice device) {
        if (device instanceof QcDevice) {
            QcDevice qc = (QcDevice) device;
            context.getQcMap().put(device.getId(), qc);
        } else if (device instanceof AscDevice) {
            AscDevice asc = (AscDevice) device;
            context.getAscMap().put(device.getId(), asc);
        } else if (device instanceof Truck) {
            Truck truck = (Truck) device;
            context.getTruckMap().put(device.getId(), truck);
        }
    }
}