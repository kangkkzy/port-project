package common.util;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 对应 PDF 1.2.1 堆场布局与操作约束
 * 模拟场桥(ASC)作业时的堆叠约束
 */
public class YardStowageMock {

    // 堆场箱位状态
    private static final Set<String> occupiedPositions = Collections.synchronizedSet(new HashSet<>());

    // 堆场堆叠关系：Key=上层, Value=下层
    private static final Map<String, String> stackMap = new ConcurrentHashMap<>();
    // 反向关系：Key=下层, Value=上层
    private static final Map<String, String> reverseStackMap = new ConcurrentHashMap<>();

    static {
        // 模拟 YARD01 区块的堆叠关系
        // YARD01-A-1 (底层) -> YARD01-A-2 (二层) -> YARD01-A-3 (顶层)
        defineStack("YARD01-A-2", "YARD01-A-1");
        defineStack("YARD01-A-3", "YARD01-A-2");
    }

    private static void defineStack(String upper, String lower) {
        stackMap.put(upper, lower);
        reverseStackMap.put(lower, upper);
    }

    private static final YardStowageMock INSTANCE = new YardStowageMock();

    public static YardStowageMock getInstance() {
        return INSTANCE;
    }

    private YardStowageMock() {}

    /**
     * 进箱/落箱校验 (Put): 只能放在空地上或已有箱子之上
     */
    public boolean isPutAllowed(String targetPos) {
        // 目标位置必须是空的
        if (occupiedPositions.contains(targetPos)) {
            System.out.printf(">>> [Yard Put失败] 位置 %s 已有箱子%n", targetPos);
            return false;
        }

        // 检查下方是否有箱子（如果有定义下层位置）
        String lower = stackMap.get(targetPos);
        if (lower != null && !occupiedPositions.contains(lower)) {
            System.out.printf(">>> [Yard Put失败] 位置 %s 悬空！下方 %s 无箱%n", targetPos, lower);
            return false;
        }
        return true;
    }

    /**
     * 提箱/取箱校验 (Fetch): 只能取最上面的箱子
     */
    public boolean isFetchAllowed(String targetPos) {
        // 检查上方是否有箱子
        String upper = reverseStackMap.get(targetPos);
        if (upper != null && occupiedPositions.contains(upper)) {
            System.out.printf(">>> [Yard Fetch失败] 位置 %s 被压住！上方 %s 有箱%n", targetPos, upper);
            return false;
        }
        return true;
    }

    public void confirmPut(String pos) { occupiedPositions.add(pos); }
    public void confirmFetch(String pos) { occupiedPositions.remove(pos); }

    public void reset() { occupiedPositions.clear(); }
    public void setOccupied(String... positions) { occupiedPositions.addAll(Arrays.asList(positions)); }
}