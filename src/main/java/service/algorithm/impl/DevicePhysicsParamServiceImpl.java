package service.algorithm.impl;

import common.exception.BusinessException;
import engine.context.GlobalContext;
import model.entity.DevicePhysicsParam;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import service.algorithm.DevicePhysicsParamService;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 设备物理参数服务实现
 * 从 GlobalContext 获取预加载的设备物理参数，或从外部接口获取
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

        DevicePhysicsParam param = context.getDevicePhysicsParamMap().get(deviceId);
        if (param != null) {
            return param;
        }

        param = fetchFromExternalApi(deviceId);

        if (param == null) {
            throw new BusinessException(String.format(
                    "设备 [%s] 物理参数未配置，请确保外部接口已返回该设备的数据", deviceId));
        }

        context.getDevicePhysicsParamMap().put(deviceId, param);
        return param;
    }

    @Override
    public Map<String, DevicePhysicsParam> getPhysicsParams(Collection<String> deviceIds) {
        Map<String, DevicePhysicsParam> result = new HashMap<>();
        if (deviceIds == null || deviceIds.isEmpty()) {
            return result;
        }

        for (String id : deviceIds) {
            result.put(id, getPhysicsParam(id));
        }

        return result;
    }

    @Override
    public void syncPhysicsParams(List<String> deviceIds) {
        if (deviceIds == null || deviceIds.isEmpty()) {
            return;
        }

        log.info("开始同步设备物理参数，数量: {}", deviceIds.size());

        Map<String, DevicePhysicsParam> params = fetchBatchFromExternalApi(deviceIds);

        List<String> missingDevices = deviceIds.stream()
                .filter(id -> !params.containsKey(id))
                .collect(Collectors.toList());

        if (!missingDevices.isEmpty()) {
            throw new BusinessException(String.format(
                    "以下设备物理参数未配置: %s，请确保外部接口已返回这些设备的数据",
                    String.join(", ", missingDevices)));
        }

        context.getDevicePhysicsParamMap().putAll(params);

        log.info("设备物理参数同步完成，共 {} 台设备", params.size());
    }

    @Override
    public void preloadAllParams(Collection<String> allDeviceIds) {
        if (allDeviceIds == null || allDeviceIds.isEmpty()) {
            return;
        }

        log.info("预加载所有设备物理参数，数量: {}", allDeviceIds.size());

        syncPhysicsParams(new ArrayList<>(allDeviceIds));
    }

    /**
     * 批量从外部接口获取设备物理参数。
     * TODO: 接入真实外部接口后，使用 deviceIds 请求并返回 Map。
     */
    @SuppressWarnings("unused")
    private Map<String, DevicePhysicsParam> fetchBatchFromExternalApi(List<String> deviceIds) {
        return new HashMap<>();
    }

    /**
     * 从外部接口获取设备物理参数。
     * TODO: 接入真实外部接口后，使用 deviceId 请求并返回 DevicePhysicsParam。
     */
    @SuppressWarnings("unused")
    private DevicePhysicsParam fetchFromExternalApi(String deviceId) {
        return null;
    }
}
