package controller;

import common.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import service.scenario.ScenarioLoaderService;

/**
 * 场景加载接口：通过 JSON 文件驱动初始化，替代 SimTestController 中的硬编码 new Truck() 等逻辑。
 * 支持从本地文件加载或从外部系统接收 JSON payload 加载。
 */
@RestController
@RequestMapping("/sim/scenario")
@RequiredArgsConstructor
public class ScenarioController {

    private final ScenarioLoaderService scenarioLoaderService;

    /**
     * 加载指定场景 JSON 文件，清理并重新填充 GlobalContext 中的设备、箱子、作业指令。
     * 文件从 classpath:resources/scenarios/ 下读取。
     *
     * @param fileName 文件名，如 scenario-demo.json
     */
    @PostMapping("/load")
    public Result load(@RequestParam("fileName") String fileName) {
        ScenarioLoaderService.LoadResult result = scenarioLoaderService.load(fileName);
        return Result.success("场景已加载", result);
    }

    /**
     * 从外部系统接收 JSON payload 加载场景。
     * 用于外部调度中心通过 REST API 下发完整场景配置。
     *
     * @param jsonPayload 场景配置的 JSON 字符串（完整 scenario 格式）
     */
    @PostMapping("/init")
    public Result initFromExternal(@RequestBody String jsonPayload) {
        ScenarioLoaderService.LoadResult result = scenarioLoaderService.loadFromJson(jsonPayload);
        return Result.success("外部场景已初始化", result);
    }
}