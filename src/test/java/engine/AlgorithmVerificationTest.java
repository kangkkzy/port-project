package engine;

import common.consts.BizTypeEnum;
import common.consts.DeviceTypeEnum;
import common.exception.BusinessException;
import common.util.VesselStowageMock;
import common.util.YardStowageMock;
import model.bo.GlobalContext;
import model.dto.request.AssignTaskReq;
import model.entity.WorkInstruction;
import model.entity.QcDevice;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import service.algorithm.TaskDecisionService;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 完整算法逻辑验证测试
 * 覆盖 PDF 描述的装船、卸船、堆场作业的核心顺序约束
 */
@SpringBootTest(classes = application.SecsApplication.class)
public class AlgorithmVerificationTest {

    @Autowired
    private TaskDecisionService taskDecisionService;

    private final VesselStowageMock vesselMock = VesselStowageMock.getInstance();
    private final YardStowageMock yardMock = YardStowageMock.getInstance();

    @BeforeEach
    void setUp() {
        vesselMock.reset();
        yardMock.reset();
        GlobalContext.getInstance().clearAll();

        // 注册一个虚拟设备用于测试，防止 DEVICE_NOT_FOUND 错误
        QcDevice qc = new QcDevice();
        qc.setId("QC01");
        qc.setType(DeviceTypeEnum.QC);
        GlobalContext.getInstance().getQcMap().put("QC01", qc);
    }

    // ==========================================
    // 1. 卸船流程验证 (Discharge)
    // 规则：必须先卸上面的箱子 (Top -> Bottom)
    // ==========================================
    @Test
    @DisplayName("验证全算法：卸船顺序约束 (Top-Down)")
    void testDischargeSequence() {
        // 场景：船上有两层箱子 TIER01(下) 和 TIER02(上)
        vesselMock.setOccupied("BAY01-01-01", "BAY01-01-02");

        // 场景：堆场是空的，可以随便落箱

        // 1. 尝试先卸底层箱子 (TIER01) -> 应该失败，因为 TIER02 还在上面
        createWi("WI_DSCH_FAIL", BizTypeEnum.DSCH, "BAY01-01-01", "YARD01-A-1");

        BusinessException ex = assertThrows(BusinessException.class, () ->
                taskDecisionService.evaluateAndDecide(createReq("WI_DSCH_FAIL"))
        );
        System.out.println("成功拦截违规卸船: " + ex.getMessage());

        // 2. 正确卸顶层箱子 (TIER02)
        createWi("WI_DSCH_TOP", BizTypeEnum.DSCH, "BAY01-01-02", "YARD01-A-1");
        assertDoesNotThrow(() -> taskDecisionService.evaluateAndDecide(createReq("WI_DSCH_TOP")));

        // 模拟执行完成
        vesselMock.confirmDischarge("BAY01-01-02");
        yardMock.confirmPut("YARD01-A-1"); // 堆场底层有了箱子

        // 3. 现在可以卸底层箱子 (TIER01) 了
        // 目标堆场位置：YARD01-A-2 (叠在刚才卸下来的箱子上面)
        createWi("WI_DSCH_BOT", BizTypeEnum.DSCH, "BAY01-01-01", "YARD01-A-2");
        assertDoesNotThrow(() -> taskDecisionService.evaluateAndDecide(createReq("WI_DSCH_BOT")));
    }

    // ==========================================
    // 2. 装船流程验证 (Load)
    // 规则：必须先装下面的箱子 (Bottom -> Top)
    // ==========================================
    @Test
    @DisplayName("验证全算法：装船顺序约束 (Bottom-Up)")
    void testLoadSequence() {
        // 场景：船是空的
        // 堆场有箱子可供装船
        yardMock.setOccupied("YARD01-A-1");

        // 1. 尝试直接装第二层 (TIER02) -> 应该失败，因为 TIER01 悬空
        createWi("WI_LOAD_FAIL", BizTypeEnum.LOAD, "YARD01-A-1", "BAY01-01-02");
        BusinessException ex = assertThrows(BusinessException.class, () ->
                taskDecisionService.evaluateAndDecide(createReq("WI_LOAD_FAIL"))
        );
        System.out.println("成功拦截违规装船: " + ex.getMessage());

        // 2. 正确装底层 (TIER01)
        createWi("WI_LOAD_BOT", BizTypeEnum.LOAD, "YARD01-A-1", "BAY01-01-01");
        assertDoesNotThrow(() -> taskDecisionService.evaluateAndDecide(createReq("WI_LOAD_BOT")));

        vesselMock.confirmStowage("BAY01-01-01");

        // 3. 现在可以装第二层 (TIER02)
        createWi("WI_LOAD_TOP", BizTypeEnum.LOAD, "YARD01-A-1", "BAY01-01-02");
        assertDoesNotThrow(() -> taskDecisionService.evaluateAndDecide(createReq("WI_LOAD_TOP")));
    }

    // --- 辅助方法 ---

    private void createWi(String ref, BizTypeEnum type, String from, String to) {
        WorkInstruction wi = new WorkInstruction();
        wi.setWiRefNo(ref);
        wi.setMoveKind(type);
        wi.setFromPos(from);
        wi.setToPos(to);
        // 填充假设备避免基础校验报错
        wi.setFetchCheId("QC01"); wi.setPutCheId("QC01"); wi.setCarryCheId("QC01");
        GlobalContext.getInstance().getWorkInstructionMap().put(ref, wi);
    }

    private AssignTaskReq createReq(String wiRef) {
        AssignTaskReq req = new AssignTaskReq();
        req.setWiRefNo(wiRef);
        req.setDeviceId("QC01");
        req.setDeviceType(DeviceTypeEnum.QC);
        return req;
    }
}
