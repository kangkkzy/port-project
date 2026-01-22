package model.bo;

import model.entity.*;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 全局字段
 */
public class GlobalContext {

    private static volatile GlobalContext instance;

    // 资源

    // Map<集卡ID, Truck>
    private final Map<String, Truck> truckMap = new ConcurrentHashMap<>();

    // Map<桥吊ID, QcDevice>
    private final Map<String, QcDevice> qcMap = new ConcurrentHashMap<>();

    // Map<龙门吊ID, AscDevice>
    private final Map<String, AscDevice> ascMap = new ConcurrentHashMap<>();

    // 作业

    // Map<指令编号, WorkInstruction>
    private final Map<String, WorkInstruction> workInstructionMap = new ConcurrentHashMap<>();

    // Map<箱号, Container>
    private final Map<String, Container> containerMap = new ConcurrentHashMap<>();

    // 基础设施
    // Map<箱区号, YardBlock>
    private final Map<String, YardBlock> yardBlockMap = new ConcurrentHashMap<>();

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

    public Map<String, Truck> getTruckMap() { return truckMap; }
    public Map<String, QcDevice> getQcMap() { return qcMap; }
    public Map<String, AscDevice> getAscMap() { return ascMap; }
    public Map<String, WorkInstruction> getWorkInstructionMap() { return workInstructionMap; }
    public Map<String, Container> getContainerMap() { return containerMap; }
    public Map<String, YardBlock> getYardBlockMap() { return yardBlockMap; }
}
