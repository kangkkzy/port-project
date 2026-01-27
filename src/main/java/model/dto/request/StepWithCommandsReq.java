package model.dto.request;

import lombok.Data;

import java.util.List;

/**
 * 高层 step API 请求：
 * 在一个仿真时间步内批量下发命令并推进时间。
 */
@Data
public class StepWithCommandsReq {

    /**
     * 本次仿真要推进的时间（毫秒）
     */
    private long stepMS;

    /**
     * 集卡移动指令
     */
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
