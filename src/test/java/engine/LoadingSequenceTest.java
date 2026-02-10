package engine;

import common.consts.BizTypeEnum;
import common.consts.DeviceTypeEnum;
import common.consts.EventTypeEnum;
import common.exception.BusinessException;
import common.util.VesselStowageMock;
import model.bo.GlobalContext;
import model.dto.request.AssignTaskReq;
import model.entity.Container;
import model.entity.QcDevice;
import model.entity.WorkInstruction;
import model.dto.request.CraneOperationReq;
import model.entity.Truck;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import service.algorithm.TaskDecisionService;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 验证 PDF 描述的装船顺序业务逻辑
 * 修正版：直接测试 TaskDecisionService 接口，确保校验逻辑被触发
 */
@SpringBootTest(classes = application.SecsApplication.class)
public class LoadingSequenceTest {

    @Autowired
    private SimulationEngine engine;

    @Autowired
    private TaskDecisionService taskDecisionService; // 注入决策服务

    private final VesselStowageMock vesselMock = VesselStowageMock.getInstance();
    private final GlobalContext context = GlobalContext.getInstance();

    @BeforeEach
    void setUp() {
        engine.reset();
        vesselMock.reset();
        context.clearAll();
        setupBasicDevices();
    }

    /**
     * 场景1：违反物理堆叠顺序（悬空装箱）
     * 预期：TaskDecisionService 直接抛出 BusinessException
     */
    @Test
    @DisplayName("验证：违反从下往上装船顺序应被拦截")
    void testViolationOfLoadingSequence() {
        // 1. 创建一个要装在第二层（TIER02）的指令，但此时 TIER01 是空的
        String targetPos = "BAY01-01-02";
        createLoadTask("WI_TOP", "CNT_TOP", targetPos);

        // 2. 构造请求对象
        AssignTaskReq req = new AssignTaskReq();
        req.setWiRefNo("WI_TOP");
        req.setDeviceId("QC01");
        req.setDeviceType(DeviceTypeEnum.QC);

        System.out.println("--- 测试开始：尝试直接装载第二层（悬空） ---");

        // 3. 核心断言：调用决策服务，应该抛出异常
        BusinessException exception = assertThrows(BusinessException.class, () -> {
            taskDecisionService.evaluateAndDecide(req);
        });

        System.out.println("成功捕获预期异常: " + exception.getMessage());
        assertTrue(exception.getMessage().contains("违反装船顺序约束"));
    }

    /**
     * 场景2：正确的装船顺序（先底层后上层）
     * 预期：Service 校验通过，任务正常执行
     */
    @Test
    @DisplayName("验证：正确的从下往上装船顺序")
    void testCorrectLoadingSequence() {
        String posBottom = "BAY01-01-01"; // 第一层
        String posTop = "BAY01-01-02";    // 第二层

        createLoadTask("WI_BOT", "CNT_BOT", posBottom);
        createLoadTask("WI_TOP", "CNT_TOP", posTop);

        System.out.println("--- 测试开始：按顺序装载 ---");

        // === Step 1: 装载第一层 ===
        AssignTaskReq reqBot = new AssignTaskReq();
        reqBot.setWiRefNo("WI_BOT");
        reqBot.setDeviceId("QC01");
        reqBot.setDeviceType(DeviceTypeEnum.QC);

        // 断言：第一层校验应该通过
        assertDoesNotThrow(() -> taskDecisionService.evaluateAndDecide(reqBot));

        // 推进仿真状态
        scheduleAssignTaskEvent("WI_BOT", "QC01");
        scheduleCraneOps("QC01", 100, 2000);
        engine.runUntil(2500);

        // 【关键】手动更新 Mock 状态（模拟物理层作业完成后反馈给计划层）
        vesselMock.confirmStowage(posBottom);

        // === Step 2: 装载第二层 ===
        AssignTaskReq reqTop = new AssignTaskReq();
        reqTop.setWiRefNo("WI_TOP");
        reqTop.setDeviceId("QC01");
        reqTop.setDeviceType(DeviceTypeEnum.QC);

        // 断言：前一层完成后，第二层校验应该通过
        assertDoesNotThrow(() -> taskDecisionService.evaluateAndDecide(reqTop));

        // 推进仿真
        scheduleAssignTaskEvent("WI_TOP", "QC01");
        scheduleCraneOps("QC01", 2600, 4000);
        engine.runUntil(5000);

        vesselMock.confirmStowage(posTop);
        System.out.println("测试通过：严格遵守了装船顺序");
    }

    // --- 辅助方法 ---

    private void setupBasicDevices() {
        QcDevice qc = new QcDevice();
        qc.setId("QC01");
        qc.setType(DeviceTypeEnum.QC);
        qc.setState(common.consts.DeviceStateEnum.IDLE);
        qc.setPosX(0.0); qc.setPosY(0.0);
        context.getQcMap().put("QC01", qc);

        Truck t = new Truck();
        t.setId("TRUCK01");
        t.setType(DeviceTypeEnum.ELECTRIC_TRUCK);
        t.setPosX(0.0); t.setPosY(0.0);
        context.getTruckMap().put("TRUCK01", t);
    }

    private void createLoadTask(String wiRef, String cntId, String toPos) {
        WorkInstruction wi = new WorkInstruction();
        wi.setWiRefNo(wiRef);
        wi.setContainerId(cntId);
        wi.setMoveKind(BizTypeEnum.LOAD);
        wi.setToPos(toPos);
        wi.setFetchCheId("ASC01");
        wi.setCarryCheId("TRUCK01");
        wi.setPutCheId("QC01");
        context.getWorkInstructionMap().put(wiRef, wi);

        Container c = new Container();
        c.setContainerId(cntId);
        c.setCurrentPos("TRUCK01");
        context.getContainerMap().put(cntId, c);
    }

    private void scheduleAssignTaskEvent(String wiRef, String deviceId) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("wiRefNo", wiRef);
        engine.scheduleEvent(null, context.getSimTime(), EventTypeEnum.CMD_ASSIGN_TASK, payload).addSubject("DEVICE", deviceId);
    }

    private void scheduleCraneOps(String craneId, long startTime, long endTime) {
        CraneOperationReq fetch = new CraneOperationReq();
        fetch.setCraneId(craneId);
        fetch.setAction(EventTypeEnum.FETCH_DONE);
        fetch.setDurationMS(500);
        engine.scheduleEvent(null, startTime, EventTypeEnum.CMD_CRANE_OP, fetch).addSubject("CRANE", craneId);

        CraneOperationReq put = new CraneOperationReq();
        put.setCraneId(craneId);
        put.setAction(EventTypeEnum.PUT_DONE);
        put.setDurationMS(500);
        engine.scheduleEvent(null, endTime, EventTypeEnum.CMD_CRANE_OP, put).addSubject("CRANE", craneId);
    }
}