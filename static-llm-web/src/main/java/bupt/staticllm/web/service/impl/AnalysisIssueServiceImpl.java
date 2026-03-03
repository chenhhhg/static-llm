package bupt.staticllm.web.service.impl;

import bupt.staticllm.common.model.AnalysisIssue;
import bupt.staticllm.web.mapper.AnalysisIssueMapper;
import bupt.staticllm.web.service.AnalysisIssueService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class AnalysisIssueServiceImpl extends ServiceImpl<AnalysisIssueMapper, AnalysisIssue> implements AnalysisIssueService {

    @Override
    public IPage<AnalysisIssue> getByTaskId(Long taskId, int page, int size, String severity, String keyword, Boolean isFalsePositive) {
        LambdaQueryWrapper<AnalysisIssue> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AnalysisIssue::getTaskId, taskId);

        if (StringUtils.hasText(severity)) {
            wrapper.eq(AnalysisIssue::getSeverity, severity);
        }

        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w
                .like(AnalysisIssue::getRuleId, keyword)
                .or().like(AnalysisIssue::getFilePath, keyword)
                .or().like(AnalysisIssue::getMessage, keyword)
            );
        }

        if (isFalsePositive != null) {
            wrapper.eq(AnalysisIssue::getIsFalsePositive, isFalsePositive);
        }

        wrapper.orderByDesc(AnalysisIssue::getCreatedTime);

        return this.page(new Page<>(page, size), wrapper);
    }
}
