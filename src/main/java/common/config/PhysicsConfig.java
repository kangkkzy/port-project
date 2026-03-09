package common.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 物理参数 - 全局通用配置
 * 注意：设备级别的物理参数（如速度）应通过 DevicePhysicsParamService 从外部接口获取
 */
@Configuration
@ConfigurationProperties(prefix = "sim.physics")
@Data
public class PhysicsConfig {

    /**
     * 到达目标点的判定阈值 (米)
     */
    private double arrivalThreshold = 0.5;

    /**
     * 充电前设备与充电桩的对准距离阈值 (米)
     */
    private double chargeAlignThreshold = 1.0;

    /**
     * 单一时间戳下允许处理的最大事件数量（防止死循环）
     */
    private int maxEventsPerTimestamp = 10_000;

    /**
     * 交接区域容差 (米)
     * 用于判断设备是否到达交接区域
     */
    private double transferZoneTolerance = 5.0;
}