package service.algorithm.impl;

import org.springframework.stereotype.Service;
import java.util.concurrent.atomic.AtomicLong;

/**
 * [PDF Part 4] 目标函数统计服务
 * 负责记录仿真过程中的关键绩效指标 (KPI)
 * 对应目标：最小化关键事件未达成数量、最小化等待时间
 */
@Service
public class SimulationStatisticsService {

    // 总任务指派尝试次数
    private final AtomicLong totalTasks = new AtomicLong(0);

    // 安全/物理冲突次数 (硬约束违反)
    private final AtomicLong safetyViolations = new AtomicLong(0);

    // 协同严重失败次数 (时间软约束违反)
    private final AtomicLong syncFailures = new AtomicLong(0);

    // 累计协同等待时间 (毫秒) - 优化的目标值
    private final AtomicLong totalWaitTimeMs = new AtomicLong(0);

    /**
     * 记录一次任务指派尝试
     */
    public void recordTaskAttempt() {
        totalTasks.incrementAndGet();
    }

    /**
     * 记录一次物理安全冲突 (如防碰撞拦截)
     */
    public void recordSafetyViolation() {
        safetyViolations.incrementAndGet();
    }

    /**
     * 记录一次时间协同失败 (如集卡过远)
     */
    public void recordSyncFailure() {
        syncFailures.incrementAndGet();
    }

    /**
     * 记录预计的等待时间成本
     */
    public void recordWaitTime(long ms) {
        totalWaitTimeMs.addAndGet(ms);
    }

    /**
     * 打印完整的仿真 KPI 报告
     */
    public void printReport() {
        long total = totalTasks.get();
        long fails = safetyViolations.get() + syncFailures.get();
        long success = total - fails;
        double successRate = total > 0 ? (success * 100.0 / total) : 0.0;

        System.out.println("\n===========================================");
        System.out.println("       仿真算法验证报告 (KPI Report)       ");
        System.out.println("===========================================");
        System.out.println("| 总指派请求数      : " + total);
        System.out.println("| 成功指派数        : " + success);
        System.out.println("| 算法通过率        : " + String.format("%.2f%%", successRate));
        System.out.println("-------------------------------------------");
        System.out.println("| [硬约束] 物理冲突拦截 : " + safetyViolations.get());
        System.out.println("| [软约束] 协同失败拦截 : " + syncFailures.get());
        System.out.println("| [目标函数] 累计等待成本 : " + (totalWaitTimeMs.get() / 1000.0) + " 秒");
        System.out.println("===========================================\n");
    }

    /**
     * 重置统计数据 (用于测试初始化)
     */
    public void reset() {
        totalTasks.set(0);
        safetyViolations.set(0);
        syncFailures.set(0);
        totalWaitTimeMs.set(0);
    }
}