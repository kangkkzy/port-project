package model.dto.response;

import lombok.Data;

/**
 * 任务指派响应 DTO
 * 外部算法不仅下发任务 还可以根据当前电量判断是否需要重定向到充电任务
 */
@Data
public class AssignTaskResp {
    private String truckId;         // 集卡ID
    private String assignedWiRefNo; // 实际指派的指令 (可能是原业务指令 也可能是生成的充电指令)
    private Double estimatedCost;   // 预估消耗电量 (百分比)
    private String nextAction;      // 下一步任务 "PROCEED_TASK" (去作业) 或 "REDIRECT_TO_CHARGE" (去充电)
}