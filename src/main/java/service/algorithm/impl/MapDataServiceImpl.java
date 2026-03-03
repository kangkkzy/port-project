package service.algorithm.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import common.exception.BusinessException;
import model.dto.config.MapConfigDto;
import model.dto.config.MapPathDto;
import model.entity.Point;
import org.springframework.stereotype.Service;
import service.algorithm.MapDataService;
import service.algorithm.TrajectoryValidationResult;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 地图数据服务实现类
 * 无配置即报错
 */
@Service
public class MapDataServiceImpl implements MapDataService {

    private final ObjectMapper objectMapper = new ObjectMapper();

    // 内存缓存
    private final Map<String, Double> coordinateCache = new ConcurrentHashMap<>();
    private final Map<String, Double> parameterCache = new ConcurrentHashMap<>();
    private final List<MapPathDto> pathList = new ArrayList<>();

    @Override
    public void loadMapConfiguration(String jsonConfig) {
        try {
            MapConfigDto config = objectMapper.readValue(jsonConfig, MapConfigDto.class);

            if (config.getCoordinates() != null) {
                coordinateCache.clear();
                coordinateCache.putAll(config.getCoordinates());
                System.out.println(">>> [MapData] 坐标配置已加载: " + config.getCoordinates().size() + " 条");
            }

            if (config.getParameters() != null) {
                parameterCache.clear();
                parameterCache.putAll(config.getParameters());
                System.out.println(">>> [MapData] 参数配置已加载: " + config.getParameters());
            }

            // 加载路径配置
            if (config.getPaths() != null) {
                pathList.clear();
                pathList.addAll(config.getPaths());
                System.out.println(">>> [MapData] 路径配置已加载: " + config.getPaths().size() + " 条");
            }

        } catch (Exception e) {
            throw new BusinessException("地图配置加载失败: " + e.getMessage());
        }
    }

    @Override
    public double getPositionX(String positionId) {
        if (positionId == null) throw new BusinessException("位置ID不能为空");

        // 精确查找
        if (coordinateCache.containsKey(positionId)) {
            return coordinateCache.get(positionId);
        }

        //  层级查找 (处理 BAY01-01-01 这种情况)
        if (positionId.contains("-")) {
            String parentId = positionId.split("-")[0];
            if (coordinateCache.containsKey(parentId)) {
                return coordinateCache.get(parentId);
            }
        }

        // 找不到配置直接报错
        throw new BusinessException("致命错误: 缺少位置坐标配置 [" + positionId + "]");
    }

    @Override
    public double getParameter(String key) {
        if (!parameterCache.containsKey(key)) {
            throw new BusinessException("致命错误: 缺少关键算法参数配置 [" + key + "]");
        }
        return parameterCache.get(key);
    }

    @Override
    public List<MapPathDto> getAllPaths() {
        return new ArrayList<>(pathList);
    }

    @Override
    public boolean isPositionOnPath(String deviceType, double x, double y) {
        // 设备类型映射
        String targetPathType;
        if ("QC".equalsIgnoreCase(deviceType) || "CRANE_QC".equalsIgnoreCase(deviceType)) {
            targetPathType = "QC_RAIL";
        } else if ("ASC".equalsIgnoreCase(deviceType) || "CRANE_ASC".equalsIgnoreCase(deviceType)) {
            targetPathType = "ASC_RAIL";
        } else if ("ELECTRIC_TRUCK".equalsIgnoreCase(deviceType) || "INTERNAL_TRUCK".equalsIgnoreCase(deviceType) || "TRUCK".equalsIgnoreCase(deviceType)) {
            targetPathType = "TRUCK_ROAD";
        } else {
            return false;
        }

        for (MapPathDto path : pathList) {
            if (!path.getPathType().equals(targetPathType)) {
                continue;
            }

            // 验证坐标是否在路径范围内
            if ("HORIZONTAL".equals(path.getDirection())) {
                // 水平路径：检查Y坐标是否在路径上，X坐标是否在起止范围内
                if (Math.abs(y - path.getPosition()) < 5 && x >= path.getStartPoint() && x <= path.getEndPoint()) {
                    return true;
                }
            } else if ("VERTICAL".equals(path.getDirection())) {
                // 垂直路径：检查X坐标是否在路径上，Y坐标是否在起止范围内
                if (Math.abs(x - path.getPosition()) < 5 && y >= path.getStartPoint() && y <= path.getEndPoint()) {
                    return true;
                }
            }
        }

        return false;
    }

    @Override
    public List<Double> getKeyPointsBetween(String deviceType, double startX, double startY, double endX, double endY) {
        // 设备类型映射
        String targetPathType;
        if ("QC".equalsIgnoreCase(deviceType) || "CRANE_QC".equalsIgnoreCase(deviceType)) {
            targetPathType = "QC_RAIL";
        } else if ("ASC".equalsIgnoreCase(deviceType) || "CRANE_ASC".equalsIgnoreCase(deviceType)) {
            targetPathType = "ASC_RAIL";
        } else if ("ELECTRIC_TRUCK".equalsIgnoreCase(deviceType) || "INTERNAL_TRUCK".equalsIgnoreCase(deviceType) || "TRUCK".equalsIgnoreCase(deviceType)) {
            targetPathType = "TRUCK_ROAD";
        } else {
            return new ArrayList<>();
        }

        // 找到起点和终点所在的路径
        MapPathDto startPath = null;
        MapPathDto endPath = null;

        for (MapPathDto path : pathList) {
            if (!path.getPathType().equals(targetPathType)) {
                continue;
            }

            // 检查起点是否在路径上
            if ("HORIZONTAL".equals(path.getDirection())) {
                if (Math.abs(startY - path.getPosition()) < 5 && startX >= path.getStartPoint() && startX <= path.getEndPoint()) {
                    startPath = path;
                }
                if (Math.abs(endY - path.getPosition()) < 5 && endX >= path.getStartPoint() && endX <= path.getEndPoint()) {
                    endPath = path;
                }
            } else if ("VERTICAL".equals(path.getDirection())) {
                if (Math.abs(startX - path.getPosition()) < 5 && startY >= path.getStartPoint() && startY <= path.getEndPoint()) {
                    startPath = path;
                }
                if (Math.abs(endX - path.getPosition()) < 5 && endY >= path.getStartPoint() && endY <= path.getEndPoint()) {
                    endPath = path;
                }
            }
        }

        // 如果起点和终点在同一条路径上，提取该路径的关键点
        if (startPath != null && startPath == endPath && startPath.getKeyPoints() != null) {
            List<Double> keyPoints = new ArrayList<>();
            List<Double> allKeyPoints = startPath.getKeyPoints();

            // 确定移动方向
            boolean movingForward;
            boolean isHorizontal = "HORIZONTAL".equals(startPath.getDirection());
            if (isHorizontal) {
                movingForward = endX > startX;
            } else {
                movingForward = endY > startY;
            }

            // 筛选在起点和终点之间的关键点
            for (Double keyPoint : allKeyPoints) {
                if (isHorizontal) {
                    if (movingForward) {
                        if (keyPoint > startX && keyPoint <= endX) {
                            keyPoints.add(keyPoint);
                        }
                    } else {
                        if (keyPoint < startX && keyPoint >= endX) {
                            keyPoints.add(keyPoint);
                        }
                    }
                } else {
                    if (movingForward) {
                        if (keyPoint > startY && keyPoint <= endY) {
                            keyPoints.add(keyPoint);
                        }
                    } else {
                        if (keyPoint < startY && keyPoint >= endY) {
                            keyPoints.add(keyPoint);
                        }
                    }
                }
            }

            return keyPoints;
        }

        return new ArrayList<>();
    }

    /**
     * 验证移动轨迹是否合法（所有点必须在路网上）
     */
    @Override
    public TrajectoryValidationResult validateTrajectory(String deviceType, List<Point> pathPoints) {
        if (pathPoints == null || pathPoints.isEmpty()) {
            return TrajectoryValidationResult.failure("轨迹点列表为空", null);
        }

        List<Point> invalidPoints = new ArrayList<>();
        List<Point> validSegments = new ArrayList<>();

        for (Point point : pathPoints) {
            if (!isPositionOnPath(deviceType, point.getX(), point.getY())) {
                invalidPoints.add(point);
            } else {
                validSegments.add(point);
            }
        }

        if (!invalidPoints.isEmpty()) {
            return TrajectoryValidationResult.failure(
                    "轨迹点脱离路网: " + invalidPoints.size() + " 个点不在合法路径上",
                    invalidPoints
            );
        }

        return TrajectoryValidationResult.success(validSegments);
    }

    /**
     * 获取设备类型的合法作业跨度
     * QC: 轨道位置 140，集卡道路 200，跨度 60 米
     * ASC: 轨道位置 varies，集卡道路 200，跨度 varies
     */
    @Override
    public double getOperationSpan(String deviceType, double truckY) {
        if ("QC".equalsIgnoreCase(deviceType) || "CRANE_QC".equalsIgnoreCase(deviceType)) {
            return Math.abs(140.0 - truckY);
        } else if ("ASC".equalsIgnoreCase(deviceType) || "CRANE_ASC".equalsIgnoreCase(deviceType)) {
            return 200.0;
        }
        return 0.0;
    }

}