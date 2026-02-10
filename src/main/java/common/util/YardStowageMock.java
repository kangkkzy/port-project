package common.util;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * [PDF Part 1] 堆场堆叠与作业约束验证工具
 * 对应 PDF 1.2.1 堆场布局：处理场桥(ASC)作业时的物理堆叠约束
 */
public class YardStowageMock {

    // 堆场当前有箱子的位置集合
    private static final Set<String> occupiedPositions = ConcurrentHashMap.newKeySet();

    // 堆场堆叠关系：Key=上层, Value=下层
    private static final Map<String, String> stackMap = new ConcurrentHashMap<>();
    // 反向堆叠关系：Key=下层, Value=上层
    private static final Map<String, String> reverseStackMap = new ConcurrentHashMap<>();

    static {
        // 初始化 YARD01 区块的堆叠关系
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
     * 校验进箱/落箱 (Put) 可行性
     * 规则：位置必须为空，且如果有下层位置，下层必须已有箱子
     */
    public boolean isPutAllowed(String targetPos) {
        // 1. 目标位置必须为空
        if (occupiedPositions.contains(targetPos)) {
            System.out.printf(">>> [Yard Put失败] 目标位置 %s 已有箱子%n", targetPos);
            return false;
        }

        // 2. 检查悬空约束
        String lower = stackMap.get(targetPos);
        if (lower != null && !occupiedPositions.contains(lower)) {
            System.out.printf(">>> [Yard Put失败] 目标位置 %s 悬空！下方位置 %s 无箱%n", targetPos, lower);
            return false;
        }
        return true;
    }

    /**
     * 校验提箱/取箱 (Fetch) 可行性
     * 规则：目标位置必须有箱，且上方不能有箱子
     */
    public boolean isFetchAllowed(String targetPos) {
        // 1. 检查上方是否压箱
        String upper = reverseStackMap.get(targetPos);
        if (upper != null && occupiedPositions.contains(upper)) {
            System.out.printf(">>> [Yard Fetch失败] 目标位置 %s 被压住！上方位置 %s 有箱%n", targetPos, upper);
            return false;
        }
        return true;
    }

    public void confirmPut(String pos) {
        occupiedPositions.add(pos);
    }

    public void confirmFetch(String pos) {
        occupiedPositions.remove(pos);
    }

    public void reset() {
        occupiedPositions.clear();
    }

    public void setOccupied(String... positions) {
        if (positions != null) {
            occupiedPositions.addAll(Arrays.asList(positions));
        }
    }
}