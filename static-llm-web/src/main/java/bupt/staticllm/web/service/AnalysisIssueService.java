package bupt.staticllm.web.service;

import bupt.staticllm.common.model.AnalysisIssue;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

public interface AnalysisIssueService extends IService<AnalysisIssue> {
    /**
     * 根据任务ID查询问题列表
     * @param taskId 任务ID
     * @return 问题列表
     */
    List<AnalysisIssue> getByTaskId(Long taskId);
}
