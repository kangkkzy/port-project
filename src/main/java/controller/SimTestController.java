package controller;

import common.Result;
import engine.context.GlobalContext;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import service.scenario.ScenarioLoaderService;

@RestController
@RequestMapping("/sim/test")
@RequiredArgsConstructor
public class SimTestController {

    private final ScenarioLoaderService scenarioLoaderService;

    @PostMapping("/load-scenario")
    public Result loadScenario(@RequestParam(defaultValue = "scenario-demo.json") String fileName) {
        ScenarioLoaderService.LoadResult result = scenarioLoaderService.load(fileName);
        return Result.success("场景加载成功并已重置沙盘环境", result);
    }

    @PostMapping("/clear")
    public Result clearContext() {
        GlobalContext ctx = GlobalContext.getInstance();
        ctx.getTruckMap().clear();
        ctx.getQcMap().clear();
        ctx.getAscMap().clear();
        ctx.getWorkInstructionMap().clear();
        ctx.getContainerMap().clear();
        return Result.success("沙盘已清空");
    }
}
