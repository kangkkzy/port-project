package model.entity;

import common.consts.BizTypeEnum;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 作业指令
 */
@Data
public class WorkInstruction {
    private String wiRefNo;       // 指令编号 (Key)
    private String queueName;     // 指令所属的队列
    private String containerId;   // 作业的箱号id
    private BizTypeEnum moveKind; // 指令类型 (DSCH, LOAD, YARD_SHIFT, DLVR, RECV, DIRECT_IN, DIRECT_OUT)

    // 抓 -> 运 -> 放
    private String fetchCheId;    // 抓箱设备
    private LocalDateTime fetchTime;

    private String carryCheId;    // 运输集卡id
    private LocalDateTime carryTime;

    private String putCheId;      // 放箱设备
    private LocalDateTime putTime;

    //位置与状态
    private String fromPos;       // 起点
    private String toPos;         // 终点/当前流转位置

    //  将 status 统一映射到 wiStatus
    private String wiStatus;      // 指令作业状态 (如: ACTIVE, COMPLETE)
    private String jobStep;       // 指令具体状态

    //  时间
    private LocalDateTime dispatchTime; // 下达时间
    private LocalDateTime doneTime;     // 结束时间

    //  详细位置
    private String blockCode;     // 箱区代码
    private String bay;           // 贝位
    private String row;           // 排
    private String tier;          // 层

    //  调度
    private String dispatchCheId; // 最终派发的设备ID
}