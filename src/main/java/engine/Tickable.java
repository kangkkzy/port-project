package engine;
@SuppressWarnings("unused")
public interface Tickable {

    /**
     * 每一帧（Tick）触发一次
     * @param deltaMS 距离上一帧经过的毫秒数 (用于计算这一帧应该前进多远)
     * @param nowMS   当前的绝对时间戳
     */
    void tick(long deltaMS, long nowMS);
}