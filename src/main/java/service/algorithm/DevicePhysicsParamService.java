package service.algorithm;

import model.entity.DevicePhysicsParam;

import java.util.List;
import java.util.Map;

/**
 * 设备物理参数外部接口服务
 * 负责从外部系统获取设备的物理参数配置
 */
public interface DevicePhysicsParamService {

    /**
     * 根据设备ID获取物理参数
     * @param deviceId 设备ID
     * @return 设备物理参数
     */
    DevicePhysicsParam getPhysicsParam(String deviceId);

    /**
     * 批量获取设备物理参数
     * @param deviceIds 设备ID列表
     * @return 设备物理参数映射
     */
    Map<String, DevicePhysicsParam> getPhysicsParams(java.util.Collection<String> deviceIds);

    /**
     * 同步设备物理参数（从外部接口拉取并缓存）
     * @param deviceIds 要同步的设备ID列表
     */
    void syncPhysicsParams(List<String> deviceIds);

    /**
     * 预加载所有设备的物理参数（用于仿真初始化）
     * @param allDeviceIds 所有设备ID
     */
    void preloadAllParams(java.util.Collection<String> allDeviceIds);
}

