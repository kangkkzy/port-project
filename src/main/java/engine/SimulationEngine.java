package engine;

import lombok.extern.slf4j.Slf4j;
import model.bo.GlobalContext;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class SimulationEngine {

    private final GlobalContext context = GlobalContext.getInstance();
    private long currentSimTime = System.currentTimeMillis();

    //  每100毫秒跳动一次
    @Scheduled(fixedRate = 100)
    public void runTick() {
        long deltaMS = 100;
        currentSimTime += deltaMS;

        //  推动场上所有集卡移动
        // （如果集卡状态是 WAITING 由于前面栅栏锁死 它在 tick 里会自动跳过 直到栅栏打开）
        context.getTruckMap().values().forEach(truck -> truck.tick(deltaMS, currentSimTime));

        //   推动岸桥/龙门吊移动
        context.getQcMap().values().forEach(qc -> qc.tick(deltaMS, currentSimTime));
        context.getAscMap().values().forEach(asc -> asc.tick(deltaMS, currentSimTime));
    }
}