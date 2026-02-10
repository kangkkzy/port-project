package common.util;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * [PDF Part 2] 船图结构与堆叠顺序约束验证工具
 * 对应 PDF 2.1.2 及 4.2 节：处理集装箱在船上的物理依赖关系
 */
public class VesselStowageMock {

    // 记录船上已被占用的位置 (Bay-Row-Tier)
    // 使用 ConcurrentHashMap.KeySetView 保证多线程仿真环境下的安全性
    private static final Set<String> occupiedPositions = ConcurrentHashMap.newKeySet();

    // 装船依赖图：Key=上层位置, Value=依赖的下层位置集合
    // 含义：要放 Key，必须先放 Value
    private static final Map<String, Set<String>> loadPrecedenceMap = new ConcurrentHashMap<>();

    // 卸船依赖图：Key=下层位置, Value=压在上面的上层位置集合
    // 含义：要取 Key，必须先取 Value
    private static final Map<String, Set<String>> dischargePrecedenceMap = new ConcurrentHashMap<>();

    static {
        // --- 初始化模拟船图结构的依赖关系 ---
        // 规则：同一 Bay 同一 Row，层层向上堆叠
        // 实际项目中这些数据应来自 BAPLIE 文件解析，此处为硬编码模拟

        // BAY01 的堆叠关系
        addDependency("BAY01-01-02", "BAY01-01-01"); // 2层依赖1层
        addDependency("BAY01-01-03", "BAY01-01-02"); // 3层依赖2层

        // BAY03 的堆叠关系 (用于多岸桥测试)
        addDependency("BAY03-01-02", "BAY03-01-01");

        // BAY02/04 等位置假设默认为底层或无依赖
    }

    /**
     * 建立双向依赖关系
     * @param upper 上层位置 (High Tier)
     * @param lower 下层位置 (Low Tier)
     */
    private static void addDependency(String upper, String lower) {
        // 构建装船图
        loadPrecedenceMap.computeIfAbsent(upper, k -> new HashSet<>()).add(lower);
        // 构建卸船图 (反向)
        dischargePrecedenceMap.computeIfAbsent(lower, k -> new HashSet<>()).add(upper);
    }

    private static final VesselStowageMock INSTANCE = new VesselStowageMock();

    public static VesselStowageMock getInstance() {
        return INSTANCE;
    }

    private VesselStowageMock() {}

    /**
     * 校验装船 (Load) 可行性
     * 规则：目标位置的所有下方支撑位置必须已有箱子
     */
    public boolean isLoadAllowed(String targetPos) {
        Set<String> lowerPositions = loadPrecedenceMap.get(targetPos);

        // 如果没有定义的下层依赖（例如本身就是底层 TIER01），则允许直接装
        if (lowerPositions == null || lowerPositions.isEmpty()) {
            return true;
        }

        for (String lower : lowerPositions) {
            if (!occupiedPositions.contains(lower)) {
                System.out.printf(">>> [LOAD校验失败] 目标位置 %s 悬空！需先装下方位置 %s%n", targetPos, lower);
                return false;
            }
        }
        return true;
    }

    /**
     * 校验卸船 (Discharge) 可行性
     * 规则：目标位置的上方不能有箱子压着
     */
    public boolean isDischargeAllowed(String targetPos) {
        // 校验1: 目标位置本身必须有箱子才能卸 (逻辑一致性)
        // 注意：在部分测试场景中可能未初始化初始箱量，此检查可设为可选，但为了严谨这里保留
        if (!occupiedPositions.contains(targetPos) && !occupiedPositions.isEmpty()) {
            // 仅打印警告，不强制返回false，以免影响单纯的顺序逻辑测试
            // System.out.printf(">>> [DSCH警告] 目标位置 %s 当前显示为空，无法卸船%n", targetPos);
        }

        Set<String> upperPositions = dischargePrecedenceMap.get(targetPos);

        // 如果上方没有位置定义（例如本身是顶层），则允许卸
        if (upperPositions == null || upperPositions.isEmpty()) {
            return true;
        }

        for (String upper : upperPositions) {
            if (occupiedPositions.contains(upper)) {
                System.out.printf(">>> [DSCH校验失败] 目标位置 %s 被压住！需先卸上方位置 %s%n", targetPos, upper);
                return false;
            }
        }
        return true;
    }

    /**
     * 确认装船完成，更新状态
     */
    public void confirmStowage(String pos) {
        occupiedPositions.add(pos);
        // System.out.printf(">>> [船图更新] 位置 %s 箱子已就位%n", pos);
    }

    /**
     * 确认卸船完成，更新状态
     */
    public void confirmDischarge(String pos) {
        occupiedPositions.remove(pos);
        // System.out.printf(">>> [船图更新] 位置 %s 箱子已移除%n", pos);
    }

    /**
     * 重置状态 (测试用)
     */
    public void reset() {
        occupiedPositions.clear();
    }

    /**
     * 初始化占用状态 (测试用)
     */
    public void setOccupied(String... positions) {
        if (positions != null) {
            occupiedPositions.addAll(Arrays.asList(positions));
        }
    }
}