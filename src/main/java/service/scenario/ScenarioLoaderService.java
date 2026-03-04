package service.scenario;

import com.fasterxml.jackson.databind.ObjectMapper;
import common.consts.BizTypeEnum;
import common.consts.DeviceStateEnum;
import common.consts.DeviceTypeEnum;
import engine.context.GlobalContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import model.dto.scenario.*;
import model.entity.*;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.ArrayList;

/**
 * 场景加载服务：解析 scenario JSON，清理并重新填充 GlobalContext 中的设备、箱子、指令。
 * 消除 SimTestController 中的硬编码初始化逻辑。
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ScenarioLoaderService {

    private final ObjectMapper objectMapper;

    /**
     * 从 classpath:resources/scenarios/ 下加载指定 JSON 文件，清理并填充全局上下文。
     *
     * @param fileName 文件名，如 scenario-demo.json（可带或不带路径，仅加载 scenarios 目录下）
     * @return 加载的设备与指令数量摘要
     */
    public LoadResult load(String fileName) {
        String safeName = fileName != null ? fileName.trim() : "";
        if (safeName.isEmpty()) {
            throw new IllegalArgumentException("场景文件名不能为空");
        }
        // 防止路径穿越，只允许 scenarios 目录下
        if (safeName.contains("..") || safeName.startsWith("/")) {
            throw new IllegalArgumentException("非法的场景文件名: " + fileName);
        }
        String path = "scenarios/" + (safeName.endsWith(".json") ? safeName : safeName + ".json");

        try (InputStream is = new ClassPathResource(path).getInputStream()) {
            ScenarioFileDto dto = objectMapper.readValue(is, ScenarioFileDto.class);
            return applyToContext(dto);
        } catch (Exception e) {
            log.error("加载场景文件失败: {}", path, e);
            throw new RuntimeException("加载场景失败: " + e.getMessage(), e);
        }
    }

    /**
     * 清理并填充 GlobalContext：设备 Map、集装箱 Map、作业指令 Map，并重置仿真时间。
     */
    private LoadResult applyToContext(ScenarioFileDto dto) {
        GlobalContext ctx = GlobalContext.getInstance();

        // 仅清理与场景相关的 Map，保留栅栏、堆场块、充电桩等基础设施
        ctx.getTruckMap().clear();
        ctx.getQcMap().clear();
        ctx.getAscMap().clear();
        ctx.getWorkInstructionMap().clear();
        ctx.getContainerMap().clear();
        ctx.setSimTime(0L);

        int trucks = 0, qcs = 0, ascs = 0, containers = 0, wis = 0;

        if (dto.getInitTrucks() != null) {
            for (ScenarioFileDto.InitTruckDto t : dto.getInitTrucks()) {
                Truck entity = toTruck(t);
                ctx.getTruckMap().put(entity.getId(), entity);
                trucks++;
            }
        }
        if (dto.getInitQcs() != null) {
            for (ScenarioFileDto.InitQcDto q : dto.getInitQcs()) {
                QcDevice entity = toQc(q);
                ctx.getQcMap().put(entity.getId(), entity);
                qcs++;
            }
        }
        if (dto.getInitAscs() != null) {
            for (ScenarioFileDto.InitAscDto a : dto.getInitAscs()) {
                AscDevice entity = toAsc(a);
                ctx.getAscMap().put(entity.getId(), entity);
                ascs++;
            }
        }
        if (dto.getInitContainers() != null) {
            for (ScenarioFileDto.InitContainerDto c : dto.getInitContainers()) {
                Container entity = toContainer(c);
                ctx.getContainerMap().put(entity.getContainerId(), entity);
                containers++;
            }
        }
        if (dto.getWorkInstructions() != null) {
            for (ScenarioFileDto.WorkInstructionEntryDto w : dto.getWorkInstructions()) {
                WorkInstruction entity = toWorkInstruction(w);
                ctx.getWorkInstructionMap().put(entity.getWiRefNo(), entity);
                wis++;
            }
        }

        log.info("场景已加载: trucks={}, qcs={}, ascs={}, containers={}, workInstructions={}",
                trucks, qcs, ascs, containers, wis);
        return new LoadResult(trucks, qcs, ascs, containers, wis);
    }

    private Truck toTruck(ScenarioFileDto.InitTruckDto dto) {
        Truck t = new Truck();
        t.setId(dto.getId());
        t.setType(parseDeviceType(dto.getType(), DeviceTypeEnum.ELECTRIC_TRUCK));
        t.setPosX(dto.getPosX() != null ? dto.getPosX() : 0.0);
        t.setPosY(dto.getPosY() != null ? dto.getPosY() : 0.0);
        t.setState(parseDeviceState(dto.getState()));
        t.setCurrWiRefNo(null);
        t.setRemainingMoveTargets(new ArrayList<>());
        return t;
    }

    private QcDevice toQc(ScenarioFileDto.InitQcDto dto) {
        QcDevice q = new QcDevice();
        q.setId(dto.getId());
        q.setType(DeviceTypeEnum.QC);
        q.setPosX(dto.getPosX() != null ? dto.getPosX() : 0.0);
        q.setPosY(dto.getPosY() != null ? dto.getPosY() : 0.0);
        q.setState(parseDeviceState(dto.getState()));
        q.setCurrWiRefNo(null);
        return q;
    }

    private AscDevice toAsc(ScenarioFileDto.InitAscDto dto) {
        AscDevice a = new AscDevice();
        a.setId(dto.getId());
        a.setType(DeviceTypeEnum.ASC);
        a.setPosX(dto.getPosX() != null ? dto.getPosX() : 0.0);
        a.setPosY(dto.getPosY() != null ? dto.getPosY() : 0.0);
        a.setState(parseDeviceState(dto.getState()));
        a.setCurrWiRefNo(null);
        return a;
    }

    private Container toContainer(ScenarioFileDto.InitContainerDto dto) {
        Container c = new Container();
        c.setContainerId(dto.getContainerId());
        c.setCurrentPos(dto.getCurrentPos());
        return c;
    }

    private WorkInstruction toWorkInstruction(ScenarioFileDto.WorkInstructionEntryDto dto) {
        WorkInstruction wi = new WorkInstruction();
        wi.setWiRefNo(dto.getWiRefNo());
        wi.setStatus(common.consts.WiStatusEnum.CREATED);
        wi.setMoveKind(parseBizType(dto.getMoveKind()));
        wi.setFetchCheId(dto.getFetchCheId());
        wi.setCarryCheId(dto.getCarryCheId());
        wi.setPutCheId(dto.getPutCheId());
        wi.setFromPos(dto.getFromPos());
        wi.setToPos(dto.getToPos());
        wi.setContainerId(dto.getContainerId());
        return wi;
    }

    private static DeviceStateEnum parseDeviceState(String state) {
        // 修复 isBlank 报错，兼容 Java 8
        if (state == null || state.trim().isEmpty()) return DeviceStateEnum.IDLE;
        try {
            return DeviceStateEnum.valueOf(state.trim());
        } catch (IllegalArgumentException ignored) {
            DeviceStateEnum byCode = DeviceStateEnum.getByCode(state.trim());
            return byCode != null ? byCode : DeviceStateEnum.IDLE;
        }
    }

    private static DeviceTypeEnum parseDeviceType(String type, DeviceTypeEnum defaultType) {
        // 修复 isBlank 报错，兼容 Java 8
        if (type == null || type.trim().isEmpty()) return defaultType;
        try {
            return DeviceTypeEnum.valueOf(type.trim());
        } catch (IllegalArgumentException e) {
            return defaultType;
        }
    }

    private static BizTypeEnum parseBizType(String moveKind) {
        // 修复 isBlank 报错，兼容 Java 8
        if (moveKind == null || moveKind.trim().isEmpty()) {
            throw new IllegalArgumentException("workInstructions[].moveKind 不能为空");
        }
        try {
            return BizTypeEnum.valueOf(moveKind.trim());
        } catch (IllegalArgumentException e) {
            for (BizTypeEnum v : BizTypeEnum.values()) {
                if (v.getCode().equalsIgnoreCase(moveKind.trim())) return v;
            }
            throw new IllegalArgumentException("未知的 moveKind: " + moveKind);
        }
    }

    /** 加载结果摘要 */
    @lombok.Data
    public static class LoadResult {
        private final int trucks;
        private final int qcs;
        private final int ascs;
        private final int containers;
        private final int workInstructions;
    }
}
