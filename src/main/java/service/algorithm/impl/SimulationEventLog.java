package service.algorithm.impl;

import model.dto.snapshot.EventLogEntryDto;
import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * 简单的内存事件日志（最近 N 条）
 */
@Component
public class SimulationEventLog {

    private static final int DEFAULT_CAPACITY = 1000;

    private final Deque<EventLogEntryDto> buffer = new ArrayDeque<>(DEFAULT_CAPACITY);

    public synchronized void append(EventLogEntryDto entry) {
        if (buffer.size() >= DEFAULT_CAPACITY) {
            buffer.removeFirst();
        }
        buffer.addLast(entry);
    }

    /**
     * 按仿真时间过滤最近的事件
     */
    public synchronized List<EventLogEntryDto> listSince(long sinceSimTime) {
        List<EventLogEntryDto> result = new ArrayList<>();
        for (EventLogEntryDto dto : buffer) {
            if (dto.getSimTime() >= sinceSimTime) {
                result.add(dto);
            }
        }
        return result;
    }
}