package engine;

/**
 * 仿真引擎生命周期状态枚举
 *
 * IDLE      - 引擎已初始化，尚未有任何事件入队或从未启动
 * RUNNING   - 后台异步循环正在处理事件
 * PAUSED    - 手动暂停，暂不处理事件（仍可入队）
 * SUSPENDED - 因业务校验异常触发全局熔断，必须人工 reset 才能恢复
 */
public enum EngineState {
    IDLE,
    RUNNING,
    PAUSED,
    SUSPENDED
}