package controller;

import common.Result;
import model.dto.snapshot.EventLogEntryDto;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import service.algorithm.impl.SimulationEventLog;

import java.util.List;

/**
 * 仿真事件日志查询接口
 */
@RestController
@RequestMapping("/sim/events")
public class SimEventController {

    private final SimulationEventLog eventLog;

    public SimEventController(SimulationEventLog eventLog) {
        this.eventLog = eventLog;
    }

    /**
     * 查询最近一段时间内的事件
     */
    @GetMapping
    public Result listEvents(@RequestParam(name = "since", defaultValue = "0") long sinceSimTime) {
        List<EventLogEntryDto> entries = eventLog.listSince(sinceSimTime);
        return Result.success("查询成功", entries);
    }
}