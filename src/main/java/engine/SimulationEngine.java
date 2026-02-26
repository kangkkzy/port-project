package engine;

import common.config.PhysicsConfig;
import common.consts.BizTypeEnum;
import common.consts.EventTypeEnum;
import common.exception.SimulationDeadLoopException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import model.bo.GlobalContext;
import model.dto.snapshot.EventLogEntryDto;
import model.entity.BaseDevice;
import model.entity.WorkInstruction;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.PriorityBlockingQueue;

import service.algorithm.impl.SimulationEventLog;
import service.algorithm.impl.SimulationErrorLog;

/**
 * 仿真引擎核心
 * 负责维护仿真时钟、管理事件优先队列、调度事件处理以及处理全局异常熔断。
 * 设计原则：单一时钟源、串行事件链。一旦发生未捕获异常，引擎将触发全局暂停以保护现场。
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class SimulationEngine implements InitializingBean {

    private final PhysicsConfig physicsConfig;
    private final SimulationEventLog eventLog;
    private final SimulationErrorLog errorLog;
    private final GlobalContext context = GlobalContext.getInstance();

    // 数据结构

    /**
     * 事件优先队列
     * 按仿真时间戳排序 驱动仿真推进
     */
    private final PriorityBlockingQueue<SimEvent> eventQueue = new PriorityBlockingQueue<>();

    /** 事件处理器注册表 */
    private final Map<EventTypeEnum, SimEventHandler> handlerMap = new EnumMap<>(EventTypeEnum.class);
    private final List<SimEventHandler> handlerBeans;

    /** 事件索引，用于快速查找/取消事件 */
    private final Map<String, SimEvent> eventIdMap = new ConcurrentHashMap<>();

    // 状态控制

    /**
     * 全局暂停标志
     * 出现异常时置为 true，阻断后续事件执行，直到人工 reset。
     */
    private volatile boolean globalSuspended = false;

    // 暂停现场记录
    private final java.util.Set<common.consts.BizTypeEnum> suspendedBizTypes = ConcurrentHashMap.newKeySet();
    private final java.util.Set<String> suspendedEventIds = ConcurrentHashMap.newKeySet();

    @Override
    public void afterPropertiesSet() {
        // 自动注册所有 Spring 管理的 Handler
        for (SimEventHandler handler : handlerBeans) {
            handlerMap.put(handler.getType(), handler);
        }
    }

    /**
     * 调度新事件
     */
    public SimEvent scheduleEvent(String parentEventId, long triggerTime, EventTypeEnum type, Object data) {
        SimEvent event = new SimEvent(parentEventId, triggerTime, type, data);
        eventQueue.add(event);
        eventIdMap.put(event.getEventId(), event);
        return event;
    }

    /**
     * 取消事件
     */
    public boolean cancelEvent(String eventId) {
        SimEvent event = eventIdMap.get(eventId);
        if (event == null) {
            return false;
        }
        event.setCancelled(true);
        return true;
    }

    /**
     * 辅助方法：尝试回溯报错事件关联的业务类型
     */
    private common.consts.BizTypeEnum getBizTypeFromEvent(SimEvent event) {
        if (event == null) return null;

        // 1. 尝试从 payload 获取
        if (event.getData() instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> payload = (Map<String, Object>) event.getData();
            String wiRefNo = (String) payload.get("wiRefNo");
            if (wiRefNo != null) {
                WorkInstruction wi = context.getWorkInstructionMap().get(wiRefNo);
                if (wi != null) return wi.getMoveKind();
            }
        }
        // 尝试从关联的 WI Subject 获取
        String wiRefNoFromSubject = event.getPrimarySubject("WI");
        if (wiRefNoFromSubject != null) {
            WorkInstruction wi = context.getWorkInstructionMap().get(wiRefNoFromSubject);
            if (wi != null) return wi.getMoveKind();
        }
        // 尝试从设备反查
        String deviceId = event.getPrimaryDeviceId();
        if (deviceId != null) {
            BaseDevice device = context.getDevice(deviceId);
            if (device != null && device.getCurrWiRefNo() != null) {
                WorkInstruction wi = context.getWorkInstructionMap().get(device.getCurrWiRefNo());
                if (wi != null) return wi.getMoveKind();
            }
        }
        // 递归父事件
        String parentEventId = event.getParentEventId();
        if (parentEventId != null) {
            SimEvent parentEvent = eventIdMap.get(parentEventId);
            if (parentEvent != null) return getBizTypeFromEvent(parentEvent);
        }
        return null;
    }

    /**
     * 触发全局熔断
     * 记录错误上下文并锁死引擎。
     */
    private void triggerGlobalSuspend(SimEvent event) {
        this.globalSuspended = true;

        if (event != null) {
            log.error(">>> 仿真引擎触发全局暂停 <<< 错误源头: Id={}, Type={}", event.getEventId(), event.getType());
            suspendedEventIds.add(event.getEventId());
            common.consts.BizTypeEnum bizType = getBizTypeFromEvent(event);
            if (bizType != null) {
                suspendedBizTypes.add(bizType);
            }
        } else {
            log.error(">>> 仿真引擎触发全局暂停 <<< (未知事件源)");
        }
    }

    public java.util.Set<common.consts.BizTypeEnum> getSuspendedBizTypes() {
        return new java.util.HashSet<>(suspendedBizTypes);
    }

    public java.util.Set<String> getSuspendedEventIds() {
        return new java.util.HashSet<>(suspendedEventIds);
    }

    /**
     * 重置仿真状态
     * 清空队列、重置时钟锁、清除错误记录。
     */
    public synchronized void reset() {
        eventQueue.clear();
        eventIdMap.clear();
        suspendedBizTypes.clear();
        suspendedEventIds.clear();
        globalSuspended = false;
        log.info("仿真引擎已重置，系统恢复就绪。");
    }

    /**
     * 单步执行
     * 供前端单步调试或 runUntil 内部调用。
     */
    public synchronized SimEvent stepNextEvent() {
        if (globalSuspended) {
            log.warn("拒绝执行：引擎处于全局暂停状态。");
            return null;
        }

        if (eventQueue.isEmpty()) {
            return null;
        }

        SimEvent nextEvent = eventQueue.poll();
        if (nextEvent == null) {
            return null;
        }

        processEvent(nextEvent);
        return nextEvent;
    }

    /**
     * 事件处理核心逻辑
     * 包含完整的异常捕获 只要有异常就会中断
     */
    private void processEvent(SimEvent nextEvent) {
        // 跳过已取消或暂停状态
        if (nextEvent.isCancelled()) {
            eventIdMap.remove(nextEvent.getEventId());
            return;
        }
        if (globalSuspended) {
            return;
        }

        eventIdMap.remove(nextEvent.getEventId());

        // 推进时钟
        context.setSimTime(nextEvent.getTriggerTime());

        // 记录流水日志
        EventLogEntryDto logEntry = new EventLogEntryDto();
        logEntry.setSimTime(nextEvent.getTriggerTime());
        logEntry.setType(nextEvent.getType());
        logEntry.setEventId(nextEvent.getEventId());
        logEntry.setParentEventId(nextEvent.getParentEventId());
        logEntry.setSubjects(nextEvent.getSubjects());
        eventLog.append(logEntry);

        // 分发执行
        SimEventHandler handler = handlerMap.get(nextEvent.getType());
        if (handler != null) {
            try {
                handler.handle(nextEvent, this, context);
            } catch (Exception e) {
                String errorMsg = String.format("事件处理异常，触发熔断: Type=%s, Id=%s, Time=%d, Error=%s",
                        nextEvent.getType(), nextEvent.getEventId(), nextEvent.getTriggerTime(), e.getMessage());

                errorLog.recordEventProcessingError(nextEvent.getEventId(), nextEvent.getType(),
                        nextEvent.getTriggerTime(), errorMsg, e, true);
                log.error(errorMsg, e);

                triggerGlobalSuspend(nextEvent);
            }
        } else {
            log.warn("未找到事件类型 {} 的处理器，忽略执行。", nextEvent.getType());
        }
    }

    /**
     * 连续运行直到指定时间
     * 防止同一时间戳产生无限微小事件
     */
    public synchronized void runUntil(long targetSimTime) {
        int sameTimeEventCount = 0;
        long lastProcessedTime = -1L;
        int maxEventsPerTimestamp = physicsConfig.getMaxEventsPerTimestamp();
        long currentTime = context.getSimTime();

        while (!eventQueue.isEmpty()) {
            if (globalSuspended) {
                log.warn("runUntil 中止：引擎已暂停");
                break;
            }

            SimEvent nextEvent = eventQueue.peek();
            if (nextEvent.getTriggerTime() > targetSimTime) {
                // 已推进到目标时间 更新时钟并退出
                if (!globalSuspended && targetSimTime > currentTime) {
                    context.setSimTime(targetSimTime);
                }
                break;
            }

            // 死循环检测
            if (nextEvent.getTriggerTime() == lastProcessedTime) {
                sameTimeEventCount++;
                if (sameTimeEventCount > maxEventsPerTimestamp) {
                    String errorMsg = String.format("检测到仿真死循环: 时间戳 %d 堆积超过 %d 个零耗时事件",
                            lastProcessedTime, maxEventsPerTimestamp);

                    errorLog.recordDeadLoop(lastProcessedTime, sameTimeEventCount, maxEventsPerTimestamp);
                    triggerGlobalSuspend(nextEvent);
                    throw new SimulationDeadLoopException(errorMsg, lastProcessedTime, sameTimeEventCount);
                }
            } else {
                lastProcessedTime = nextEvent.getTriggerTime();
                sameTimeEventCount = 1;
            }

            eventQueue.poll();
            processEvent(nextEvent);
            currentTime = context.getSimTime();
        }

        // 空跑或队列处理完后 确保时钟对齐到目标时间
        if (!globalSuspended && eventQueue.isEmpty() && targetSimTime > currentTime) {
            context.setSimTime(targetSimTime);
        }
    }
}