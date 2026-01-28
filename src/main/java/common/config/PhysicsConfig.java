package common.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 物理参数
 */
@Configuration
@ConfigurationProperties(prefix = "sim.physics")
@Data
public class PhysicsConfig {

    /**
     * 到达目标点的判定阈值 (米)
     */
    private double arrivalThreshold = 0.01;

    /**
     * 充电前设备与充电桩的对准距离阈值 (米)
     */
    private double chargeAlignThreshold = 1.0;

    /**
     * 单一时间戳下允许处理的最大事件数量（防止死循环）
     */
    private int maxEventsPerTimestamp = 10_000;
}