package common;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 响应结果
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Result {
    private Integer code; // 200成功 500失败
    private String msg;   // 消息
    private Object data;  // 数据

    // 成功 (无数据)
    public static Result success() {
        return new Result(200, "操作成功", null);
    }

    // 成功 (带数据)
    public static Result success(Object data) {
        return new Result(200, "操作成功", data);
    }

    // 成功 (带消息和数据)
    public static Result success(String msg, Object data) {
        return new Result(200, msg, data);
    }

    // 失败 (默认 500 状态码)
    public static Result error(String msg) {
        return new Result(500, msg, null);
    }

    // 失败 (带自定义状态码和消息)
    @SuppressWarnings("unused")
    public static Result error(Integer code, String msg) {
        return new Result(code, msg, null);
    }
}