package service.algorithm;

import model.dto.config.MapPathDto;

import java.util.List;

/**
 * 地图数据服务接口
 * 负责管理码头静态数据结构和物理坐标
 */
public interface MapDataService {

    /**
     * 从 JSON 字符串加载地图配置
     */
    void loadMapConfiguration(String jsonConfig);

    /**
     * 获取指定位置的 X 轴坐标
     * @param positionId 位置ID (如 BAY01, YARD01)
     * @return 坐标值(米)
     * @throws common.exception.BusinessException 如果坐标未配置
     */
    double getPositionX(String positionId);

    /**
     * 获取配置参数
     * @param key 参数名
     * @return 参数值
     * @throws common.exception.BusinessException 如果参数未配置
     */
    double getParameter(String key);

    /**
     * 获取所有路径配置信息（用于前端验证和显示）
     * @return 路径配置列表
     */
    List<MapPathDto> getAllPaths();

    /**
     * 验证设备类型在指定坐标是否在有效路径上
     * @param deviceType 设备类型 (TRUCK, QC, ASC)
     * @param x X坐标
     * @param y Y坐标
     * @return 是否在有效路径上
     */
    boolean isPositionOnPath(String deviceType, double x, double y);
}