package model.dto.snapshot;

import common.consts.EventTypeEnum;
import lombok.Data;

import java.util.Map;

/**
 * 离散事件日志条目 DTO
 */
@Data
public class EventLogEntryDto {
    /**
     * 事件触发时间（仿真时间戳，毫秒）
     */
    private long simTime;

    /**
     * 事件类型
     */
    private EventTypeEnum type;

    /**
     * 事件唯一ID
     */
    private String eventId;

    /**
     * 父事件ID（可选，用于构建因果链）
     */
    private String parentEventId;

    /**
     * 事件主体（TRUCK/CRANE/FENCE/WI 等）
     */
    private Map<String, String> subjects;
}