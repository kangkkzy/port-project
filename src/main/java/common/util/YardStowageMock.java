package common.util;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 堆场堆叠与作业约束验证工具
 * * 假设堆场位置格式: YARDxx-BLOCK-TIER (例如: YARD01-A-1, YARD01-A-2)
 * Tier 为最后一位数字
 */
public class YardStowageMock {

    private static final Set<String> occupiedPositions = ConcurrentHashMap.newKeySet();

    // 匹配以数字结尾的位置字符串
    private static final Pattern YARD_POS_PATTERN = Pattern.compile("(.+)-(\\d+)$");

    private static final YardStowageMock INSTANCE = new YardStowageMock();

    public static YardStowageMock getInstance() {
        return INSTANCE;
    }

    private YardStowageMock() {}

    /**
     * 校验进箱 (Put)
     */
    public boolean isPutAllowed(String targetPos) {
        if (occupiedPositions.contains(targetPos)) {
            System.out.printf(">>> [Yard Put失败] 目标 %s 已占用%n", targetPos);
            return false;
        }

        String lowerPos = getLowerTier(targetPos);
        // 如果有下层且下层无箱，则悬空
        if (lowerPos != null && !occupiedPositions.contains(lowerPos)) {
            System.out.printf(">>> [Yard Put失败] 目标 %s 悬空！下方 %s 无箱%n", targetPos, lowerPos);
            return false;
        }
        return true;
    }

    /**
     * 校验提箱 (Fetch)
     */
    public boolean isFetchAllowed(String targetPos) {
        String upperPos = getUpperTier(targetPos);
        // 如果有上层且上层有箱，则被压
        if (upperPos != null && occupiedPositions.contains(upperPos)) {
            System.out.printf(">>> [Yard Fetch失败] 目标 %s 被压住！上方 %s 有箱%n", targetPos, upperPos);
            return false;
        }
        return true;
    }

    private String getLowerTier(String pos) {
        Matcher m = YARD_POS_PATTERN.matcher(pos);
        if (m.find()) {
            String prefix = m.group(1);
            int tier = Integer.parseInt(m.group(2));
            if (tier > 1) {
                return prefix + "-" + (tier - 1);
            }
        }
        return null;
    }

    private String getUpperTier(String pos) {
        Matcher m = YARD_POS_PATTERN.matcher(pos);
        if (m.find()) {
            String prefix = m.group(1);
            int tier = Integer.parseInt(m.group(2));
            // 假设堆场堆高限制逻辑在外部或此处暂不限制最大高度
            return prefix + "-" + (tier + 1);
        }
        return null;
    }

    public void confirmPut(String pos) { occupiedPositions.add(pos); }
    public void confirmFetch(String pos) { occupiedPositions.remove(pos); }
    public void reset() { occupiedPositions.clear(); }
    public void setOccupied(String... positions) {
        if (positions != null) occupiedPositions.addAll(Arrays.asList(positions));
    }
}