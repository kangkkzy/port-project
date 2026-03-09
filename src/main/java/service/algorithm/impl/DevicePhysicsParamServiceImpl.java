package service.algorithm.impl;

import common.exception.BusinessException;
import engine.context.GlobalContext;
import model.entity.DevicePhysicsParam;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import service.algorithm.DevicePhysicsParamService;

import java.util.Collection;
import java.util.Map;

/**
 * 设备物理参数服务实现
 *
 * 严格模式：所有参数必须在场景初始化时由外部系统注入到 GlobalContext 中。
 * 如果运行时找不到参数，说明外部系统漏传，引擎直接抛出异常。
 */
@Service
public class DevicePhysicsParamServiceImpl implements DevicePhysicsParamService {

    private static final Logger log = LoggerFactory.getLogger(DevicePhysicsParamServiceImpl.class);

    private final GlobalContext context = GlobalContext.getInstance();

    @Override
    public DevicePhysicsParam getPhysicsParam(String deviceId) {
        if (deviceId == null || deviceId.isEmpty()) {
            throw new BusinessException("设备ID不能为空");
        }

        // 纯粹的读取逻辑，不尝试调用外部接口
        DevicePhysicsParam param = context.getDevicePhysicsParamMap().get(deviceId);
        if (param == null) {
            throw new BusinessException(String.format(
                    "设备 [%s] 物理参数缺失，请确保在场景初始化时由外部算法下发了该数据", deviceId));
        }

        return param;
    }

    @Override
    public Map<String, DevicePhysicsParam> getPhysicsParams(Collection<String> deviceIds) {
        if (deviceIds == null || deviceIds.isEmpty()) {
            throw new BusinessException("设备ID列表不能为空");
        }

        // 逐个获取，任一缺失则抛异常
        Map<String, DevicePhysicsParam> result = new java.util.HashMap<>();
        for (String id : deviceIds) {
            result.put(id, getPhysicsParam(id));
        }
        return result;
    }

    @Override
    public void syncPhysicsParams(java.util.List<String> deviceIds) {
        // 此方法已废弃，不再支持运行时同步
        // 外部系统必须在场景初始化时通过 /sim/scenario/init 接口注入所有参数
        throw new BusinessException("syncPhysicsParams 已废弃，请通过 /sim/scenario/init 接口在场景初始化时注入所有物理参数");
    }

    @Override
    public void preloadAllParams(Collection<String> allDeviceIds) {
        // 此方法已废弃，不再支持运行时预加载
        // 外部系统必须在场景初始化时通过 /sim/scenario/init 接口注入所有参数
        throw new BusinessException("preloadAllParams 已废弃，请通过 /sim/scenario/init 接口在场景初始化时注入所有物理参数");
    }
}
