package service.algorithm;

/**
 * 设备物理参数配置服务接口
 * 负责为仿真引擎提供去硬编码的物理属性如速度、耗电率、重载系数、安全阈值等。
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
}