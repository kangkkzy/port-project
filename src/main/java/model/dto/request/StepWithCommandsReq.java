package model.dto.request;

import lombok.Data;

import java.util.List;

/**
 * 下发本步控制指令后执行一次单事件推进（无时间窗、无按步长推进）
 */
@Data
public class StepWithCommandsReq {

    /** 集卡移动指令 */
    private List<MoveCommandReq> truckMoves;

    /**
     * 吊机移动指令
     */
    private List<CraneMoveReq> craneMoves;

    /**
     * 栅栏状态控制
     */
    private List<FenceControlReq> fenceControls;

    /**
     * 吊机抓/放箱作业
     */
    private List<CraneOperationReq> craneOps;

    /**
     * 集卡充电指令
     */
    private List<ChargeCommandReq> chargeCommands;
}
