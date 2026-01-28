package service.algorithm.impl;

import common.consts.EventTypeEnum;
import lombok.Data;
import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * 仿真错误日志服务
 * 记录异常和死循环信息，供外部算法查询
 */
@Component
public class SimulationErrorLog {

    private static final int DEFAULT_CAPACITY = 500;

    private final Deque<ErrorLogEntry> errorBuffer = new ArrayDeque<>(DEFAULT_CAPACITY);

    /**
     * 记录事件处理异常
     */
    public synchronized void recordEventProcessingError(String eventId, EventTypeEnum eventType,
                                                        long simTime, String message, Throwable cause) {
        recordEventProcessingError(eventId, eventType, simTime, message, cause, true);
    }

    /**
     * 记录事件处理异常（带事件链暂停标记）
     */
    public synchronized void recordEventProcessingError(String eventId, EventTypeEnum eventType,
                                                        long simTime, String message, Throwable cause,
                                                        boolean eventChainSuspended) {
        ErrorLogEntry entry = new ErrorLogEntry();
        entry.setErrorType(ErrorType.EVENT_PROCESSING_ERROR);
        entry.setSimTime(simTime);
        entry.setEventId(eventId);
        entry.setEventType(eventType);
        entry.setMessage(message);
        entry.setCause(cause != null ? cause.getClass().getSimpleName() + ": " + cause.getMessage() : null);
        entry.setEventChainSuspended(eventChainSuspended);
        entry.setTimestamp(System.currentTimeMillis());

        addEntry(entry);
    }

    /**
     * 记录死循环错误
     */
    public synchronized void recordDeadLoopError(long simTime, int eventCount, int threshold, String message) {
        ErrorLogEntry entry = new ErrorLogEntry();
        entry.setErrorType(ErrorType.DEAD_LOOP);
        entry.setSimTime(simTime);
        entry.setMessage(message);
        entry.setEventCount(eventCount);
        entry.setThreshold(threshold);
        entry.setTimestamp(System.currentTimeMillis());

        addEntry(entry);
    }

    private void addEntry(ErrorLogEntry entry) {
        if (errorBuffer.size() >= DEFAULT_CAPACITY) {
            errorBuffer.removeFirst();
        }
        errorBuffer.addLast(entry);
    }

    /**
     * 查询指定时间之后的错误日志
     */
    public synchronized List<ErrorLogEntry> listSince(long sinceSimTime) {
        List<ErrorLogEntry> result = new ArrayList<>();
        for (ErrorLogEntry entry : errorBuffer) {
            if (entry.getSimTime() >= sinceSimTime) {
                result.add(entry);
            }
        }
        return result;
    }

    /**
     * 查询所有错误日志
     */
    public synchronized List<ErrorLogEntry> listAll() {
        return new ArrayList<>(errorBuffer);
    }

    /**
     * 错误类型
     */
    public enum ErrorType {
        EVENT_PROCESSING_ERROR,  // 事件处理异常
        DEAD_LOOP                // 死循环
    }

    /**
     * 错误日志条目
     */
    @Data
    public static class ErrorLogEntry {
        private ErrorType errorType;
        private long simTime;
        private String eventId;
        private EventTypeEnum eventType;
        private String message;
        private String cause;
        private Integer eventCount;
        private Integer threshold;
        private long timestamp;
        private Boolean eventChainSuspended; // 事件链是否被暂停
    }
}
