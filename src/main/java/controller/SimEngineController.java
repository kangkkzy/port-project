package controller;

import common.Result;
import engine.EngineState;
import engine.SimulationEngine;
import engine.SimEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * 仿真引擎生命周期控制 REST API
 */
@RestController
@RequestMapping("/api/engine")
@RequiredArgsConstructor
public class SimEngineController {

    private final SimulationEngine engine;

    /**
     * 单步执行：从事件队列取出并执行一个事件（供调试使用）
     */
    @PostMapping("/step")
    public Result step() {
        SimEvent executedEvent = engine.step();
        if (executedEvent != null) {
            return Result.success("执行了一个仿真事件", executedEvent.getType().name());
        }
        return Result.success("当前没有待处理的仿真事件");
    }

    /**
     * 启动引擎：若当前为暂停状态则恢复运行，否则启动后台事件循环
     */
    @PostMapping("/start")
    public Result start() {
        EngineState state = engine.getState();
        if (state == EngineState.PAUSED) {
            engine.resume();
            return Result.success("引擎已恢复运行");
        }
        engine.start();
        return Result.success("引擎已启动");
    }

    /**
     * 暂停引擎
     */
    @PostMapping("/pause")
    public Result pause() {
        engine.pause();
        return Result.success("引擎已暂停");
    }

    /**
     * 重置引擎：清空事件队列并恢复初始状态
     */
    @PostMapping("/reset")
    public Result reset() {
        engine.reset();
        return Result.success("重置成功");
    }

    /**
     * 查询当前引擎状态与仿真时间
     */
    @GetMapping("/status")
    public Result status() {
        Map<String, Object> data = new HashMap<>();
        data.put("state", engine.getState().name());
        data.put("simTime", engine.getSimTime());
        return Result.success(data);
    }
}