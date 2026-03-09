package common.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 物理参数 - 系统级配置
 * 注意：业务物理参数（如 tolerance, threshold 等）必须通过外部配置获取
 */
@Configuration
@ConfigurationProperties(prefix = "sim.physics")
@Data
public class PhysicsConfig {

    /**
     * 单一时间戳下允许处理的最大事件数量（防止死循环）
     */
    private int maxEventsPerTimestamp = 10_000;
}
