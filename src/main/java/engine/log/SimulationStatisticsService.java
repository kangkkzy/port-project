package engine.log;

import org.springframework.stereotype.Service;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class SimulationStatisticsService {

    private final AtomicLong totalTasks = new AtomicLong(0);
    private final AtomicLong safetyViolations = new AtomicLong(0);
    private final AtomicLong syncFailures = new AtomicLong(0);
    private final AtomicLong totalWaitTimeMs = new AtomicLong(0);

    public void recordTaskAttempt() { totalTasks.incrementAndGet(); }
    public void recordSafetyViolation() { safetyViolations.incrementAndGet(); }
    public void recordSyncFailure() { syncFailures.incrementAndGet(); }
    public void recordWaitTime(long ms) { totalWaitTimeMs.addAndGet(ms); }

    public void printReport() {
        long total = totalTasks.get();
        long fails = safetyViolations.get() + syncFailures.get();
        long success = total - fails;
        double rate = total > 0 ? (success * 100.0 / total) : 0.0;

        System.out.println("\n=== 算法验证 KPI 报告 ===");
        System.out.println("总请求: " + total);
        System.out.println("成功数: " + success);
        System.out.println("通过率: " + String.format("%.2f%%", rate));
        System.out.println("物理拦截: " + safetyViolations.get());
        System.out.println("协同失败: " + syncFailures.get());
        System.out.println("预计等待: " + (totalWaitTimeMs.get() / 1000.0) + "s");
        System.out.println("=========================\n");
    }

    public void reset() {
        totalTasks.set(0);
        safetyViolations.set(0);
        syncFailures.set(0);
        totalWaitTimeMs.set(0);
    }
}