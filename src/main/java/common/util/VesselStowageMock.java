package common.util;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 船图结构与堆叠顺序约束验证工具
 * 假设位置格式为: BAYxx-ROWxx-TIERxx
 */
public class VesselStowageMock {

    // 记录船上已被占用的位置
    private static final Set<String> occupiedPositions = ConcurrentHashMap.newKeySet();
    private static final Pattern POS_PATTERN = Pattern.compile("BAY(\\d+)-(\\d+)-(\\d+)");

    private static final VesselStowageMock INSTANCE = new VesselStowageMock();

    public static VesselStowageMock getInstance() {
        return INSTANCE;
    }

    private VesselStowageMock() {}

    /**
     * 校验装船可行性
     * 算法：解析目标位置 Tier。如果 Tier > 1 (不是底层)，则 Tier-1 的位置必须被占用。
     */
    public boolean isLoadAllowed(String targetPos) {
        String lowerPos = calculateLowerTierPosition(targetPos);

        // 如果没有下层位置（说明是底层，或者格式无法解析），则默认允许装船
        if (lowerPos == null) {
            return true;
        }

        // 检查下层是否有箱子
        if (!occupiedPositions.contains(lowerPos)) {
            System.out.printf(">>> [LOAD校验失败] 目标 %s 悬空！需先装下方 %s%n", targetPos, lowerPos);
            return false;
        }
        return true;
    }

    /**
     * 校验卸船 可行性
     * 算法：解析目标位置 Tier。计算出 Tier+1 的位置，检查是否被占用。
     */
    public boolean isDischargeAllowed(String targetPos) {
        // 基础检查：位置本身得有箱子
        if (!occupiedPositions.contains(targetPos) && !occupiedPositions.isEmpty()) {
        }

        String upperPos = calculateUpperTierPosition(targetPos);

        // 如果算不出上层位置（格式错误），放行
        if (upperPos == null) {
            return true;
        }

        // 检查上层是否有箱子
        if (occupiedPositions.contains(upperPos)) {
            System.out.printf(">>> [DSCH校验失败] 目标 %s 被压住！需先卸上方 %s%n", targetPos, upperPos);
            return false;
        }
        return true;
    }

    /**
     * 内部算法：计算同一列的下一层位置
     */
    private String calculateLowerTierPosition(String pos) {
        Matcher m = POS_PATTERN.matcher(pos);
        if (m.find()) {
            String bay = m.group(1);
            String row = m.group(2);
            int tier = Integer.parseInt(m.group(3));

            // 假设 Tier 从 1 或 01 开始。如果 Tier <= 1，则没有下层
            if (tier <= 1) {
                return null;
            }

            // 格式化 Tier 为两位数字 (例如 1 -> 01)
            String lowerTierStr = String.format("%02d", tier - 1);
            return pos.substring(0, pos.lastIndexOf("-") + 1) + lowerTierStr;
        }
        return null;
    }

    /**
     * 内部算法：计算同一列的上一层位置
     */
    private String calculateUpperTierPosition(String pos) {
        Matcher m = POS_PATTERN.matcher(pos);
        if (m.find()) {
            int tier = Integer.parseInt(m.group(3));
            String upperTierStr = String.format("%02d", tier + 1);
            return pos.substring(0, pos.lastIndexOf("-") + 1) + upperTierStr;
        }
        return null;
    }

    public void confirmStowage(String pos) { occupiedPositions.add(pos); }
    public void confirmDischarge(String pos) { occupiedPositions.remove(pos); }
    public void reset() { occupiedPositions.clear(); }
    public void setOccupied(String... positions) {
        if (positions != null) occupiedPositions.addAll(Arrays.asList(positions));
    }
}