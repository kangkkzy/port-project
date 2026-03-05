package controller;

import common.Result;
import engine.SimulationEngine;
import engine.SimEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/sim/debug")
@RequiredArgsConstructor
public class SimEngineController {

    private final SimulationEngine engine;

    @PostMapping("/step")
    public Result step() {
        // 安全调用引擎向外暴露的单步执行方法
        SimEvent executedEvent = engine.step();
        if (executedEvent != null) {
            return Result.success("执行了一个仿真事件", executedEvent.getType().name());
        }
        return Result.success("当前没有待处理的仿真事件");
    }
}