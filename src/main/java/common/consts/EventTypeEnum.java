package common.consts;

/**
 * 离散事件仿真中的所有事件类型
 */
public enum EventTypeEnum {
    //  物理移动事件
    MOVE_START,    // 开始向下一个路径点移动
    ARRIVAL,       // 到达路径点

    //  外部指令事件 (API -> 内部事件)
    CMD_MOVE,         // 移动指令
    CMD_CRANE_MOVE,   // ASC/QC 移动指令
    CMD_CRANE_OP,     // ASC/QC 吊具操作
    CMD_FENCE_TOGGLE, // 栅栏控制
    CMD_CHARGE,       // 充电指令 (仅限电集卡

    //  任务指派交互
    CMD_ASSIGN_TASK,  //  系统下发任务
    CMD_TASK_ACK,     //  设备确认接收

    //  环境与资源事件
    FENCE_CONTROL, // 执行栅栏状态变更
    FENCE_OPEN,    // 栅栏开启后续处理
    RESOURCE_UPDATE, // 栅栏状态更新

    //  充电
    CHARGING_START,// 集卡开始充电
    CHARGE_FULL,   // 集卡充电完成
    REPORT_IDLE,   // 设备空闲上报 (仅上报状态

    //  作业协同事件
    REACH_FETCH_POS,// 抓箱设备到达抓箱位置
    FETCH_DONE,     // 完成抓箱操作
    REACH_PUT_POS,  // 放箱设备/集卡到达放箱位置
    PUT_DONE,       // 放箱操作完成
    WI_COMPLETE     // 作业指令完成
}
