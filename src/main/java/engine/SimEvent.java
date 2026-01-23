package engine;

import common.consts.EventTypeEnum;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class SimEvent implements Comparable<SimEvent> {
    private long triggerTime;       // 事件发生的绝对仿真时间戳
    private EventTypeEnum type;     // 事件类型
    private String targetId;        // 事件主体ID (如 集卡ID  栅栏ID)
    private Object data;            // 事件负载 (如 目标坐标点)

    @Override
    public int compareTo(SimEvent other) {
        // 时间早的排在队列前面
        return Long.compare(this.triggerTime, other.triggerTime);
    }
}
