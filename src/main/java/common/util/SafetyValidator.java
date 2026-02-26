package common.util;

import engine.context.GlobalContext;
import model.entity.BaseDevice;
import model.entity.QcDevice;
import model.entity.Truck;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import service.algorithm.MapDataService;

import java.util.*;
import java.util.stream.Collectors;

/**
 *物理安全与协同约束校验器
 * 完全依赖外部配置
 */
@Component
public class SafetyValidator {

    @Autowired
    private MapDataService mapDataService;

    /**
     * 坐标解析
     */
    public double parsePositionToX(String pos) {
        // 直接代理给 MapDataService，如果没配置会抛出 BusinessException
        return mapDataService.getPositionX(pos);
    }

    /**
     * 岸桥防碰撞校验
     */
    public boolean checkQcInterference(String targetQcId, double targetPosX) {
        // 必须配置 minQcDistance，否则报错
        double minDistance = mapDataService.getParameter("minQcDistance");

        Map<String, QcDevice> qcMap = GlobalContext.getInstance().getQcMap();
        if (qcMap.isEmpty()) return true;

        List<QcDevice> sortedQcs = qcMap.values().stream()
                .sorted(Comparator.comparing(BaseDevice::getId))
                .collect(Collectors.toList());

        int myIndex = -1;
        for (int i = 0; i < sortedQcs.size(); i++) {
            if (sortedQcs.get(i).getId().equals(targetQcId)) {
                myIndex = i; break;
            }
        }

        if (myIndex == -1) return true;

        // 检查左邻居
        if (myIndex > 0) {
            QcDevice left = sortedQcs.get(myIndex - 1);
            if (targetPosX <= left.getPosX() + minDistance) {
                System.out.printf(">>> [防碰撞] %s(%.1f) 逼近左侧 %s(%.1f) (安全距:%.1f)%n",
                        targetQcId, targetPosX, left.getId(), left.getPosX(), minDistance);
                return false;
            }
        }

        // 检查右邻居
        if (myIndex < sortedQcs.size() - 1) {
            QcDevice right = sortedQcs.get(myIndex + 1);
            if (targetPosX >= right.getPosX() - minDistance) {
                System.out.printf(">>> [防碰撞] %s(%.1f) 逼近右侧 %s(%.1f) (安全距:%.1f)%n",
                        targetQcId, targetPosX, right.getId(), right.getPosX(), minDistance);
                return false;
            }
        }
        return true;
    }

    /**
     *时间协同计算
     */
    public long checkTimeSync(String truckId, String qcId, String targetPos) {
        double truckSpeed = mapDataService.getParameter("truckSpeed");
        double qcSpeed = mapDataService.getParameter("qcSpeed");
        double maxWait = mapDataService.getParameter("maxSyncWaitMs");

        Truck truck = GlobalContext.getInstance().getTruckMap().get(truckId);
        QcDevice qc = GlobalContext.getInstance().getQcMap().get(qcId);

        if (truck == null || qc == null) return 0L;

        double targetX = parsePositionToX(targetPos);

        // 计算集卡时间
        double truckDist = Math.abs(targetX - truck.getPosX());
        long truckTime = (long) ((truckDist / truckSpeed) * 1000);

        // 计算岸桥时间
        double qcDist = Math.abs(targetX - qc.getPosX());
        long qcTime = (long) ((qcDist / qcSpeed) * 1000);

        long diff = Math.abs(truckTime - qcTime);

        if (diff > maxWait) {
            System.out.printf(">>> [协同超时] %s 与 %s 时间差 %.1f 分钟 (阈值:%.1f)%n",
                    truckId, qcId, diff / 60000.0, maxWait / 60000.0);
            return -1L;
        }
        return diff;
    }
}