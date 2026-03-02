package controller;

import common.Result;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 健康检查控制器 - 用于验证服务器是否正常
 */
@RestController
@RequestMapping("/health")
public class HealthController {

    /**
     * 健康检查端点
     */
    @GetMapping("/ping")
    public Result ping() {
        return Result.success("pong");
    }
}
