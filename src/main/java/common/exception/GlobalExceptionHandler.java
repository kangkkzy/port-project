package common.exception;

import common.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import service.algorithm.impl.SimulationErrorLog;

/**
 * 全局异常处理器
 * 捕获所有异常，记录日志，并返回给外部算法处理
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    private final SimulationErrorLog errorLog;

    public GlobalExceptionHandler(SimulationErrorLog errorLog) {
        this.errorLog = errorLog;
    }

    /**
     * 处理业务异常
     */
    @ExceptionHandler(BusinessException.class)
    public Result handleBusinessException(BusinessException e) {
        log.warn("业务异常: {}", e.getMessage());
        // 记录到错误日志
        errorLog.recordEventProcessingError(null, null,
                model.bo.GlobalContext.getInstance().getSimTime(),
                "业务异常: " + e.getMessage(), e);
        return Result.error(e.getMessage());
    }

    /**
     * 处理仿真死循环异常
     */
    @ExceptionHandler(SimulationDeadLoopException.class)
    public Result handleDeadLoopException(SimulationDeadLoopException e) {
        log.error("仿真死循环异常: {}", e.getMessage());
        // 错误日志在SimulationEngine中记录
        return Result.error(500, "仿真死循环: " + e.getMessage());
    }

    /**
     * 处理所有其他异常
     */
    @ExceptionHandler(Exception.class)
    public Result handleException(Exception e) {
        log.error("系统异常", e);
        // 记录到错误日志
        errorLog.recordEventProcessingError(null, null,
                model.bo.GlobalContext.getInstance().getSimTime(),
                "系统异常: " + e.getClass().getSimpleName(), e);
        return Result.error("系统内部错误: " + e.getMessage());
    }
}
