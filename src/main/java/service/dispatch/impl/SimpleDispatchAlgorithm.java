package service.dispatch.impl;

import common.Result;
import lombok.extern.slf4j.Slf4j;
import model.bo.GlobalContext;
import org.springframework.stereotype.Component;
import service.dispatch.DispatchAlgorithm;

/**
 * 空实现
 * 防止系统启动报错，同时作为外部算法的接入示例
 */
@Slf4j
@Component
public class SimpleDispatchAlgorithm implements DispatchAlgorithm {

    @Override
    public Result calculate(GlobalContext context) {
        log.info(">>> 外部调度算法被触发 <<<");
        log.info("当前环境: 集卡数={}, 指令数={}",
                context.getTruckMap().size(),
                context.getWorkInstructionMap().size());


        // 目前什么都不做，直接返回成功

        return Result.success("默认算法执行完毕 (无操作)");
    }
}
