package engine;

import common.consts.EventTypeEnum;
import common.consts.FenceStateEnum;
import lombok.extern.slf4j.Slf4j;
import model.bo.GlobalContext;
import model.entity.BaseDevice;
import model.entity.Fence;
import model.entity.Point;
import org.springframework.stereotype.Component;

import java.util.PriorityQueue;

@Component
@Slf4j
public class SimulationEngine {

    private final GlobalContext context = GlobalContext.getInstance();

    // 【DES核心】优先队列，按发生时间自动排序
    private final PriorityQueue<SimEvent> eventQueue = new PriorityQueue<>();

    /**
     * 向未来注册一个事件
     */
    public void scheduleEvent(long triggerTime, EventTypeEnum type, String targetId, Object data) {
        eventQueue.add(new SimEvent(triggerTime, type, targetId, data));
    }

    /**
     * 外部算法驱动接口 推演仿真系统直到指定的 targetSimTime
     */
    public void runUntil(long targetSimTime) {
        while (!eventQueue.isEmpty()) {
            SimEvent nextEvent = eventQueue.peek();

            // 如果队列最前面的事件发生时间 已经超过要推演的时间 暂停推演
            if (nextEvent.getTriggerTime() > targetSimTime) {
                break;
            }

            // 取出事件
            eventQueue.poll();

            // 时钟瞬间跳跃到事件发生的时间
            context.setSimTime(nextEvent.getTriggerTime());

            // 消费事件
            handleEvent(nextEvent);
        }
        // 推演结束 将当前仿真时间同步为目标时间
        context.setSimTime(targetSimTime);
    }

    /**
     * 事件消费中心：严格执行事件
     */
    private void handleEvent(SimEvent event) {
        long now = context.getSimTime();

        switch (event.getType()) {
            case MOVE_START:
                BaseDevice device = context.getTruckMap().get(event.getTargetId());
                if (device != null) device.onMoveStart(now, this);
                break;

            case ARRIVAL:
                BaseDevice arrivingDevice = context.getTruckMap().get(event.getTargetId());
                if (arrivingDevice != null) arrivingDevice.onArrival((Point) event.getData(), now, this);
                break;

            //  执行外部算法下发的任意栅栏状态
            case FENCE_CONTROL:
                Fence fence = context.getFenceMap().get(event.getTargetId());
                if (fence != null) fence.setStatus((FenceStateEnum) event.getData());
                break;

            // 预留扩展接口
            default:
                break;
        }
    }
}