package service.algorithm;

import java.util.Collection;

/**
 * 设备物理参数配置服务接口
 * 负责为仿真引擎提供去硬编码的物理属性如速度、耗电率、重载系数、安全阈值等。
 * 数据来源：DevicePhysicsParamService 从外部接口获取设备物理参数。
 * Fail-Fast (快速失败)。任何设备如果没有明确配置物理参数，抛出异常。
 */
public interface DevicePhysicsService {

    /**
     * 获取设备的水平大车移动速度 (米/秒)
     * 适用于集卡、龙门吊与桥吊的水平移动。
     * @param deviceId 设备ID (精确到单台设备的配置)
     * @return 速度值
     * @throws common.exception.BusinessException 若数据库或配置中心中查不到该设备的配置时抛出
     */
    double getHorizontalSpeed(String deviceId);

    /**
     * 获取大机的垂直起升/下降速度 (米/秒)
     * 仅适用于 QC (岸桥) 和 ASC (龙门吊) 的吊具动作。
     * @param deviceId 设备ID
     * @return 垂直速度值
     * @throws common.exception.BusinessException 若查不到该设备的垂直速度配置时抛出
     */
    double getVerticalHoistSpeed(String deviceId);

    /**
     * 获取电集卡的能耗率 (每米消耗的电量百分比)
     * 对应于空载状态下的基础能耗
     * @param deviceId 设备ID
     * @return 耗电率
     * @throws common.exception.BusinessException 若查不到能耗配置时抛出
     */
    double getPowerConsumeRate(String deviceId);

    /**
     * 获取电集卡的重载能耗系数
     * @param deviceId 设备ID
     * @return 重载系数
     * @throws common.exception.BusinessException 若查不到配置时抛出
     */
    double getLoadedConsumeCoefficient(String deviceId);

    /**
     * 获取设备的安全电量冗余阈值 (百分比)
     * @param deviceId 设备ID
     * @return 安全冗余阈值百分比
     * @throws common.exception.BusinessException 若查不到配置时抛出
     */
    double getSafePowerThreshold(String deviceId);

    /**
     * 物理推演：更新所有移动中设备的坐标
     * 实现曼哈顿正交移动（L型路径）和单轴移动（起重机）
     * 动态到达触发：当物理坐标真实触碰终点时，自动触发 REPORT_IDLE 事件
     * @param context 全局上下文
     * @param simulationEngine 仿真引擎实例（用于动态触发事件）
     * @param deltaTimeSec 距离上次推演经过的真实秒数（已乘以 timeScale）
     */
    void updateMovingDevices(engine.context.GlobalContext context, engine.SimulationEngine simulationEngine, double deltaTimeSec);
}

