package common.consts;

/**
 * 离散事件仿真中的所有事件类型
 */
public enum EventTypeEnum {
    // 移动相关事件
    MOVE_START,    // 开始向下一个路径点移动 (准备触发计算)
    ARRIVAL,       // 瞬间到达某个路径点

    // 栅栏控制事件
    FENCE_OPEN,    // 栅栏开启 (状态变为 02)
    FENCE_CLOSE,   // 栅栏锁死 (状态变为 01)

    // 作业相关事件
    FETCH_DONE,    // 抓箱操作完成
    PUT_DONE       // 放箱操作完成
}