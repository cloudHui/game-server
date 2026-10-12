package utils.concurrent.realtime;

import utils.concurrent.pool.Recyclable;

/**
 * 实时竞技 60 FPS 专用池化帧事件实体。
 * <p>
 * <b>应用场景：</b>
 * 网络 IO 线程高频接收玩家实时操作指令（移动、摇杆、技能施法）时，从对象池借出本实体并排队投递至实时房间。
 * 帧循环消费完毕后自动重置并归还入池，达成 100% 堆内存零分配（Zero-Allocation）。
 *
 * @author cloud
 */
public class FrameEvent implements Recyclable {

    /** 目标房间 ID */
    private long roomId;

    /** 触发玩家 ID */
    private long playerId;

    /** 事件类型标识 */
    private int eventType;

    /** 事件产生纳秒时钟戳 */
    private long timestampNs;

    /** 业务操作负载数据（如摇杆角度、坐标向量、技能参数） */
    private Object payload;

    public FrameEvent() {
        reset();
    }

    /**
     * 重置所有业务字段，杜绝帧间脏数据残留。
     */
    @Override
    public void reset() {
        this.roomId = 0L;
        this.playerId = 0L;
        this.eventType = 0;
        this.timestampNs = 0L;
        this.payload = null;
    }

    public long getRoomId() {
        return roomId;
    }

    public void setRoomId(long roomId) {
        this.roomId = roomId;
    }

    public long getPlayerId() {
        return playerId;
    }

    public void setPlayerId(long playerId) {
        this.playerId = playerId;
    }

    public int getEventType() {
        return eventType;
    }

    public void setEventType(int eventType) {
        this.eventType = eventType;
    }

    public long getTimestampNs() {
        return timestampNs;
    }

    public void setTimestampNs(long timestampNs) {
        this.timestampNs = timestampNs;
    }

    public Object getPayload() {
        return payload;
    }

    public void setPayload(Object payload) {
        this.payload = payload;
    }
}
