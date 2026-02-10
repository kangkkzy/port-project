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
import model.entity.Truck;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import service.algorithm.TaskDecisionService;
import service.algorithm.impl.SimulationStatisticsService;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 完整算法逻辑验证测试套件
 * 目标：验证 PDF 文档 Part 2 (堆叠工艺) 和 Part 4 (物理安全与调度协同)
 */
@SpringBootTest(classes = application.SecsApplication.class)
public class AlgorithmVerificationTest {

    @Autowired
    private TaskDecisionService taskDecisionService;

    @Autowired
    private SimulationStatisticsService statisticsService;

    // 引入工具类以便在测试中设置初始状态
    private final VesselStowageMock vesselMock = VesselStowageMock.getInstance();
    private final YardStowageMock yardMock = YardStowageMock.getInstance();
    private final GlobalContext context = GlobalContext.getInstance();

    @BeforeEach
    void setUp() {
        // 1. 重置所有 Mock 和 统计状态
        vesselMock.reset();
        yardMock.reset();
        statisticsService.reset();
        context.clearAll();

        // 2. 初始化物理环境
        // 设置两台相邻岸桥: QC01 @ BAY01 (X=20m), QC02 @ BAY03 (X=60m)
        setupQc("QC01", 20.0);
        setupQc("QC02", 60.0);

        // 设置集卡: TRUCK01 @ 100m (堆场附近)
        setupTruck("TRUCK01", 100.0);
    }

    @Test
    @DisplayName("全场景综合验证：堆叠逻辑 + 物理防碰撞 + 时间协同")
    void verifyFullAlgorithm() {
        System.out.println(">>> 开始运行全场景综合验证...");

        // ============================================================
        // 场景 A: 验证装船堆叠顺序 (Part 2)
        // ============================================================
        System.out.println("\n[Test A] 装船堆叠顺序验证 (Bottom-Up)");

        // 准备工作：堆场有箱子 (YARD01-A-1)
        yardMock.setOccupied("YARD01-A-1");

        // A1. 错误尝试：直接装 2 层 (悬空)
        createLoadWi("WI_LOAD_ERR", "YARD01-A-1", "BAY01-01-02", "TRUCK01");
        assertThrows(BusinessException.class, () -> runDecision("WI_LOAD_ERR", "QC01"));
        System.out.println("  -> Pass: 成功拦截悬空装船指令");

        // A2. 正确尝试：装 1 层
        createLoadWi("WI_LOAD_OK", "YARD01-A-1", "BAY01-01-01", "TRUCK01");
        assertDoesNotThrow(() -> runDecision("WI_LOAD_OK", "QC01"));
        System.out.println("  -> Pass: 底层装船指令执行成功");

        // 模拟物理执行完成
        vesselMock.confirmStowage("BAY01-01-01");


        // ============================================================
        // 场景 B: 验证岸桥防碰撞 (Part 4.5.2.1)
        // ============================================================
        System.out.println("\n[Test B] 岸桥防碰撞验证 (Safety Constraint)");

        // 当前状态: QC01(20m), QC02(60m)

        // B1. 冲突尝试：QC01 试图去 BAY04 (80m)，这需要穿越 QC02 (60m)
        createLoadWi("WI_CRASH", "YARD01", "BAY04-01-01", null);
        assertThrows(BusinessException.class, () -> runDecision("WI_CRASH", "QC01"));
        System.out.println("  -> Pass: 成功拦截穿越冲突指令");

        // B2. 安全尝试：QC01 去 BAY02 (40m)
        // 距离 QC02(60m) 还有 20m，大于安全距离 15m，应该允许
        createLoadWi("WI_SAFE", "YARD01", "BAY02-01-01", null);
        assertDoesNotThrow(() -> runDecision("WI_SAFE", "QC01"));
        System.out.println("  -> Pass: 安全距离内的移动指令执行成功");


        // ============================================================
        // 场景 C: 验证时间协同与目标函数 (Part 4.5.2.4)
        // ============================================================
        System.out.println("\n[Test C] 时间协同验证 (Time Sync)");

        // C1. 协同失败：集卡距离极远
        setupTruck("TRUCK_FAR", 20000.0); // 20km 外
        // QC02(60m) 需要 TRUCK_FAR 配合
        createLoadWi("WI_SYNC_FAIL", "YARD01", "BAY03-01-01", "TRUCK_FAR");

        assertThrows(BusinessException.class, () -> runDecision("WI_SYNC_FAIL", "QC02"));
        System.out.println("  -> Pass: 成功拦截协同超时指令 (集卡过远)");

        // C2. 协同成功：集卡就在附近
        // QC02(60m) 需要 TRUCK01(100m) 配合，距离 40m，很快能到
        createLoadWi("WI_SYNC_OK", "YARD01", "BAY03-01-01", "TRUCK01");
        assertDoesNotThrow(() -> runDecision("WI_SYNC_OK", "QC02"));
        System.out.println("  -> Pass: 高效协同指令执行成功");


        // ============================================================
        // 打印最终报告
        // ============================================================
        statisticsService.printReport();
    }

    // --- 辅助方法 ---

    private void runDecision(String wiRef, String qcId) {
        AssignTaskReq req = new AssignTaskReq();
        req.setWiRefNo(wiRef);
        req.setDeviceId(qcId);
        req.setDeviceType(DeviceTypeEnum.QC);
        taskDecisionService.evaluateAndDecide(req);
    }

    private void createLoadWi(String ref, String from, String to, String truckId) {
        WorkInstruction wi = new WorkInstruction();
        wi.setWiRefNo(ref);
        wi.setMoveKind(BizTypeEnum.LOAD);
        wi.setFromPos(from);
        wi.setToPos(to);
        wi.setPutCheId("QC01");
        if (truckId != null) {
            wi.setFetchCheId(truckId); // 绑定集卡
        }
        context.getWorkInstructionMap().put(ref, wi);
    }

    private void setupQc(String id, double x) {
        QcDevice qc = new QcDevice();
        qc.setId(id);
        qc.setType(DeviceTypeEnum.QC);
        qc.setPosX(x);
        context.getQcMap().put(id, qc);
    }

    private void setupTruck(String id, double x) {
        Truck t = new Truck();
        t.setId(id);
        t.setType(DeviceTypeEnum.ELECTRIC_TRUCK);
        t.setPosX(x);
        context.getTruckMap().put(id, t);
    }
}
