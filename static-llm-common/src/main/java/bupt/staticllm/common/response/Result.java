package bupt.staticllm.common.response;

import bupt.staticllm.common.enums.ReturnCode;
import lombok.Data;

import java.io.Serializable;

@Data
public class Result<T> implements Serializable {
    private int code;
    private String message;
    private T data;

    public static <T> Result<T> success() {
        return success(null);
    }

    public static <T> Result<T> success(T data) {
        Result<T> result = new Result<>();
        result.setCode(ReturnCode.SUCCESS.getCode());
        result.setMessage(ReturnCode.SUCCESS.getMessage());
        result.setData(data);
        return result;
    }

    public static <T> Result<T> fail(String message) {
        Result<T> result = new Result<>();
        result.setCode(ReturnCode.FAIL.getCode());
        result.setMessage(message);
        return result;
    }

    public static <T> Result<T> fail(ReturnCode returnCode) {
        Result<T> result = new Result<>();
        result.setCode(returnCode.getCode());
        result.setMessage(returnCode.getMessage());
        return result;
    }
}
