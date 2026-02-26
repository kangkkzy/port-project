package service.algorithm.impl;

import common.exception.BusinessException;
import common.util.BizTypeUtil;
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
    public void validate(WorkInstruction wi, AssignTaskReq req) {
        if (wi == null || wi.getMoveKind() == null) {
            return;
        }
        String err = BizTypeUtil.validateWorkInstructionDevices(wi);
        if (err != null) {
            throw new BusinessException(err);
        }
    }
}