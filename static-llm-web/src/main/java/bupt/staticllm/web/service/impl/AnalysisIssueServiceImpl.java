package bupt.staticllm.web.service.impl;

import bupt.staticllm.common.model.AnalysisIssue;
import bupt.staticllm.web.mapper.AnalysisIssueMapper;
import bupt.staticllm.web.service.AnalysisIssueService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AnalysisIssueServiceImpl extends ServiceImpl<AnalysisIssueMapper, AnalysisIssue> implements AnalysisIssueService {

    @Override
    public List<AnalysisIssue> getByTaskId(Long taskId) {
        return this.list(new LambdaQueryWrapper<AnalysisIssue>()
                .eq(AnalysisIssue::getTaskId, taskId)
                .orderByDesc(AnalysisIssue::getSeverity));
    }
}
