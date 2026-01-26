package model.dto.request;

import lombok.Data;

@Data
public class AssignTaskReq {
    private String truckId;   // 给哪辆车
    private String wiRefNo;   // 绑定什么任务
    private String eventId;   // 触发本次指派的上游事件ID
}