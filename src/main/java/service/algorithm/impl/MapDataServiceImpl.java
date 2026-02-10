package service.algorithm.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import common.exception.BusinessException;
import model.dto.config.MapConfigDto;
import org.springframework.stereotype.Service;
import service.algorithm.MapDataService;

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
}