package service.algorithm;

import model.dto.request.AssignTaskReq;
import model.entity.WorkInstruction;

/**
 * 作业指令校验扩展点
 * 不同的物理/业务/安全规则可以各自实现该接口，并由 TaskDecisionService 以责任链方式依次调用。
 */
public interface WorkInstructionValidator {

    /**
     * 对 WI 与当前任务请求进行校验，不通过时直接抛出 BusinessException。
     */
    void validate(WorkInstruction wi, AssignTaskReq req);
}

