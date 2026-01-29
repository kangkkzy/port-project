package common.util;

import common.consts.BizTypeEnum;
import common.consts.DeviceTypeEnum;
import model.entity.WorkInstruction;

/**
 * 业务类型工具类
 * 提供业务类型相关的验证和查询功能
 */
public class BizTypeUtil {

    /**
     * 根据业务类型判断是否需要抓箱设备
     *
     * @param bizType 业务类型
     * @return true 如果需要抓箱设备，false 如果不需要
     */
    public static boolean requiresFetchDevice(BizTypeEnum bizType) {
        if (bizType == null) {
            return false;
        }
        // RECV 和 DIRECT_IN 不需要抓箱（外集卡自带箱子）
        return bizType != BizTypeEnum.RECV && bizType != BizTypeEnum.DIRECT_IN;
    }

    /**
     * 根据业务类型判断是否需要放箱设备
     *
     * @param bizType 业务类型
     * @return true 如果需要放箱设备，false 如果不需要
     */
    public static boolean requiresPutDevice(BizTypeEnum bizType) {
        if (bizType == null) {
            return false;
        }
        // DLVR 和 DIRECT_OUT 不需要放箱（直接出港）
        return bizType != BizTypeEnum.DLVR && bizType != BizTypeEnum.DIRECT_OUT;
    }

    /**
     * 根据业务类型获取推荐的抓箱设备类型
     *
     * @param bizType 业务类型
     * @return 推荐的设备类型，如果不需要则返回null
     */
    public static DeviceTypeEnum getRecommendedFetchDeviceType(BizTypeEnum bizType) {
        if (bizType == null) {
            return null;
        }
        switch (bizType) {
            case DSCH:
            case DIRECT_OUT:
                return DeviceTypeEnum.QC;  // 岸桥抓箱
            case LOAD:
            case YARD_SHIFT:
            case DLVR:
                return DeviceTypeEnum.ASC;  // 龙门吊抓箱
            case RECV:
            case DIRECT_IN:
                return null;  // 不需要抓箱
            default:
                return null;
        }
    }

    /**
     * 根据业务类型获取推荐的放箱设备类型
     *
     * @param bizType 业务类型
     * @return 推荐的设备类型，如果不需要则返回null
     */
    public static DeviceTypeEnum getRecommendedPutDeviceType(BizTypeEnum bizType) {
        if (bizType == null) {
            return null;
        }
        switch (bizType) {
            case DSCH:
            case YARD_SHIFT:
            case RECV:
                return DeviceTypeEnum.ASC;  // 龙门吊放箱
            case LOAD:
            case DIRECT_IN:
                return DeviceTypeEnum.QC;  // 岸桥放箱
            case DLVR:
            case DIRECT_OUT:
                return null;  // 不需要放箱
            default:
                return null;
        }
    }

    /**
     * 根据业务类型判断是否需要运输设备（集卡）
     *
     * @param bizType 业务类型
     * @return true 如果需要运输设备，false 如果不需要
     */
    public static boolean requiresCarryDevice(BizTypeEnum bizType) {
        // 所有业务类型都需要运输设备（集卡）
        return bizType != null;
    }

    /**
     * 根据业务类型判断运输设备应该是内集卡还是外集卡
     *
     * @param bizType 业务类型
     * @return true 如果是外集卡，false 如果是内集卡
     */
    public static boolean requiresExternalTruck(BizTypeEnum bizType) {
        if (bizType == null) {
            return false;
        }
        // DLVR, RECV, DIRECT_IN, DIRECT_OUT 需要外集卡
        return bizType == BizTypeEnum.DLVR ||
                bizType == BizTypeEnum.RECV ||
                bizType == BizTypeEnum.DIRECT_IN ||
                bizType == BizTypeEnum.DIRECT_OUT;
    }

    /**
     * 验证作业指令的设备配置是否符合业务类型要求
     *
     * @param wi 作业指令
     * @return 验证结果，null表示验证通过，否则返回错误信息
     */
    public static String validateWorkInstructionDevices(WorkInstruction wi) {
        if (wi == null || wi.getMoveKind() == null) {
            return "作业指令或业务类型不能为空";
        }

        BizTypeEnum bizType = wi.getMoveKind();

        // 验证抓箱设备
        if (requiresFetchDevice(bizType)) {
            if (wi.getFetchCheId() == null || wi.getFetchCheId().trim().isEmpty()) {
                return String.format("业务类型 [%s] 需要抓箱设备，但 fetchCheId 为空", bizType.getDesc());
            }
        } else {
            // 不需要抓箱的业务，fetchCheId 应该为空
            if (wi.getFetchCheId() != null && !wi.getFetchCheId().trim().isEmpty()) {
                return String.format("业务类型 [%s] 不需要抓箱设备，但 fetchCheId 不为空: %s",
                        bizType.getDesc(), wi.getFetchCheId());
            }
        }

        // 验证放箱设备
        if (requiresPutDevice(bizType)) {
            if (wi.getPutCheId() == null || wi.getPutCheId().trim().isEmpty()) {
                return String.format("业务类型 [%s] 需要放箱设备，但 putCheId 为空", bizType.getDesc());
            }
        } else {
            // 不需要放箱的业务，putCheId 应该为空
            if (wi.getPutCheId() != null && !wi.getPutCheId().trim().isEmpty()) {
                return String.format("业务类型 [%s] 不需要放箱设备，但 putCheId 不为空: %s",
                        bizType.getDesc(), wi.getPutCheId());
            }
        }

        // 验证运输设备
        if (requiresCarryDevice(bizType)) {
            if (wi.getCarryCheId() == null || wi.getCarryCheId().trim().isEmpty()) {
                return String.format("业务类型 [%s] 需要运输设备，但 carryCheId 为空", bizType.getDesc());
            }
        }

        return null;  // 验证通过
    }

    /**
     * 检查设备ID是否匹配作业指令的业务类型要求
     *
     * @param deviceId 设备ID
     * @param wi 作业指令
     * @return true 如果设备匹配，false 如果不匹配
     */
    public static boolean isDeviceMatchingWorkInstruction(String deviceId, WorkInstruction wi) {
        if (deviceId == null || wi == null) {
            return false;
        }
        // 检查设备是否是指令中指定的设备之一
        return deviceId.equals(wi.getFetchCheId()) ||
                deviceId.equals(wi.getCarryCheId()) ||
                deviceId.equals(wi.getPutCheId());
    }

    /**
     * 获取业务类型的完整描述
     *
     * @param bizType 业务类型
     * @return 完整描述，包括代码和描述
     */
    public static String getFullDescription(BizTypeEnum bizType) {
        if (bizType == null) {
            return "未知业务类型";
        }
        return String.format("%s (%s)", bizType.getCode(), bizType.getDesc());
    }
}
