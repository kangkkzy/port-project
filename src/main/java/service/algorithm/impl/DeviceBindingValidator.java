package service.algorithm.impl;

import common.exception.BusinessException;
import common.util.BizTypeUtil;
import engine.context.GlobalContext;
import model.dto.request.AssignTaskReq;
import model.entity.WorkInstruction;
import org.springframework.stereotype.Component;
import service.algorithm.WorkInstructionValidator;

/**
 * 校验作业指令与设备绑定关系是否合法（抓箱设备/放箱设备等）。
 */
@Component
public class DeviceBindingValidator implements WorkInstructionValidator {

    @Override
    public void validate(AssignTaskReq req, GlobalContext context) {
        WorkInstruction wi = context.getWorkInstruction(req.getWiRefNo());
        if (wi == null || wi.getMoveKind() == null) {
            return;
        }
        String err = BizTypeUtil.validateWorkInstructionDevices(wi);
        if (err != null) {
            throw new BusinessException(err);
        }
    }
}