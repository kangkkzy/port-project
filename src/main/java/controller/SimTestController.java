package controller;

import common.Result;
import engine.context.GlobalContext;
import lombok.RequiredArgsConstructor;
import model.dto.request.AssignTaskReq;
import model.entity.WorkInstruction;
import org.springframework.web.bind.annotation.*;
import service.algorithm.TaskDecisionService;
import service.scenario.ScenarioLoaderService;

@RestController
@RequestMapping("/sim/test")
@RequiredArgsConstructor
public class SimTestController {

    private final ScenarioLoaderService scenarioLoaderService;
    private final TaskDecisionService taskDecisionService;

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
            return Result.error("当前场景中没有指令，请先加载场景");
        }

        int count = 0;
        // 模拟外部算法：将所有场景中预设的指令转化为调度请求下发
        for (WorkInstruction wi : ctx.getWorkInstructionMap().values()) {
            AssignTaskReq req = new AssignTaskReq();
            req.setWiRefNo(wi.getWiRefNo());
            // 假设外部算法已经分配好了集卡和岸桥/龙门吊
            req.setTruckId(wi.getCarryCheId());
            req.setCraneId(wi.getFetchCheId() != null ? wi.getFetchCheId() : wi.getPutCheId());

            // 下发给引擎责任链（会进行防碰撞、堆叠拦截，通过后入队）
            taskDecisionService.assignTask(req);
            count++;
        }

        return Result.success("已模拟外部算法派发了 " + count + " 条调度指令到引擎");
    }
}
