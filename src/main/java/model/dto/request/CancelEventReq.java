package model.dto.request;

import lombok.Data;

/**
 * 取消事件请求
 */
@Data
public class CancelEventReq {
    /**
     * 要取消的事件ID
     */
    private String eventId;
}
