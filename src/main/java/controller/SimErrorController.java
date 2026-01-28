package controller;

import common.Result;
import engine.SimulationEngine;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import service.algorithm.impl.SimulationErrorLog;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 仿真错误日志查询接口
 * 供外部算法查询异常和死循环信息
 */
@RestController
@RequestMapping("/sim/errors")
public class SimErrorController {

    private final SimulationErrorLog errorLog;
    private final SimulationEngine simulationEngine;

    public SimErrorController(SimulationErrorLog errorLog, SimulationEngine simulationEngine) {
        this.errorLog = errorLog;
        this.simulationEngine = simulationEngine;
    }

    /**
     * 查询最近一段时间内的错误日志
     */
    @GetMapping
    public Result listErrors(@RequestParam(name = "since", defaultValue = "0") long sinceSimTime) {
        List<SimulationErrorLog.ErrorLogEntry> entries = errorLog.listSince(sinceSimTime);
        return Result.success("查询成功", entries);
    }

    /**
     * 查询所有错误日志
     */
    @GetMapping("/all")
    public Result listAllErrors() {
        List<SimulationErrorLog.ErrorLogEntry> entries = errorLog.listAll();
        return Result.success("查询成功", entries);
    }

    /**
     * 查询所有被暂停的业务类型和事件
     */
    @GetMapping("/suspended-chains")
    public Result listSuspendedEventChains() {
        Set<common.consts.BizTypeEnum> suspendedBizTypes = simulationEngine.getSuspendedBizTypes();
        Set<String> suspendedEventIds = simulationEngine.getSuspendedEventIds();
        Map<String, Object> result = new HashMap<>();
        result.put("suspendedBizTypes", suspendedBizTypes);
        result.put("suspendedEventIds", suspendedEventIds);
        result.put("bizTypeCount", suspendedBizTypes.size());
        result.put("eventIdCount", suspendedEventIds.size());
        return Result.success("查询成功", result);
    }
}