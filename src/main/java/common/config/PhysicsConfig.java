package common.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 仿真物理与数值稳定性配置
 * 所有与“物理参数/数值容忍度”相关的常量，统一从这里集中管理，避免在代码各处硬编码。
 *
 * 这些参数依然有合理的缺省值，但可以通过 Spring 配置文件覆盖：
 *
 * sim.physics.arrival-threshold
 * sim.physics.charge-align-threshold
 * sim.physics.max-events-per-timestamp
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
    private double chargeAlignThreshold = 5.0;

    /**
     * 单一时间戳下允许处理的最大事件数量（防止死循环）
     */
    private int maxEventsPerTimestamp = 10_000;
}