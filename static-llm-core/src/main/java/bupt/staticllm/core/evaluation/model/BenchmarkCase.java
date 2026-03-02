package bupt.staticllm.core.evaluation.model;

import lombok.Data;

@Data
public class BenchmarkCase {
    private String filename;
    private String category;
    private String cweId;
    private boolean isRealVulnerability;
    private Integer startLine;
    private Integer endLine;
}
