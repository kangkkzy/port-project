package service.algorithm.impl;

import common.consts.DeviceTypeEnum;
import common.exception.BusinessException;
import common.util.SafetyValidator;
import engine.context.GlobalContext;
import engine.log.SimulationStatisticsService;
import engine.websocket.SimulationEventWebSocketService;
import model.dto.request.AssignTaskReq;
import model.entity.WorkInstruction;
import org.springframework.stereotype.Component;
import service.algorithm.WorkInstructionValidator;

/**
 * 安全与时间协同校验：
 * - 岸桥防碰撞
 * - 集卡与岸桥时间协同
 */
@Component
public class SafetyAndSyncValidator implements WorkInstructionValidator {

    private final SafetyValidator safetyValidator;
    private final SimulationStatisticsService statisticsService;
    private final GlobalContext context = GlobalContext.getInstance();
    private final SimulationEventWebSocketService webSocketService;

    public SafetyAndSyncValidator(SafetyValidator safetyValidator,
                                  SimulationStatisticsService statisticsService,
                                  SimulationEventWebSocketService webSocketService) {
        this.safetyValidator = safetyValidator;
        this.statisticsService = statisticsService;
        this.webSocketService = webSocketService;
    }

    @Override
    public void validate(AssignTaskReq req, GlobalContext context) {
        if (req == null || req.getDeviceType() == null) {
            return;
        }
        WorkInstruction wi = context.getWorkInstructionMap().get(req.getWiRefNo());
        if (wi == null) {
            return;
        }
        // 仅对岸桥设备生效
        if (!DeviceTypeEnum.QC.equals(req.getDeviceType())) {
            return;
        }
        checkSafetyAndSync(req, wi);
    }

    private void checkSafetyAndSync(AssignTaskReq req, WorkInstruction wi) {
        // 此时调用 safetyValidator 可能会抛出 "缺少位置坐标配置" 异常
        double targetX = safetyValidator.parsePositionToX(wi.getToPos());

        // 此时调用 checkQcInterference 可能会抛出 "缺少关键算法参数" 异常
        if (!safetyValidator.checkQcInterference(req.getDeviceId(), targetX)) {
            statisticsService.recordSafetyViolation();
            // 推送错误事件到前端
            webSocketService.broadcastError(req.getDeviceId(), "岸桥防碰撞预警", context.getSimTime());
            throw new BusinessException("岸桥防碰撞预警");
        }

        String truckId = findTruckId(wi);
        if (truckId != null) {
            // 同上，可能抛出参数缺失异常
            long wait = safetyValidator.checkTimeSync(truckId, req.getDeviceId(), wi.getToPos());
            if (wait == -1) {
                statisticsService.recordSyncFailure();
                // 推送错误事件到前端
                webSocketService.broadcastError(req.getDeviceId(), "集卡协同超时", context.getSimTime());
                throw new BusinessException("集卡协同超时");
            }
            statisticsService.recordWaitTime(wait);
        }
    }

    private String findTruckId(WorkInstruction wi) {
        if (isValidTruck(wi.getFetchCheId())) return wi.getFetchCheId();
        if (isValidTruck(wi.getCarryCheId())) return wi.getCarryCheId();
        if (isValidTruck(wi.getPutCheId())) return wi.getPutCheId();
        return null;
    }

    private boolean isValidTruck(String id) {
        return id != null && context.getTruckMap().containsKey(id);
    }
}


