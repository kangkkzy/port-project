package engine;

public interface Tickable {
    /**
     * 每一帧触发一次
     * @param deltaMS 距离上一帧经过的毫秒数
     * @param nowMS   当前的绝对时间戳
     */
    void tick(long deltaMS, long nowMS);
}
