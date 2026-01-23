package model.bo;

import model.entity.*;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 全局上下文
 */
public class GlobalContext {

    private static volatile GlobalContext instance;

    // 仿真世界当前的绝对时间戳
    private long simTime = 0L;

    // 物理实体
    private final Map<String, Truck> truckMap = new ConcurrentHashMap<>();
    private final Map<String, QcDevice> qcMap = new ConcurrentHashMap<>();
    private final Map<String, AscDevice> ascMap = new ConcurrentHashMap<>();

    // 环境约束与基础设施
    private final Map<String, Fence> fenceMap = new ConcurrentHashMap<>(); // 栅栏池
    private final Map<String, YardBlock> yardBlockMap = new ConcurrentHashMap<>();

    // 业务数据
    private final Map<String, WorkInstruction> workInstructionMap = new ConcurrentHashMap<>();
    private final Map<String, Container> containerMap = new ConcurrentHashMap<>();

    private GlobalContext() {}

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
     *  统一设备查找：依次在 集卡、岸桥、龙门吊 池中查找设备
     */
    public BaseDevice getDevice(String deviceId) {
        if (truckMap.containsKey(deviceId)) return truckMap.get(deviceId);
        if (qcMap.containsKey(deviceId)) return qcMap.get(deviceId);
        if (ascMap.containsKey(deviceId)) return ascMap.get(deviceId);
        return null;
    }

    /**
     * 清空所有数据
     */
    public void clearAll() {
        truckMap.clear();
        qcMap.clear();
        ascMap.clear();
        fenceMap.clear();
        yardBlockMap.clear();
        workInstructionMap.clear();
        containerMap.clear();
        simTime = 0L;
    }

    // 时间的 Getters & Setters
    public long getSimTime() { return simTime; }
    public void setSimTime(long simTime) { this.simTime = simTime; }

    // 其他 Getters
    public Map<String, Truck> getTruckMap() { return truckMap; }
    public Map<String, QcDevice> getQcMap() { return qcMap; }
    public Map<String, AscDevice> getAscMap() { return ascMap; }
    public Map<String, Fence> getFenceMap() { return fenceMap; }
    public Map<String, YardBlock> getYardBlockMap() { return yardBlockMap; }
    public Map<String, WorkInstruction> getWorkInstructionMap() { return workInstructionMap; }
    public Map<String, Container> getContainerMap() { return containerMap; }
}