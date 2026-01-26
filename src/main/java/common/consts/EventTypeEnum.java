package common.consts;

/**
 * 离散事件仿真中的所有事件类型
 */
public enum EventTypeEnum {
    //  移动相关事件
    MOVE_START,    // 开始向下一个路径点移动 (准备计算)
    ARRIVAL,       // 瞬间到达某个路径点

    //   环境事件
    FENCE_CONTROL, // 外部算法控制栅栏
    FENCE_OPEN,    // 栅栏开启
    FENCE_CLOSE,   // 栅栏锁死
    RESOURCE_UPDATE,// 资源更改事件 (更新泊位/集装箱/设备信息)

    //  充电事件
    NEED_CHARGE,   // 集卡电量不足事件
    HANDOVER_WI,   // 集卡移交指令
    GO_CHARGING,   // 集卡前往充电桩
    CHARGING_START,// 集卡开始充电
    CHARGE_FULL,   // 集卡电量充足
    REPORT_IDLE,   // 空闲集卡上报

    //  作业协同事件
    REACH_FETCH_POS,// 抓箱设备到达抓箱位置
    FETCH_DONE,     // 完成抓箱操作 -> 触发集卡运输
    REACH_PUT_POS,  // 放箱设备/集卡到达放箱位置
    PUT_DONE,       // 放箱操作完成
    WI_COMPLETE     // 作业指令完成 -> 触发工单结束分配新工单
}