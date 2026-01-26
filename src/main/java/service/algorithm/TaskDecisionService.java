package service.algorithm;

import model.dto.request.AssignTaskReq;
import model.dto.response.AssignTaskResp;

/**
 * 任务决策服务接口
 * 负责评估任务耗电、路径、时间等 并作出最终的调度决策
 */
public interface TaskDecisionService {

    /**
     * 评估任务并作出决策 (执行业务 或 重定向去充电)
     * @param req 任务指派请求
     * @return 决策响应
     */
    AssignTaskResp evaluateAndDecide(AssignTaskReq req);
}