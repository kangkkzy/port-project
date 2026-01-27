package engine;

import common.consts.EventTypeEnum;
import model.bo.GlobalContext;

/**
 * 事件处理器扩展点
 * 新增事件类型时只需新增实现类 无需修改 SimulationEngine 本身。
 */
public interface SimEventHandler {

    /**
     * 该处理器负责的事件类型
     */
    EventTypeEnum getType();

    /**
     * 执行具体事件处理逻辑
     */
    void handle(SimEvent event, SimulationEngine engine, GlobalContext context);
}
