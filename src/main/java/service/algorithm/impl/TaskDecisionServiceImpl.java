package service.algorithm.impl;

import common.consts.DeviceTypeEnum;
import common.consts.ErrorCodes;
import common.exception.BusinessException;
import model.bo.GlobalContext;
import model.dto.request.AssignTaskReq;
import model.dto.response.AssignTaskResp;
import model.entity.BaseDevice;
import model.entity.WorkInstruction;
import org.springframework.stereotype.Service;
import service.algorithm.TaskDecisionService;

/**
 * 任务决策服务
 */
@Service
public class TaskDecisionServiceImpl implements TaskDecisionService {

    private final GlobalContext context = GlobalContext.getInstance();

    @Override
    public AssignTaskResp evaluateAndDecide(AssignTaskReq req) {
        BaseDevice device = null;

        //  根据具体类型查找设备
        // 油集卡和电集卡都存储在 truckMap 中
        if (DeviceTypeEnum.ELECTRIC_TRUCK.equals(req.getDeviceType()) || DeviceTypeEnum.OIL_TRUCK.equals(req.getDeviceType())) {
            device = context.getTruckMap().get(req.getDeviceId());
        } else if (DeviceTypeEnum.ASC.equals(req.getDeviceType())) {
            device = context.getAscMap().get(req.getDeviceId());
        } else if (DeviceTypeEnum.QC.equals(req.getDeviceType())) {
            device = context.getQcMap().get(req.getDeviceId());
        } else {
            //  报错
            if (req.getDeviceType() == null) throw new BusinessException("设备类型不能为空");
            device = context.getDevice(req.getDeviceId());
        }

        if (device == null) {
            throw new BusinessException(ErrorCodes.DEVICE_NOT_FOUND);
        }

        // 验证工单
        WorkInstruction wi = context.getWorkInstructionMap().get(req.getWiRefNo());
        if (wi == null) {
            throw new BusinessException("指定的作业指令 [" + req.getWiRefNo() + "] 不存在");
        }

        //  生成响应
        AssignTaskResp resp = new AssignTaskResp();
        resp.setTruckId(req.getDeviceId());
        resp.setAssignedWiRefNo(req.getWiRefNo());
        resp.setEstimatedCost(0.0); // 留空
        resp.setNextAction("PROCEED_TASK");

        return resp;
    }
}