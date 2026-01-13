package bupt.staticllm.common.enums;

import lombok.Getter;

@Getter
public enum ReturnCode {
    SUCCESS(200, "操作成功"),
    FAIL(500, "操作失败"),
    PARAM_ERROR(400, "参数错误"),
    NOT_FOUND(404, "资源不存在"),
    UNAUTHORIZED(401, "未授权");

    private final int code;
    private final String message;

    ReturnCode(int code, String message) {
        this.code = code;
        this.message = message;
    }
}
