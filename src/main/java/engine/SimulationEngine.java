package engine;

import common.config.PhysicsConfig;
import common.consts.EventTypeEnum;
import common.exception.SimulationDeadLoopException;
import engine.context.GlobalContext;
import engine.log.SimulationEventLog;
import engine.log.SimulationErrorLog;
import engine.websocket.SimulationEventWebSocketService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import model.dto.snapshot.EventLogEntryDto;
import model.entity.BaseDevice;
import model.entity.WorkInstruction;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.PriorityBlockingQueue;

/**
 * 仿真引擎核心类
 * <p>
 * 负责：
 * - 维护仿真时钟（单一时间源）
 * - 管理事件优先队列（按触发时间排序）
 * - 调度事件处理器（根据事件类型分发）
 * - 处理全局熔断（异常时暂停引擎）
 * - 提供单步执行、连续运行、播放控制等接口
 * <p>
 * 设计原则：
 * - 串行事件处理，每次只处理一个事件，保证状态一致性
 * - 一旦发生未捕获异常，触发全局暂停，保护现场便于排查
 * - 支持回放速度调节和时间同步（用于前端动画）
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class SimulationEngine implements InitializingBean {

    private final PhysicsConfig physicsConfig;
    private final SimulationEventLog eventLog;
    private final SimulationErrorLog errorLog;

    @Autowired(required = false)
    private SimulationEventWebSocketService webSocketService;   // 可选，没有也能运行

    private final GlobalContext context = GlobalContext.getInstance();

    // -------------------- 数据结构 --------------------

    /**
     * 事件优先队列，按仿真时间戳升序排列。
     * 引擎的核心驱动：每次从中取出最早的事件进行处理。
     */
    private final PriorityBlockingQueue<SimEvent> eventQueue = new PriorityBlockingQueue<>();

    /**
     * 事件类型到处理器的映射表。
     * 由 Spring 自动注入所有 SimEventHandler 实现类，并在 afterPropertiesSet 中注册。
     */
    private final Map<EventTypeEnum, SimEventHandler> handlerMap = new EnumMap<>(EventTypeEnum.class);
    private final List<SimEventHandler> handlerBeans;   // 由构造器注入所有处理器 Bean

    /**
     * 事件ID到事件的映射，用于快速查找或取消事件。
     */
    private final Map<String, SimEvent> eventIdMap = new ConcurrentHashMap<>();

    // -------------------- 状态控制标志 --------------------

    /**
     * 全局暂停标志。
     * 当引擎处理事件抛出异常时置为 true，阻止后续所有事件执行，直到人工 reset。
     */
    private volatile boolean globalSuspended = false;

    /**
     * 引擎是否正在自动运行（连续播放）。
     */
    private volatile boolean isRunning = false;

    /**
     * 引擎是否处于暂停状态（用于单步调试）。
     * 当 isRunning == false 且 isPaused == true 时，允许执行单步。
     */
    private volatile boolean isPaused = true;

    /**
     * 回放速度倍率。
     * 1.0 = 实时（1ms 仿真时间对应 1ms 真实时间）
     * 2.0 = 2倍速，0.5 = 0.5倍速
     */
    private double playbackSpeed = 1.0;

    /**
     * 是否启用时间同步。
     * 启用后，引擎在处理事件之间会 sleep 适当时间，使前端动画平滑。
     */
    private boolean timeSyncEnabled = false;

    // -------------------- 暂停现场记录 --------------------

    /**
     * 被挂起的业务类型集合（用于前端展示哪些业务被阻塞）。
     */
    private final java.util.Set<common.consts.BizTypeEnum> suspendedBizTypes = ConcurrentHashMap.newKeySet();

    /**
     * 被挂起的事件ID集合（导致熔断的事件源头）。
     */
    private final java.util.Set<String> suspendedEventIds = ConcurrentHashMap.newKeySet();

    // -------------------- 初始化 --------------------

    @Override
    public void afterPropertiesSet() {
        // 将所有 Spring 管理的 SimEventHandler 注册到 handlerMap
        for (SimEventHandler handler : handlerBeans) {
            handlerMap.put(handler.getType(), handler);
        }
    }

    // -------------------- 事件调度 --------------------

    /**
     * 调度一个新事件，放入事件队列。
     *
     * @param parentEventId 父事件ID（可为空）
     * @param triggerTime   触发时间（仿真时间戳）
     * @param type          事件类型
     * @param data          附加数据（通常为 Map）
     * @return 生成的事件对象
     */
    public SimEvent scheduleEvent(String parentEventId, long triggerTime, EventTypeEnum type, Object data) {
        SimEvent event = new SimEvent(parentEventId, triggerTime, type, data);
        eventQueue.add(event);
        eventIdMap.put(event.getEventId(), event);
        return event;
    }

    /**
     * 取消指定事件（标记为已取消，实际处理时会跳过）。
     *
     * @param eventId 事件ID
     * @return 如果事件存在则返回 true
     */
    public boolean cancelEvent(String eventId) {
        SimEvent event = eventIdMap.get(eventId);
        if (event == null) {
            return false;
        }
        event.setCancelled(true);
        return true;
    }

    // -------------------- 事件处理核心 --------------------

    /**
     * 处理单个事件的核心逻辑。
     * 包含：移除事件ID映射、推进仿真时钟、记录日志、调用处理器、WebSocket广播。
     * 任何异常都会触发全局熔断。
     *
     * @param event 待处理的事件
     */
    private void processEvent(SimEvent event) {
        // 跳过已取消的事件
        if (event.isCancelled()) {
            eventIdMap.remove(event.getEventId());
            return;
        }

        // 如果已经全局暂停，不再处理新事件
        if (globalSuspended) {
            return;
        }

        // 从映射中移除（事件已取出）
        eventIdMap.remove(event.getEventId());

        // 推进仿真时钟到事件触发时间
        context.setSimTime(event.getTriggerTime());

        // 记录流水日志（供前端查询）
        EventLogEntryDto logEntry = new EventLogEntryDto();
        logEntry.setSimTime(event.getTriggerTime());
        logEntry.setType(event.getType());
        logEntry.setEventId(event.getEventId());
        logEntry.setParentEventId(event.getParentEventId());
        logEntry.setSubjects(event.getSubjects());
        eventLog.append(logEntry);

        // 查找对应的事件处理器
        SimEventHandler handler = handlerMap.get(event.getType());
        if (handler != null) {
            try {
                // 执行处理器
                handler.handle(event, this, context);

                // 通过 WebSocket 推送事件给前端（如果服务可用）
                if (webSocketService != null) {
                    webSocketService.broadcast(event);
                }
            } catch (Exception e) {
                // 处理异常，触发全局熔断
                String errorMsg = String.format("事件处理异常，触发熔断: Type=%s, Id=%s, Time=%d, Error=%s",
                        event.getType(), event.getEventId(), event.getTriggerTime(), e.getMessage());

                errorLog.recordEventProcessingError(event.getEventId(), event.getType(),
                        event.getTriggerTime(), errorMsg, e, true);
                log.error(errorMsg, e);

                triggerGlobalSuspend(event);
            }
        } else {
            log.warn("未找到事件类型 {} 的处理器，忽略执行。", event.getType());
        }
    }

    // -------------------- 全局熔断 --------------------

    /**
     * 触发全局熔断，记录错误上下文并锁死引擎。
     *
     * @param sourceEvent 导致熔断的事件源
     */
    private void triggerGlobalSuspend(SimEvent sourceEvent) {
        this.globalSuspended = true;

        if (sourceEvent != null) {
            log.error(">>> 仿真引擎触发全局暂停 <<< 错误源头: Id={}, Type={}", sourceEvent.getEventId(), sourceEvent.getType());
            suspendedEventIds.add(sourceEvent.getEventId());

            // 尝试获取关联的业务类型，用于前端展示
            common.consts.BizTypeEnum bizType = getBizTypeFromEvent(sourceEvent);
            if (bizType != null) {
                suspendedBizTypes.add(bizType);
            }
        } else {
            log.error(">>> 仿真引擎触发全局暂停 <<< (未知事件源)");
        }
    }

    /**
     * 辅助方法：从事件中回溯关联的业务类型（MoveKind）。
     * 用于熔断时记录被挂起的业务类型。
     */
    private common.consts.BizTypeEnum getBizTypeFromEvent(SimEvent event) {
        if (event == null) return null;

        // 1. 尝试从事件 payload 中获取 wiRefNo
        if (event.getData() instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> payload = (Map<String, Object>) event.getData();
            String wiRefNo = (String) payload.get("wiRefNo");
            if (wiRefNo != null) {
                WorkInstruction wi = context.getWorkInstructionMap().get(wiRefNo);
                if (wi != null) return wi.getMoveKind();
            }
        }

        // 2. 尝试从事件主体中获取 WI
        String wiRefNoFromSubject = event.getPrimarySubject("WI");
        if (wiRefNoFromSubject != null) {
            WorkInstruction wi = context.getWorkInstructionMap().get(wiRefNoFromSubject);
            if (wi != null) return wi.getMoveKind();
        }

        // 3. 尝试从关联设备反查当前作业指令
        String deviceId = event.getPrimaryDeviceId();
        if (deviceId != null) {
            BaseDevice device = context.getDevice(deviceId);
            if (device != null && device.getCurrWiRefNo() != null) {
                WorkInstruction wi = context.getWorkInstructionMap().get(device.getCurrWiRefNo());
                if (wi != null) return wi.getMoveKind();
            }
        }

        // 4. 递归父事件
        String parentEventId = event.getParentEventId();
        if (parentEventId != null) {
            SimEvent parentEvent = eventIdMap.get(parentEventId);
            if (parentEvent != null) return getBizTypeFromEvent(parentEvent);
        }

        return null;
    }

    // -------------------- 对外状态查询 --------------------

    public java.util.Set<common.consts.BizTypeEnum> getSuspendedBizTypes() {
        return new java.util.HashSet<>(suspendedBizTypes);
    }

    public java.util.Set<String> getSuspendedEventIds() {
        return new java.util.HashSet<>(suspendedEventIds);
    }

    public boolean isGlobalSuspended() {
        return globalSuspended;
    }

    public long getSimTime() {
        return context.getSimTime();
    }

    // -------------------- 引擎重置 --------------------

    /**
     * 重置仿真引擎到初始状态。
     * 清空事件队列、清除熔断标志、恢复暂停状态。
     */
    public synchronized void reset() {
        eventQueue.clear();
        eventIdMap.clear();
        suspendedBizTypes.clear();
        suspendedEventIds.clear();
        globalSuspended = false;
        isRunning = false;
        isPaused = true;
        log.info("仿真引擎已重置，系统恢复就绪。");
    }

    // -------------------- 播放控制 --------------------

    public void setPlaybackSpeed(double speed) {
        this.playbackSpeed = speed;
        log.info("回放速度已设置为: {}x", speed);
    }

    public double getPlaybackSpeed() {
        return playbackSpeed;
    }

    public void setTimeSyncEnabled(boolean enabled) {
        this.timeSyncEnabled = enabled;
        log.info("时间同步已{}", enabled ? "启用" : "禁用");
    }

    public boolean isTimeSyncEnabled() {
        return timeSyncEnabled;
    }

    public boolean isRunning() {
        return isRunning;
    }

    public void setRunning(boolean running) {
        this.isRunning = running;
    }

    public boolean isPaused() {
        return isPaused;
    }

    public void setPaused(boolean paused) {
        this.isPaused = paused;
    }

    /**
     * 恢复引擎自动运行。
     */
    public void resume() {
        this.isPaused = false;
        this.isRunning = true;
        log.info("仿真引擎已恢复运行");
    }

    /**
     * 暂停引擎自动运行。
     */
    public void pause() {
        this.isRunning = false;
        this.isPaused = true;
        log.info("仿真引擎已暂停");
    }

    // -------------------- 单步执行 --------------------

    /**
     * 单步执行一个事件（供前端单步调试使用）。
     * 仅在引擎处于暂停状态且未全局熔断时有效。
     *
     * @return 执行的事件，若无可执行事件则返回 null
     */
    public synchronized SimEvent step() {
        if (globalSuspended) {
            log.warn("拒绝单步执行：引擎处于全局暂停状态。");
            return null;
        }

        if (isRunning) {
            log.warn("拒绝单步执行：引擎正在自动运行中。");
            return null;
        }

        // 确保处于暂停状态
        this.isPaused = true;

        if (eventQueue.isEmpty()) {
            log.info("事件队列为空，无法单步执行");
            return null;
        }

        SimEvent nextEvent = eventQueue.poll();
        if (nextEvent == null) {
            return null;
        }

        log.info("单步执行事件: {} at time {}", nextEvent.getEventId(), nextEvent.getTriggerTime());
        processEvent(nextEvent);
        return nextEvent;
    }

    // -------------------- 连续运行 --------------------

    /**
     * 连续运行引擎直到指定的仿真时间。
     * <p>
     * 从事件队列中依次取出触发时间 ≤ targetSimTime 的事件并处理。
     * 包含死循环检测：同一时间戳连续处理事件数超过配置阈值时抛出异常并熔断。
     *
     * @param targetSimTime 目标仿真时间（毫秒）
     * @throws SimulationDeadLoopException 当检测到死循环时抛出
     */
    public synchronized void runUntil(long targetSimTime) {
        int sameTimeEventCount = 0;          // 同一时间戳连续处理的事件计数
        long lastProcessedTime = -1L;        // 上一个处理的事件时间戳
        int maxEventsPerTimestamp = physicsConfig.getMaxEventsPerTimestamp(); // 阈值
        long currentTime = context.getSimTime();

        while (!eventQueue.isEmpty()) {
            if (globalSuspended) {
                log.warn("runUntil 中止：引擎已暂停");
                break;
            }

            SimEvent nextEvent = eventQueue.peek();
            if (nextEvent.getTriggerTime() > targetSimTime) {
                // 已推进到目标时间之后，更新时钟并退出
                if (!globalSuspended && targetSimTime > currentTime) {
                    context.setSimTime(targetSimTime);
                }
                break;
            }

            // 死循环检测：同一时间戳连续处理过多事件
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
                sameTimeEventCount = 1;   // 重置为新时间戳的第一个事件
            }

            // 注意：在 HTTP 驱动模式下（前端调用 tick），这里不再 sleep。
            // 前端已经通过 tick() 的间隔控制了视觉上的动画速度。
            // 只有在后端自主运行模式（isRunning == true）时才需要 syncToRealTime。
            // if (timeSyncEnabled) {
            //     syncToRealTime(nextEvent.getTriggerTime());
            // }

            // 取出并处理事件
            eventQueue.poll();
            processEvent(nextEvent);
            currentTime = context.getSimTime();
        }

        // 如果队列为空但目标时间大于当前时间，直接推进时钟（空闲推进）
        if (!globalSuspended && eventQueue.isEmpty() && targetSimTime > currentTime) {
            context.setSimTime(targetSimTime);
        }
    }

    /**
     * 时间同步：根据播放速度使真实时间与仿真时间对齐。
     * 用于前端动画展示时，让事件之间的间隔在真实时间上被感知。
     *
     * @param targetSimTime 即将处理的事件的时间戳
     */
    private void syncToRealTime(long targetSimTime) {
        long currentSimTime = context.getSimTime();
        long timeDelta = targetSimTime - currentSimTime;

        if (timeDelta <= 0) {
            return;
        }

        // 根据播放速度计算需要等待的真实毫秒数
        long realTimeToSleep = (long) (timeDelta / playbackSpeed);

        if (realTimeToSleep > 0) {
            try {
                Thread.sleep(realTimeToSleep);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("时间同步被中断");
            }
        }
    }
}