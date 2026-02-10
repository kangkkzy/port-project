package service.algorithm.impl;

import common.consts.BizTypeEnum;
import common.consts.DeviceTypeEnum;
import common.consts.ErrorCodes;
import common.exception.BusinessException;
import common.util.BizTypeUtil;
import common.util.SafetyValidator;
import common.util.VesselStowageMock;
import common.util.YardStowageMock;
import model.bo.GlobalContext;
import model.dto.request.AssignTaskReq;
import model.dto.response.AssignTaskResp;
import model.entity.BaseDevice;
import model.entity.WorkInstruction;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import service.algorithm.TaskDecisionService;

@Service
public class TaskDecisionServiceImpl implements TaskDecisionService {

    private final GlobalContext context = GlobalContext.getInstance();
    private final VesselStowageMock vesselMock = VesselStowageMock.getInstance();
    private final YardStowageMock yardMock = YardStowageMock.getInstance();

    @Autowired
    private SafetyValidator safetyValidator;

    @Autowired
    private SimulationStatisticsService statisticsService;

    @Override
    public AssignTaskResp evaluateAndDecide(AssignTaskReq req) {
        statisticsService.recordTaskAttempt();

        BaseDevice device = getDevice(req);
        if (device == null) throw new BusinessException(ErrorCodes.DEVICE_NOT_FOUND);

        WorkInstruction wi = context.getWorkInstructionMap().get(req.getWiRefNo());
        if (wi == null) throw new BusinessException("作业指令不存在");

        if (wi.getMoveKind() != null) {
            String err = BizTypeUtil.validateWorkInstructionDevices(wi);
            if (err != null) throw new BusinessException(err);
        }

        checkStowage(wi);
        checkSafetyAndSync(req, wi);

        AssignTaskResp resp = new AssignTaskResp();
        resp.setTruckId(req.getDeviceId());
        resp.setAssignedWiRefNo(req.getWiRefNo());
        resp.setNextAction("PROCEED_TASK");
        return resp;
    }

    private void checkStowage(WorkInstruction wi) {
        String from = wi.getFromPos();
        String to = wi.getToPos();
        BizTypeEnum type = wi.getMoveKind();

        if (type == BizTypeEnum.LOAD) {
            if (isYardPos(from) && !yardMock.isFetchAllowed(from))
                throw new BusinessException("堆场提箱受阻: " + from);
            if (isVesselPos(to) && !vesselMock.isLoadAllowed(to))
                throw new BusinessException("装船顺序错误: " + to);
        } else if (type == BizTypeEnum.DSCH) {
            if (isVesselPos(from) && !vesselMock.isDischargeAllowed(from))
                throw new BusinessException("卸船顺序错误: " + from);
            if (isYardPos(to) && !yardMock.isPutAllowed(to))
                throw new BusinessException("堆场落箱受阻: " + to);
        }
    }

    private void checkSafetyAndSync(AssignTaskReq req, WorkInstruction wi) {
        if (DeviceTypeEnum.QC.equals(req.getDeviceType())) {
            // 此时调用 safetyValidator 可能会抛出 "缺少位置坐标配置" 异常
            double targetX = safetyValidator.parsePositionToX(wi.getToPos());

            // 此时调用 checkQcInterference 可能会抛出 "缺少关键算法参数" 异常
            if (!safetyValidator.checkQcInterference(req.getDeviceId(), targetX)) {
                statisticsService.recordSafetyViolation();
                throw new BusinessException("岸桥防碰撞预警");
            }

            String truckId = findTruckId(wi);
            if (truckId != null) {
                // 同上，可能抛出参数缺失异常
                long wait = safetyValidator.checkTimeSync(truckId, req.getDeviceId(), wi.getToPos());
                if (wait == -1) {
                    statisticsService.recordSyncFailure();
                    throw new BusinessException("集卡协同超时");
                }
                statisticsService.recordWaitTime(wait);
            }
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

    private BaseDevice getDevice(AssignTaskReq req) {
        if (req.getDeviceType() == null) throw new BusinessException("设备类型为空");
        switch (req.getDeviceType()) {
            case ELECTRIC_TRUCK: case OIL_TRUCK: return context.getTruckMap().get(req.getDeviceId());
            case ASC: return context.getAscMap().get(req.getDeviceId());
            case QC: return context.getQcMap().get(req.getDeviceId());
            default: return context.getDevice(req.getDeviceId());
        }
    }

    private boolean isVesselPos(String s) { return s != null && s.startsWith("BAY"); }
    private boolean isYardPos(String s) { return s != null && s.startsWith("YARD"); }
}