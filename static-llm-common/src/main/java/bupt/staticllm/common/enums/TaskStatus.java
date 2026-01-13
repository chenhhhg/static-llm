package bupt.staticllm.common.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.Getter;

@Getter
public enum TaskStatus {
    SUBMITTED(0, "已提交"),
    DOWNLOADING(1, "下载中"),
    WAITING_ANALYSIS(2, "等待提交分析"),
    ANALYZING(3, "正在分析"),
    WAITING_LLM(4, "等待提交大模型"),
    JUDGING(5, "正在判别"),
    COMPLETED(6, "已完成"),
    FAILED(99, "失败"),
    CANCELLED(-1, "已取消");

    @EnumValue
    private final int code;
    private final String desc;

    TaskStatus(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }
}
