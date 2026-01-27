package engine;

import common.consts.EventTypeEnum;
import lombok.Data;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

@Data
public class SimEvent implements Comparable<SimEvent> {
    private String eventId;         // 本次事件唯一标识
    private String parentEventId;   // 触发本事件的上游事件ID
    private long triggerTime;       // 事件发生的绝对仿真时间戳
    private EventTypeEnum type;     // 事件类型

    // 多主体参与 如 {"TRUCK": "T001", "CRANE": "QC01", "FENCE": "F01"}
    private Map<String, String> subjects;

    private Object data;            // 事件负载
    private boolean cancelled;      // 是否被取消
    private long creationSequence;  // 创建序号

    // 计数器 解决同一毫秒内的事件排序
    private static final AtomicLong sequenceGenerator = new AtomicLong(0);

    // 构造函数
    public SimEvent(String parentEventId, long triggerTime, EventTypeEnum type, Object data) {
        this.eventId = UUID.randomUUID().toString();
        this.parentEventId = parentEventId;
        this.triggerTime = triggerTime;
        this.type = type;
        this.subjects = new HashMap<>();
        this.data = data;
        this.cancelled = false;
        this.creationSequence = sequenceGenerator.getAndIncrement();
    }

    /**
     * 添加参与该事件的主体
     */
    public void addSubject(String role, String targetId) {
        this.subjects.put(role, targetId);
    }

    /**
     *  指定的ID
     */
    public String getPrimarySubject(String role) {
        return subjects.get(role);
    }

    @Override
    public int compareTo(SimEvent other) {
        //  按时间早晚排
        int timeCompare = Long.compare(this.triggerTime, other.triggerTime);
        if (timeCompare != 0) {
            return timeCompare;
        }
        //  时间相同 按事件生成顺序排 确保先触发的并发事件先执行
        return Long.compare(this.creationSequence, other.creationSequence);
    }
}
