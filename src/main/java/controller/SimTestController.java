package controller;

import common.Result;
import engine.context.GlobalContext;
import lombok.RequiredArgsConstructor;
import model.dto.request.AssignTaskReq;
import model.entity.WorkInstruction;
import org.springframework.web.bind.annotation.*;
import service.algorithm.ExternalAlgorithmApi;
import service.scenario.ScenarioLoaderService;

@RestController
@RequestMapping("/sim/test")
@RequiredArgsConstructor
public class SimTestController {

    private final ScenarioLoaderService scenarioLoaderService;
    private final ExternalAlgorithmApi algorithmApi;

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

    @PostMapping("/dispatch-all")
    public Result dispatchAllLoadedInstructions() {
        GlobalContext ctx = GlobalContext.getInstance();
        if (ctx.getWorkInstructionMap().isEmpty()) {
            return Result.error("当前场景中没有指令。请检查外部 JSON 场景文件是否包含了 workInstructions。");
        }

        int count = 0;
        for (WorkInstruction wi : ctx.getWorkInstructionMap().values()) {
            AssignTaskReq req = new AssignTaskReq();
            req.setWiRefNo(wi.getWiRefNo());
            req.setTruckId(wi.getCarryCheId());
            req.setCraneId(wi.getFetchCheId() != null ? wi.getFetchCheId() : wi.getPutCheId());
            algorithmApi.assignTask(req);
            count++;
        }

        return Result.success("模拟外部算法成功，已通过标准 API 派发了 " + count + " 条调度指令到引擎");
    }
}