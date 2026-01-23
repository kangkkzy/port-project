package model.dto.request;

import lombok.Data;

@Data
public class FenceControlReq {
    private String fenceId; // 控制哪个栅栏
    private String status;  // "01":锁死, "02":通行
}