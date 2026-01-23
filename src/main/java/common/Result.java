package common;

import lombok.Data;

/**
 * 响应结果
 */
@Data
public class Result {
    private Integer code; // 200成功 500失败
    private String msg;   // 消息
    private Object data;  // 数据

    // 空构造
    public Result() {
    }

    // 全参构造
    public Result(Integer code, String msg, Object data) {
        this.code = code;
        this.msg = msg;
        this.data = data;
    }

    //  无参的成功返回方法
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

    // 失败 (带消息)
    public static Result error(String msg) {
        return new Result(500, msg, null);
    }

    // 失败 (带自定义状态码和消息)
    public static Result error(Integer code, String msg) {
        return new Result(code, msg, null);
    }
}