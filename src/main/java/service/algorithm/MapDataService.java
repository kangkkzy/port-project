package service.algorithm;

import model.dto.config.MapPathDto;
import model.dto.config.TransferZoneDto;
import model.entity.Point;

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
     * 获取所有交接区域配置（用于前端显示和验证）
     * @return 交接区域列表
     */
    List<TransferZoneDto> getAllTransferZones();

    /**
     * 验证设备类型在指定坐标是否在有效路径上
     * @param deviceType 设备类型 (TRUCK, QC, ASC)
     * @param x X坐标
     * @param y Y坐标
     * @return 是否在有效路径上
     */
    boolean isPositionOnPath(String deviceType, double x, double y);

    /**
     * 获取两个位置之间的关键点列表（用于分段移动）
     * @param deviceType 设备类型
     * @param startX 起始X坐标
     * @param startY 起始Y坐标
     * @param endX 目标X坐标
     * @param endY 目标Y坐标
     * @return 关键点列表（包含起点和终点之间的所有中间节点坐标）
     */
    List<Double> getKeyPointsBetween(String deviceType, double startX, double startY, double endX, double endY);

    /**
     * 验证移动轨迹是否合法（所有点必须在路网上）
     * @param deviceType 设备类型
     * @param pathPoints 轨迹点列表
     * @return 验证结果
     */
    TrajectoryValidationResult validateTrajectory(String deviceType, List<Point> pathPoints);

    /**
     * 获取设备类型的合法作业跨度
     * 例如：QC 的合法作业跨度是 60 米（QC轨道Y=140 与集卡道路 Y=200 之间的偏移）
     * @param deviceType 设备类型 (QC, ASC)
     * @param truckY 集卡道路 Y 坐标（用于计算偏移）
     * @return 合法作业跨度（米）
     */
    double getOperationSpan(String deviceType, double truckY);

    /**
     * 根据路径类型获取路径列表
     * @param pathType 路径类型 (TRUCK_ROAD, QC_RAIL, ASC_RAIL)
     * @return 路径列表
     */
    List<MapPathDto> getPathsByType(String pathType);

    /**
     * 获取 QC 轨道（单条）
     * @return QC 轨道配置
     */
    MapPathDto getQcRail();

    /**
     * 获取所有 ASC 轨道
     * @return ASC 轨道列表
     */
    List<MapPathDto> getAscRails();

    /**
     * 获取指定 X 坐标最近的 ASC 轨道
     * @param x X 坐标
     * @return 最近的 ASC 轨道
     */
    MapPathDto getAscRailAtPosition(double x);

    /**
     * 判断点是否在任何交接区域内
     * @param zoneType 区域类型 (QC, ASC, null表示所有)
     * @param x X 坐标
     * @param y Y 坐标
     * @return 是否在交接区域内
     */
    boolean isPointInTransferZone(String zoneType, double x, double y);

    /**
     * 获取 QC 对应的交接区域
     * @param qcX QC 的 X 坐标
     * @return 交接区域配置
     */
    TransferZoneDto getTransferZoneForQc(double qcX);

    /**
     * 获取 ASC 对应的交接区域
     * @param ascX ASC 的 X 坐标
     * @return 交接区域配置
     */
    TransferZoneDto getTransferZoneForAsc(double ascX);

    /**
     * 校验交接区域是否有效（设备双方都在同一交接区内）
     * @param zoneType 区域类型 (QC, ASC)
     * @param craneX 起重机 X 坐标
     * @param craneY 起重机 Y 坐标
     * @param truckX 集卡 X 坐标
     * @param truckY 集卡 Y 坐标
     * @return 交接是否合法
     */
    boolean isTransferZoneValid(String zoneType, double craneX, double craneY, double truckX, double truckY);

}