package service.algorithm.impl;

import common.consts.DeviceTypeEnum;
import engine.context.GlobalContext;
import model.entity.BaseDevice;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import service.algorithm.DevicePhysicsParamService;
import service.algorithm.DevicePhysicsService;

/**
 * 设备物理参数服务实现
 *
 * 纯离散版本：仅保留获取物理参数的辅助方法，
 * 连续 Tick 推演逻辑已废弃（详见 Phase 4 清理）。
 */
@Service
public class DevicePhysicsServiceImpl implements DevicePhysicsService {

    private static final Logger log = LoggerFactory.getLogger(DevicePhysicsServiceImpl.class);

    private final DevicePhysicsParamService devicePhysicsParamService;

    public DevicePhysicsServiceImpl(DevicePhysicsParamService devicePhysicsParamService) {
        this.devicePhysicsParamService = devicePhysicsParamService;
    }

    @Override
    public double getHorizontalSpeed(String deviceId) {
        return devicePhysicsParamService.getPhysicsParam(deviceId).getHorizontalSpeed();
    }

    @Override
    public double getVerticalHoistSpeed(String deviceId) {
        Double verticalSpeed = devicePhysicsParamService.getPhysicsParam(deviceId).getVerticalSpeed();
        return verticalSpeed != null ? verticalSpeed : 0.0;
    }

    @Override
    public double getPowerConsumeRate(String deviceId) {
        Double rate = devicePhysicsParamService.getPhysicsParam(deviceId).getPowerConsumeRate();
        return rate != null ? rate : 0.1;
    }

    @Override
    public double getLoadedConsumeCoefficient(String deviceId) {
        Double coefficient = devicePhysicsParamService.getPhysicsParam(deviceId).getLoadedConsumeCoefficient();
        return coefficient != null ? coefficient : 1.5;
    }

    @Override
    public double getSafePowerThreshold(String deviceId) {
        Double threshold = devicePhysicsParamService.getPhysicsParam(deviceId).getSafePowerThreshold();
        return threshold != null ? threshold : 20.0;
    }
}

