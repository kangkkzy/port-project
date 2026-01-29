package engine;

import common.consts.BizTypeEnum;
import common.consts.DeviceStateEnum;
import common.consts.DeviceTypeEnum;
import common.consts.EventTypeEnum;
import common.consts.WiStatusEnum;
import common.util.BizTypeUtil;
import model.bo.GlobalContext;
import model.entity.*;
import model.dto.request.CraneMoveReq;
import model.dto.request.CraneOperationReq;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import service.algorithm.impl.SimulationErrorLog;
import service.algorithm.impl.SimulationEventLog;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 完整离散仿真系统测试
 * 覆盖所有核心功能和业务流程
 */
@SpringBootTest(classes = application.SecsApplication.class)
@TestPropertySource(properties = {
        "spring.main.allow-bean-definition-overriding=true"
})
@DisplayName("完整离散仿真系统测试")
class FullSimulationTest {

    @Autowired
    private SimulationEngine engine;

    @Autowired
    private SimulationEventLog eventLog;

    @Autowired
    private SimulationErrorLog errorLog;

    private GlobalContext context;

    @BeforeEach
    void setUp() {
        context = GlobalContext.getInstance();
        context.clearAll();
        engine.reset();
    }

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
     * 测试2: 完整DSCH业务流程
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

        // 2. 创建集装箱
        Container container = createContainer("CONTAINER001", "VESSEL001-BAY01");
        context.getContainerMap().put("CONTAINER001", container);

        // 3. 创建设备
        QcDevice qc = createQcDevice("QC01");
        context.getQcMap().put("QC01", qc);

        Truck truck = createTruck("TRUCK01");
        context.getTruckMap().put("TRUCK01", truck);

        AscDevice asc = createAscDevice("ASC01");
        context.getAscMap().put("ASC01", asc);

        // 4. 指派任务给岸桥
        Map<String, Object> assignPayload = new HashMap<>();
        assignPayload.put("wiRefNo", "WI001");
        SimEvent assignEvent = engine.scheduleEvent(null, 0, EventTypeEnum.CMD_ASSIGN_TASK, assignPayload);
        assignEvent.addSubject("DEVICE", "QC01");

        // 5. 推进时间处理任务指派
        engine.runUntil(100);

        // 验证：设备已绑定任务
        assertEquals("WI001", qc.getCurrWiRefNo(), "岸桥应该绑定任务WI001");
        assertEquals(DeviceStateEnum.WORKING, qc.getState(), "岸桥状态应该是WORKING");

        // 6. 移动岸桥到抓箱位置
        CraneMoveReq moveReq = new CraneMoveReq();
        moveReq.setCraneId("QC01");
        moveReq.setMoveType(DeviceStateEnum.MOVE_HORIZONTAL);
        moveReq.setDistance(10.0);
        moveReq.setSpeed(2.0);

        Map<String, Object> movePayload = new HashMap<>();
        movePayload.put("req", moveReq);
        movePayload.put("speed", 2.0);

        SimEvent moveEvent = engine.scheduleEvent(null, 100, EventTypeEnum.CMD_CRANE_MOVE, movePayload);
        moveEvent.addSubject("CRANE", "QC01");

        // 推进时间到移动完成（10米/2米每秒 = 5秒 = 5000毫秒）
        engine.runUntil(6000);

        // 验证：设备已到达位置
        assertEquals(DeviceStateEnum.IDLE, qc.getState(), "岸桥移动后应该处于IDLE状态");

        // 7. 执行抓箱操作
        CraneOperationReq opReq = new CraneOperationReq();
        opReq.setCraneId("QC01");
        opReq.setAction(EventTypeEnum.FETCH_DONE);
        opReq.setDurationMS(2000);

        SimEvent opEvent = engine.scheduleEvent(null, 6000, EventTypeEnum.CMD_CRANE_OP, opReq);
        opEvent.addSubject("CRANE", "QC01");

        // 推进时间处理抓箱
        engine.runUntil(10000);

        // 验证：集装箱位置已更新
        assertEquals("QC01", container.getCurrentPos(), "集装箱应该在岸桥上");

        // 8. 移动岸桥到集卡位置（放箱）
        moveReq.setDistance(5.0);
        SimEvent moveEvent2 = engine.scheduleEvent(null, 10000, EventTypeEnum.CMD_CRANE_MOVE, movePayload);
        moveEvent2.addSubject("CRANE", "QC01");
        engine.runUntil(15000);

        // 9. 执行放箱操作（集装箱转移到集卡）
        opReq.setAction(EventTypeEnum.PUT_DONE);
        SimEvent opEvent2 = engine.scheduleEvent(null, 15000, EventTypeEnum.CMD_CRANE_OP, opReq);
        opEvent2.addSubject("CRANE", "QC01");
        engine.runUntil(17000);

        // 验证：集装箱位置已更新到集卡
        assertEquals("TRUCK01", container.getCurrentPos(), "集装箱应该在集卡上");

        // 10. 集卡移动到堆场
        Map<String, Object> truckMovePayload = new HashMap<>();
        truckMovePayload.put("target", new Point(100.0, 200.0));
        truckMovePayload.put("speed", 5.0);
        SimEvent truckMoveEvent = engine.scheduleEvent(null, 17000, EventTypeEnum.CMD_MOVE, truckMovePayload);
        truckMoveEvent.addSubject("TRUCK", "TRUCK01");
        engine.runUntil(20000);

        // 11. 集卡到达堆场，龙门吊抓箱
        assignPayload.put("wiRefNo", "WI001");
        SimEvent assignEvent2 = engine.scheduleEvent(null, 20000, EventTypeEnum.CMD_ASSIGN_TASK, assignPayload);
        assignEvent2.addSubject("DEVICE", "ASC01");
        engine.runUntil(20100);

        // 12. 龙门吊移动到集卡位置
        moveReq.setCraneId("ASC01");
        SimEvent moveEvent3 = engine.scheduleEvent(null, 20100, EventTypeEnum.CMD_CRANE_MOVE, movePayload);
        moveEvent3.addSubject("CRANE", "ASC01");
        engine.runUntil(21000);

        // 13. 龙门吊抓箱
        opReq.setCraneId("ASC01");
        opReq.setAction(EventTypeEnum.FETCH_DONE);
        SimEvent opEvent3 = engine.scheduleEvent(null, 21000, EventTypeEnum.CMD_CRANE_OP, opReq);
        opEvent3.addSubject("CRANE", "ASC01");
        engine.runUntil(23000);

        // 14. 龙门吊移动到堆场位置并放箱
        moveReq.setDistance(3.0);
        SimEvent moveEvent4 = engine.scheduleEvent(null, 23000, EventTypeEnum.CMD_CRANE_MOVE, movePayload);
        moveEvent4.addSubject("CRANE", "ASC01");
        engine.runUntil(24000);

        opReq.setAction(EventTypeEnum.PUT_DONE);
        SimEvent opEvent4 = engine.scheduleEvent(null, 24000, EventTypeEnum.CMD_CRANE_OP, opReq);
        opEvent4.addSubject("CRANE", "ASC01");
        engine.runUntil(26000);

        // 验证：作业完成
        assertEquals(WiStatusEnum.COMPLETED.getCode(), wi.getWiStatus(), "作业指令应该已完成");
        assertEquals("YARD001", container.getCurrentPos(), "集装箱应该在最终位置YARD001");
        assertEquals(DeviceStateEnum.IDLE, asc.getState(), "龙门吊应该处于IDLE状态");

        // 验证：事件日志
        List<model.dto.snapshot.EventLogEntryDto> events = eventLog.listSince(0);
        assertFalse(events.isEmpty(), "应该有事件被处理");
    }

    /**
     * 测试3: 所有业务类型基本流程
     */
    @Test
    @DisplayName("测试所有业务类型")
    void testAllBusinessTypes() {
        for (BizTypeEnum bizType : BizTypeEnum.values()) {
            // 创建作业指令
            String wiRefNo = "WI_" + bizType.getCode();
            WorkInstruction wi = createWorkInstruction(wiRefNo, "CONTAINER_" + bizType.getCode(), bizType);
            context.getWorkInstructionMap().put(wiRefNo, wi);

            // 创建集装箱
            Container container = createContainer("CONTAINER_" + bizType.getCode(), wi.getFromPos());
            context.getContainerMap().put(container.getContainerId(), container);

            // 根据业务类型创建设备
            BaseDevice device = createDeviceForBizType(bizType);
            addDeviceToContext(device);

            // 指派任务
            Map<String, Object> assignPayload = new HashMap<>();
            assignPayload.put("wiRefNo", wiRefNo);
            SimEvent assignEvent = engine.scheduleEvent(null, 0, EventTypeEnum.CMD_ASSIGN_TASK, assignPayload);
            assignEvent.addSubject("DEVICE", device.getId());

            // 推进时间处理任务指派
            engine.runUntil(100);

            // 验证：设备已绑定任务
            assertEquals(wiRefNo, device.getCurrWiRefNo(),
                    String.format("业务类型 %s 的设备应该绑定任务", bizType.getDesc()));

            // 验证：业务类型工具类
            assertNotNull(BizTypeUtil.getFullDescription(bizType), "业务类型应该有描述");
        }
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
     * 测试5: 业务类型暂停机制
     */
    @Test
    @DisplayName("测试业务类型暂停机制")
    void testBusinessTypeSuspension() {
        // 创建两个不同业务类型的任务
        WorkInstruction wi1 = createWorkInstruction("WI001", "CONTAINER001", BizTypeEnum.DSCH);
        WorkInstruction wi2 = createWorkInstruction("WI002", "CONTAINER002", BizTypeEnum.LOAD);

        context.getWorkInstructionMap().put("WI001", wi1);
        context.getWorkInstructionMap().put("WI002", wi2);

        // 创建设备
        QcDevice qc1 = createQcDevice("QC01");
        QcDevice qc2 = createQcDevice("QC02");
        context.getQcMap().put("QC01", qc1);
        context.getQcMap().put("QC02", qc2);

        // 指派任务
        Map<String, Object> assignPayload1 = new HashMap<>();
        assignPayload1.put("wiRefNo", "WI001");
        SimEvent assignEvent1 = engine.scheduleEvent(null, 0, EventTypeEnum.CMD_ASSIGN_TASK, assignPayload1);
        assignEvent1.addSubject("DEVICE", "QC01");

        Map<String, Object> assignPayload2 = new HashMap<>();
        assignPayload2.put("wiRefNo", "WI002");
        SimEvent assignEvent2 = engine.scheduleEvent(null, 0, EventTypeEnum.CMD_ASSIGN_TASK, assignPayload2);
        assignEvent2.addSubject("DEVICE", "QC02");

        // 推进时间
        engine.runUntil(100);

        // 验证：两个设备都绑定了任务
        assertEquals("WI001", qc1.getCurrWiRefNo(), "QC01应该绑定WI001");
        assertEquals("WI002", qc2.getCurrWiRefNo(), "QC02应该绑定WI002");

        // 查询暂停状态
        java.util.Set<BizTypeEnum> suspendedBizTypes = engine.getSuspendedBizTypes();
        assertNotNull(suspendedBizTypes, "暂停业务类型集合应该存在");
        assertEquals(0, suspendedBizTypes.size(), "初始时不应该有暂停的业务类型");
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

    /**
     * 测试7: 设备状态管理
     */
    @Test
    @DisplayName("测试设备状态管理")
    void testDeviceStateManagement() {
        // 创建设备
        QcDevice qc = createQcDevice("QC01");
        context.getQcMap().put("QC01", qc);

        // 初始状态应该是IDLE
        assertEquals(DeviceStateEnum.IDLE, qc.getState(), "初始状态应该是IDLE");

        // 指派任务
        WorkInstruction wi = createWorkInstruction("WI001", "CONTAINER001", BizTypeEnum.DSCH);
        context.getWorkInstructionMap().put("WI001", wi);

        Map<String, Object> assignPayload = new HashMap<>();
        assignPayload.put("wiRefNo", "WI001");
        SimEvent assignEvent = engine.scheduleEvent(null, 0, EventTypeEnum.CMD_ASSIGN_TASK, assignPayload);
        assignEvent.addSubject("DEVICE", "QC01");

        engine.runUntil(100);

        // 验证：设备状态应该是WORKING
        assertEquals(DeviceStateEnum.WORKING, qc.getState(), "指派任务后状态应该是WORKING");
    }

    /**
     * 测试8: 集装箱位置跟踪
     */
    @Test
    @DisplayName("测试集装箱位置跟踪")
    void testContainerPositionTracking() {
        // 创建作业指令和集装箱
        WorkInstruction wi = createWorkInstruction("WI001", "CONTAINER001", BizTypeEnum.DSCH);
        wi.setFetchCheId("QC01");
        wi.setFromPos("VESSEL001");
        wi.setToPos("YARD001");
        context.getWorkInstructionMap().put("WI001", wi);

        Container container = createContainer("CONTAINER001", "VESSEL001");
        context.getContainerMap().put("CONTAINER001", container);

        // 创建设备
        QcDevice qc = createQcDevice("QC01");
        context.getQcMap().put("QC01", qc);

        // 指派任务并抓箱
        Map<String, Object> assignPayload = new HashMap<>();
        assignPayload.put("wiRefNo", "WI001");
        SimEvent assignEvent = engine.scheduleEvent(null, 0, EventTypeEnum.CMD_ASSIGN_TASK, assignPayload);
        assignEvent.addSubject("DEVICE", "QC01");
        engine.runUntil(100);

        // 执行抓箱
        CraneOperationReq opReq = new CraneOperationReq();
        opReq.setCraneId("QC01");
        opReq.setAction(EventTypeEnum.FETCH_DONE);
        opReq.setDurationMS(1000);
        SimEvent opEvent = engine.scheduleEvent(null, 100, EventTypeEnum.CMD_CRANE_OP, opReq);
        opEvent.addSubject("CRANE", "QC01");
        engine.runUntil(2000);

        // 验证：集装箱位置已更新
        assertEquals("QC01", container.getCurrentPos(), "集装箱应该在岸桥上");
    }

    // ========== 辅助方法 ==========

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
