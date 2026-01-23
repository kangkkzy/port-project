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

    //  优先队列 按发生时间自动排序
    private final PriorityQueue<SimEvent> eventQueue = new PriorityQueue<>();

    /**
     * 向未来注册一个事件
     */
    public void scheduleEvent(long triggerTime, EventTypeEnum type, String targetId, Object data) {
        eventQueue.add(new SimEvent(triggerTime, type, targetId, data));
    }

    /**
     * 外部算法驱动接口 推演仿真系统
     */
    public void runUntil(long targetSimTime) {
        while (!eventQueue.isEmpty()) {
            SimEvent nextEvent = eventQueue.peek();

            if (nextEvent.getTriggerTime() > targetSimTime) {
                break;
            }

            eventQueue.poll();
            context.setSimTime(nextEvent.getTriggerTime());
            handleEvent(nextEvent);
        }
        context.setSimTime(targetSimTime);
    }

    /**
     * 事件消费中心
     */
    private void handleEvent(SimEvent event) {
        long now = context.getSimTime();

        //  获取设备  集卡、岸桥、龙门吊
        BaseDevice device = context.getDevice(event.getTargetId());

        switch (event.getType()) {
            case MOVE_START:
                if (device != null) device.onMoveStart(now, this);
                break;

            case ARRIVAL:
                if (device != null) device.onArrival((Point) event.getData(), now, this);
                break;

            //  完成抓箱操作
            case FETCH_DONE:
                if (device != null) {
                    log.info("时间:{} 设备:{} 完成抓箱", now, device.getId());
                    //  释放设备
                }
                break;

            //  完成放箱的操作
            case PUT_DONE:
                if (device != null) {
                    log.info("时间:{} 设备:{} 完成放箱", now, device.getId());
                    //  触发 作业指令完成 事件
                }
                break;

            case FENCE_CONTROL:
                Fence fence = context.getFenceMap().get(event.getTargetId());
                if (fence != null) {
                    FenceStateEnum targetStatus = (FenceStateEnum) event.getData();
                    fence.setStatus(targetStatus.getCode());
                }
                break;

            default:
                break;
        }
    }
}