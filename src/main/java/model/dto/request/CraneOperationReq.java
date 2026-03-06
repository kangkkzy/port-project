package model.dto.request;

import common.consts.EventTypeEnum;
import lombok.Data;

@Data
public class CraneOperationReq {
    private String craneId;       // 执行作业的起重机 ID (QC/ASC)
    private String targetTruckId; // 协同作业的目标集卡 ID（抓/放箱时必填，用于距离校验）
    private EventTypeEnum action; // 作业动作: FETCH_DONE(抓箱完成) 或 PUT_DONE(放箱完成)
    private long durationMS;      // 该动作预计耗时（毫秒）
}