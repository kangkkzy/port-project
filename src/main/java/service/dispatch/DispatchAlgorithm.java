package service.dispatch;

import common.Result;
import model.bo.GlobalContext;

/**
 * 调度算法接口
 * 外部算法只需要实现这个接口，就能接管系统的调度逻辑
 */
public interface DispatchAlgorithm {
    /**
     * 执行计算
     * @param context 全局上下文 (包含了所有的车、箱子、指令状态)
     * @return 执行结果 (可以是指令列表，也可以是简单的成功/失败消息)
     */
    Result calculate(GlobalContext context);
}
