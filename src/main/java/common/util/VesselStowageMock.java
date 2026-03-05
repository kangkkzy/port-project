package common.util;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 船图结构与堆叠顺序约束验证工具（模拟）
 * <p>
 * 用于模拟船舶配载时的堆叠规则：集装箱装船时，下层必须先存在；卸船时，上层必须为空。
 * 位置格式约定为：BAYxx-ROWxx-TIERxx，例如 "BAY01-03-02" 表示 01 行 03 列 02 层。
 * 该类为单例，所有状态（已占用位置）全局共享。
 */
public class VesselStowageMock {

    // 记录当前船上已被占用的位置（即已经放置了箱子的位置）
    private static final Set<String> occupiedPositions = ConcurrentHashMap.newKeySet();

    // 用于解析位置字符串的正则表达式：BAY数字-数字-数字
    private static final Pattern POS_PATTERN = Pattern.compile("BAY(\\d+)-(\\d+)-(\\d+)");

    // 单例实例
    private static final VesselStowageMock INSTANCE = new VesselStowageMock();

    /**
     * 获取单例实例
     */
    public static VesselStowageMock getInstance() {
        return INSTANCE;
    }

    private VesselStowageMock() {} // 私有构造，防止外部实例化

    /**
     * 校验装船是否可行
     * <p>
     * 规则：如果目标位置不是底层（Tier > 1），则其正下方一层必须已经被占用（即已有箱子）。
     * 如果目标位置是底层（Tier = 1）或格式无法解析，则默认允许装船。
     *
     * @param targetPos 目标位置，格式如 "BAY01-03-02"
     * @return true 表示允许装船，false 表示不允许（下层缺失）
     */
    public boolean isLoadAllowed(String targetPos) {
        String lowerPos = calculateLowerTierPosition(targetPos);

        // 如果没有下层位置（说明是底层或者格式错误），则允许装船（底层可以直接放）
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
     * 校验卸船是否可行
     * <p>
     * 规则：目标位置必须存在箱子（已占用），且其正上方一层必须为空（无箱子）。
     * 如果上方位置不存在（比如已达到最高层）或格式错误，则视为允许卸船。
     *
     * @param targetPos 目标位置，格式如 "BAY01-03-02"
     * @return true 表示允许卸船，false 表示不允许（上层压着箱子）
     */
    public boolean isDischargeAllowed(String targetPos) {
        // 这里原本可能想检查目标位置本身是否有箱子，但代码未实现，保留原样并注释说明
        // if (!occupiedPositions.contains(targetPos) && !occupiedPositions.isEmpty()) { }

        String upperPos = calculateUpperTierPosition(targetPos);

        // 如果算不出上层位置（格式错误或超出范围），放行
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
     * 计算同一列的下层位置
     * <p>
     * 根据当前位置解析出 BAY、ROW、TIER，将 TIER 减 1 后重新格式化为两位数字，
     * 并组合成新的位置字符串。若 TIER ≤ 1，则认为没有下层，返回 null。
     *
     * @param pos 当前位置，如 "BAY01-03-02"
     * @return 下层位置字符串，如 "BAY01-03-01"；若无下层或格式错误返回 null
     */
    private String calculateLowerTierPosition(String pos) {
        Matcher m = POS_PATTERN.matcher(pos);
        if (m.find()) {
            String bay = m.group(1);
            String row = m.group(2);
            int tier = Integer.parseInt(m.group(3));

            // 假设 Tier 从 1 开始，如果 Tier <= 1 则没有下层
            if (tier <= 1) {
                return null;
            }

            // 格式化下层 Tier 为两位数字 (例如 1 -> "01")
            String lowerTierStr = String.format("%02d", tier - 1);
            // 保留前缀（BAYxx-ROWxx-），替换最后一部分为下层 tier
            return pos.substring(0, pos.lastIndexOf("-") + 1) + lowerTierStr;
        }
        return null; // 格式不匹配
    }

    /**
     * 计算同一列的上层位置
     * <p>
     * 将 TIER 加 1 后重新格式化为两位数字，组合成新位置字符串。
     * 不检查是否超出实际最大层数（由调用方处理）。
     *
     * @param pos 当前位置，如 "BAY01-03-02"
     * @return 上层位置字符串，如 "BAY01-03-03"；若格式错误返回 null
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

    /**
     * 确认装船完成，将目标位置标记为已占用
     * @param pos 已装船的位置
     */
    public void confirmStowage(String pos) {
        occupiedPositions.add(pos);
    }

    /**
     * 确认卸船完成，将目标位置从占用集合中移除
     * @param pos 已卸船的位置
     */
    public void confirmDischarge(String pos) {
        occupiedPositions.remove(pos);
    }

    /**
     * 重置所有占用状态（清空船上所有箱子），通常用于开始新场景测试
     */
    public void reset() {
        occupiedPositions.clear();
    }

    /**
     * 批量预设占用位置（用于测试环境初始化）
     * @param positions 可变参数，每个字符串代表一个已占用的位置
     */
    public void setOccupied(String... positions) {
        if (positions != null) {
            occupiedPositions.addAll(Arrays.asList(positions));
        }
    }
}