package common.util;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 对应 PDF 4.2.1 及 2.1.2 节
 * 模拟船图结构、装船(LOAD)和卸船(DSCH)的顺序约束
 */
public class VesselStowageMock {

    // 船图状态：记录哪些位置有箱子
    private static final Set<String> occupiedPositions = Collections.synchronizedSet(new HashSet<>());

    // 正向依赖：装船时，Key 依赖 Value (Key 在 Value 之上)
    // 例如: "BAY01-01-02" -> {"BAY01-01-01"} (装 TIER02 前必须有 TIER01)
    private static final Map<String, Set<String>> loadPrecedenceMap = new ConcurrentHashMap<>();

    // 反向依赖：卸船时，Key 被 Value 依赖 (Value 在 Key 之上)
    // 例如: "BAY01-01-01" -> {"BAY01-01-02"} (卸 TIER01 前必须卸掉 TIER02)
    private static final Map<String, Set<String>> dischargePrecedenceMap = new ConcurrentHashMap<>();

    static {
        // --- 初始化模拟依赖关系 ---
        // 规则：同一 Bay 同一 Row，层层向上堆叠

        // TIER02 依赖 TIER01
        addDependency("BAY01-01-02", "BAY01-01-01");
        // TIER03 依赖 TIER02
        addDependency("BAY01-01-03", "BAY01-01-02");
    }

    private static void addDependency(String upper, String lower) {
        // 装船约束: 上层依赖下层
        loadPrecedenceMap.computeIfAbsent(upper, k -> new HashSet<>()).add(lower);
        // 卸船约束: 下层被上层依赖
        dischargePrecedenceMap.computeIfAbsent(lower, k -> new HashSet<>()).add(upper);
    }

    private static final VesselStowageMock INSTANCE = new VesselStowageMock();

    public static VesselStowageMock getInstance() {
        return INSTANCE;
    }

    private VesselStowageMock() {}

    /**
     * 装船校验 (LOAD): 检查下面是否有箱子
     */
    public boolean isLoadAllowed(String targetPos) {
        // 如果该位置没有定义前序依赖（比如是底层），则允许
        Set<String> lowerPositions = loadPrecedenceMap.get(targetPos);
        if (lowerPositions == null || lowerPositions.isEmpty()) {
            return true;
        }

        for (String lower : lowerPositions) {
            if (!occupiedPositions.contains(lower)) {
                System.out.printf(">>> [LOAD校验失败] 位置 %s 悬空！下方位置 %s 尚未装船%n", targetPos, lower);
                return false;
            }
        }
        return true;
    }

    /**
     * 卸船校验 (DSCH): 检查上面是否还有箱子
     */
    public boolean isDischargeAllowed(String targetPos) {
        // 检查上方是否压着箱子
        Set<String> upperPositions = dischargePrecedenceMap.get(targetPos);
        if (upperPositions == null || upperPositions.isEmpty()) {
            return true;
        }

        for (String upper : upperPositions) {
            if (occupiedPositions.contains(upper)) {
                System.out.printf(">>> [DSCH校验失败] 位置 %s 被压住！上方位置 %s 尚未卸船%n", targetPos, upper);
                return false;
            }
        }
        return true;
    }

    // 状态更新方法
    public void confirmStowage(String pos) {
        occupiedPositions.add(pos);
        System.out.printf(">>> [船图更新] 位置 %s 已装载集装箱%n", pos);
    }

    public void confirmDischarge(String pos) {
        occupiedPositions.remove(pos);
        System.out.printf(">>> [船图更新] 位置 %s 已卸下集装箱%n", pos);
    }

    public void reset() {
        occupiedPositions.clear();
    }

    // 初始化船上已有箱子 (用于测试)
    public void setOccupied(String... positions) {
        occupiedPositions.addAll(Arrays.asList(positions));
    }
}