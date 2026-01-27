package model.bo;

import common.config.PhysicsConfig;
import lombok.Getter;
import lombok.Setter;
import model.entity.*;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 仿真世界的全局上下文
 */
@Getter
public class GlobalContext {

    // volatile保证多线程环境下的可见性与禁止指令重排序 确保单例的安全
    private static volatile GlobalContext instance;

    // 仿真世界的绝对时间戳 (单位：毫秒)
    @Setter
    private long simTime = 0L;

    //  物理实体
    // 存储当前港口内所有的集卡
    private final Map<String, Truck> truckMap = new ConcurrentHashMap<>();
    // 存储所有岸桥
    private final Map<String, QcDevice> qcMap = new ConcurrentHashMap<>();
    // 存储所有龙门吊
    private final Map<String, AscDevice> ascMap = new ConcurrentHashMap<>();
    // 存储靠泊在码头的船只
    private final Map<String, Vessel> vesselMap = new ConcurrentHashMap<>();

    //  环境约束与基础设施
    // 存储交通栅栏
    private final Map<String, Fence> fenceMap = new ConcurrentHashMap<>();
    // 存储堆场箱区配置
    private final Map<String, YardBlock> yardBlockMap = new ConcurrentHashMap<>();
    // 存储充电桩资源
    private final Map<String, ChargingStation> chargingStationMap = new ConcurrentHashMap<>();

    //  业务流转数据
    // 存储所有的作业指令
    private final Map<String, WorkInstruction> workInstructionMap = new ConcurrentHashMap<>();
    // 存储港口内所有的集装箱
    private final Map<String, Container> containerMap = new ConcurrentHashMap<>();

    //  物理与数值配置（由 Spring 容器注入后通过静态方法设置）
    @Setter
    private PhysicsConfig physicsConfig;

    // 私有化构造函数，防止外部直接 new 对象
    private GlobalContext() {}

    /**
     * 获取全局上下文单例 (双重检查锁实现)
     */
    public static GlobalContext getInstance() {
        if (instance == null) {
            synchronized (GlobalContext.class) {
                if (instance == null) {
                    instance = new GlobalContext();
                }
            }
        }
        return instance;
    }

    /**
     * 多态查询辅助方法：根据设备ID获取具体的设备基类
     */
    public BaseDevice getDevice(String deviceId) {
        if (truckMap.containsKey(deviceId)) return truckMap.get(deviceId);
        if (qcMap.containsKey(deviceId)) return qcMap.get(deviceId);
        if (ascMap.containsKey(deviceId)) return ascMap.get(deviceId);
        return null;
    }

    /**
     * 场景重置
     */
    @SuppressWarnings("unused")
    public void clearAll() {
        truckMap.clear();
        qcMap.clear();
        ascMap.clear();
        vesselMap.clear();
        fenceMap.clear();
        yardBlockMap.clear();
        chargingStationMap.clear();
        workInstructionMap.clear();
        containerMap.clear();
        simTime = 0L;
    }

}