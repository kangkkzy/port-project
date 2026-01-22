package service.dispatch;

import common.Result;
import lombok.extern.slf4j.Slf4j;
import model.bo.GlobalContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * 调度服务
 * 负责连接 Controller 和 算法接口
 */
@Service
@Slf4j
public class DispatchService {

    @Autowired
    private DispatchAlgorithm dispatchAlgorithm;

    public Result runDispatch() {
        if (dispatchAlgorithm == null) {
            return Result.error("未找到调度算法实现！");
        }
        try {
            // 将单例 Context 传递给算法
            return dispatchAlgorithm.calculate(GlobalContext.getInstance());
        } catch (Exception e) {
            log.error("调度算法执行异常", e);
            return Result.error("算法错误: " + e.getMessage());
        }
    }
}