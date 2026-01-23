package model.dto.request;

import common.consts.EventTypeEnum;
import lombok.Data;

@Data
public class CraneOperationReq {
    private String craneId;       //  QC或ASC
    private EventTypeEnum action; //  抓箱完成或放箱完成
    private long durationMS;      // 该动作预计耗时
}