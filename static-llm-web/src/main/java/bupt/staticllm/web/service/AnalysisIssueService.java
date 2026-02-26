package bupt.staticllm.web.service;

import bupt.staticllm.common.model.AnalysisIssue;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;

public interface AnalysisIssueService extends IService<AnalysisIssue> {
    /**
     * 根据任务ID查询问题列表（分页）
     * @param taskId 任务ID
     * @param page 页码
     * @param size 每页大小
     * @return 分页结果
     */
    IPage<AnalysisIssue> getByTaskId(Long taskId, int page, int size);
}
