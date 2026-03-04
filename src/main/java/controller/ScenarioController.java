package controller;

import common.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import service.scenario.ScenarioLoaderService;

/**
 * 场景加载接口：通过 JSON 文件驱动初始化，替代 SimTestController 中的硬编码 new Truck() 等逻辑。
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
}