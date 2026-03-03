package controller;

import common.Result;
import model.dto.config.MapPathDto;
import model.dto.config.TransferZoneDto;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import service.algorithm.MapDataService;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 地图配置查询接口
 */
@RestController
@RequestMapping("/sim/map")
public class MapConfigController {

    private final MapDataService mapDataService;

    public MapConfigController(MapDataService mapDataService) {
        this.mapDataService = mapDataService;
    }

    /**
     * 获取所有路径配置（用于前端验证和显示）
     */
    @GetMapping("/paths")
    public Result getAllPaths() {
        List<MapPathDto> paths = mapDataService.getAllPaths();
        return Result.success("查询成功", paths);
    }

    /**
     * 获取路径配置Map（便于前端快速查找）
     */
    @GetMapping("/paths/map")
    public Result getPathsMap() {
        List<MapPathDto> paths = mapDataService.getAllPaths();

        // 按路径类型分组
        Map<String, List<MapPathDto>> pathsByType = paths.stream()
                .collect(Collectors.groupingBy(MapPathDto::getPathType));

        return Result.success("查询成功", pathsByType);
    }

    /**
     * 获取所有交接区域配置（用于前端显示）
     */
    @GetMapping("/transfer-zones")
    public Result getTransferZones() {
        List<TransferZoneDto> zones = mapDataService.getAllTransferZones();
        return Result.success("查询成功", zones);
    }

    /**
     * 验证指定坐标是否在有效路径上
     */
    @GetMapping("/validate")
    public Result validatePosition(String deviceType, double x, double y) {
        boolean isValid = mapDataService.isPositionOnPath(deviceType, x, y);
        if (!isValid) {
            return Result.error(deviceType + " 设备不能移动到位置 (" + x + ", " + y + ")，该位置不在有效路径上");
        }
        return Result.success("验证通过", true);
    }
}
