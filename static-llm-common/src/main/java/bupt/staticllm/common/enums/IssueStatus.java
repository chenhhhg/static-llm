package bupt.staticllm.common.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.Getter;

@Getter
public enum IssueStatus {
    PENDING(0, "待处理"),
    RAG_RETRIEVING(1, "RAG检索中"),
    RAG_COMPLETED(2, "RAG已完成"),
    LLM_ANALYZING(3, "LLM分析中"),
    COMPLETED(4, "已完成"),
    FAILED(99, "失败");

    @EnumValue
    private final int code;
    private final String desc;

    IssueStatus(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }
}

