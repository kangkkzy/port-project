package service.algorithm.impl;

import common.consts.ErrorCodes;
import common.exception.BusinessException;
import engine.log.SimulationStatisticsService;
import engine.context.GlobalContext;
import model.dto.request.AssignTaskReq;
import model.dto.response.AssignTaskResp;
import model.entity.BaseDevice;
import model.entity.WorkInstruction;
import org.springframework.stereotype.Service;
import service.algorithm.TaskDecisionService;
import service.algorithm.WorkInstructionValidator;

import java.util.List;

@Service
public class TaskDecisionServiceImpl implements TaskDecisionService {

    private final GlobalContext context = GlobalContext.getInstance();
    private final List<WorkInstructionValidator> validators;
    private final SimulationStatisticsService statisticsService;

    public TaskDecisionServiceImpl(List<WorkInstructionValidator> validators,
                                   SimulationStatisticsService statisticsService) {
        this.validators = validators;
        this.statisticsService = statisticsService;
    }

    @Override
    public AssignTaskResp evaluateAndDecide(AssignTaskReq req) {
        statisticsService.recordTaskAttempt();

        BaseDevice device = getDevice(req);
        if (device == null) throw new BusinessException(ErrorCodes.DEVICE_NOT_FOUND);

        WorkInstruction wi = context.getWorkInstructionMap().get(req.getWiRefNo());
        if (wi == null) throw new BusinessException("作业指令不存在");

        // 校验器责任链：设备/作业合法性等在各自实现中完成
        for (WorkInstructionValidator validator : validators) {
            validator.validate(wi, req);
        }

        AssignTaskResp resp = new AssignTaskResp();
        resp.setTruckId(req.getDeviceId());
        resp.setAssignedWiRefNo(req.getWiRefNo());
        resp.setNextAction("PROCEED_TASK");
        return resp;
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
}