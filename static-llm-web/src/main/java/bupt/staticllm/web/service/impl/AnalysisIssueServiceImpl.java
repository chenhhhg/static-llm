package bupt.staticllm.web.service.impl;

import bupt.staticllm.common.model.AnalysisIssue;
import bupt.staticllm.web.mapper.AnalysisIssueMapper;
import bupt.staticllm.web.service.AnalysisIssueService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

@Service
public class AnalysisIssueServiceImpl extends ServiceImpl<AnalysisIssueMapper, AnalysisIssue> implements AnalysisIssueService {

    @Override
    public IPage<AnalysisIssue> getByTaskId(Long taskId, int page, int size) {
        return this.page(new Page<>(page, size),
                new LambdaQueryWrapper<AnalysisIssue>()
                .eq(AnalysisIssue::getTaskId, taskId)
                .orderByDesc(AnalysisIssue::getSeverity));
    }
}
