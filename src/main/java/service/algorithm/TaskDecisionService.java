package service.algorithm;

import model.dto.request.AssignTaskReq;
import model.dto.response.AssignTaskResp;

/**
 * 任务决策服务接口
 * 负责接收外部算法下发的调度指令，并推入物理引擎执行
 */
public interface TaskDecisionService {

    /**
     * 外部算法直接指派任务到引擎
     *
     * @param req 任务指派请求
     * @return 指派结果
     */
    AssignTaskResp assignTask(AssignTaskReq req);
}