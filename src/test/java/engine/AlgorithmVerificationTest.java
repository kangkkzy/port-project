package engine;

import common.consts.BizTypeEnum;
import common.consts.DeviceTypeEnum;
import common.exception.BusinessException;
import common.util.VesselStowageMock;
import common.util.YardStowageMock;
import engine.context.GlobalContext;
import model.dto.request.AssignTaskReq;
import model.entity.AscDevice;
import model.entity.WorkInstruction;
import model.entity.QcDevice;
import model.entity.Truck;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import service.algorithm.MapDataService;
import service.algorithm.TaskDecisionService;
import engine.log.SimulationStatisticsService;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes = application.SecsApplication.class)
public class AlgorithmVerificationTest {

    @Autowired
    private TaskDecisionService taskDecisionService;

    @Autowired
    private SimulationStatisticsService statisticsService;

    @Autowired
    private MapDataService mapDataService;

    private final VesselStowageMock vesselMock = VesselStowageMock.getInstance();
    private final YardStowageMock yardMock = YardStowageMock.getInstance();
    private final GlobalContext context = GlobalContext.getInstance();

    @BeforeEach
    void setUp() {
        vesselMock.reset();
        yardMock.reset();
        statisticsService.reset();
        context.clearAll();

        // [关键] 必须提供完整的配置，任何缺失都会导致测试崩溃 (符合 Fail-Fast 要求)
        String jsonConfig = "{"
                + "\"coordinates\": {"
                + "  \"BAY01\": 20.0,"
                + "  \"BAY02\": 40.0,"
                + "  \"BAY03\": 60.0,"
                + "  \"BAY04\": 80.0,"
                + "  \"YARD01\": 100.0,"
                + "  \"GATE\": 500.0"
                + "},"
                + "\"parameters\": {"
                + "  \"minQcDistance\": 15.0,"
                + "  \"truckSpeed\": 5.0,"
                + "  \"qcSpeed\": 0.8,"
                + "  \"maxSyncWaitMs\": 900000.0"
                + "}"
                + "}";
        mapDataService.loadMapConfiguration(jsonConfig);

        // 初始化物理设备
        setupQc("QC01", 20.0);
        setupQc("QC02", 60.0);
        setupTruck("TRUCK01", 100.0);
        setupAsc("ASC01");
    }

    @Test
    @DisplayName("全场景综合验证 (Strict Config Mode)")
    void verifyFullAlgorithm() {
        System.out.println(">>> 启动验证...");

        // A. 堆叠逻辑
        yardMock.setOccupied("YARD01-A-1");
        createLoadWi("WI_LOAD_OK", "YARD01-A-1", "BAY01-01-01", "TRUCK01");
        assertDoesNotThrow(() -> runDecision("WI_LOAD_OK", "QC01"));
        vesselMock.confirmStowage("BAY01-01-01");

        // B. 防碰撞 (依赖 JSON 坐标)
        createLoadWi("WI_CRASH", "YARD01", "BAY04-01-01", "TRUCK01");
        assertThrows(BusinessException.class, () -> runDecision("WI_CRASH", "QC01"));

        // C. 协同 (依赖 JSON 参数)
        setupTruck("TRUCK_FAR", 20000.0);
        createLoadWi("WI_SYNC_FAIL", "YARD01", "BAY03-01-01", "TRUCK_FAR");
        assertThrows(BusinessException.class, () -> runDecision("WI_SYNC_FAIL", "QC02"));

        // D. [新增验证] 测试配置缺失报错
        // 构造一个未配置坐标的位置
        createLoadWi("WI_UNKNOWN_POS", "YARD01", "UNKNOWN_BAY-01-01", "TRUCK01");
        BusinessException ex = assertThrows(BusinessException.class, () -> runDecision("WI_UNKNOWN_POS", "QC01"));
        System.out.println("成功捕获配置缺失错误: " + ex.getMessage());
        assertTrue(ex.getMessage().contains("缺少位置坐标配置"));

        statisticsService.printReport();
    }

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
        wi.setCarryCheId(truckId);
        wi.setFetchCheId("ASC01");
        context.getWorkInstructionMap().put(ref, wi);
    }

    private void setupQc(String id, double x) {
        QcDevice qc = new QcDevice(); qc.setId(id); qc.setType(DeviceTypeEnum.QC); qc.setPosX(x);
        context.getQcMap().put(id, qc);
    }

    private void setupTruck(String id, double x) {
        Truck t = new Truck(); t.setId(id); t.setType(DeviceTypeEnum.ELECTRIC_TRUCK); t.setPosX(x);
        context.getTruckMap().put(id, t);
    }

    private void setupAsc(String id) {
        AscDevice asc = new AscDevice(); asc.setId(id); asc.setType(DeviceTypeEnum.ASC);
        context.getAscMap().put(id, asc);
    }
}