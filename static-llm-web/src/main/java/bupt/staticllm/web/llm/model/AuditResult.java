package bupt.staticllm.web.llm.model;

import lombok.Data;
import java.util.List;

@Data
public class AuditResult {
    private List<IssueAnalysis> analyses;

    @Data
    public static class IssueAnalysis {
        /**
         * 对应数据库中的 analysis_issue.id
         */
        private Long issueId;
        
        /**
         * 是否误报
         */
        private Boolean isFalsePositive;
        
        /**
         * 分析依据
         */
        private String reasoning;
        
        /**
         * 修复建议
         */
        private String fixSuggestion;
    }
}
