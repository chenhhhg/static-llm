package bupt.staticllm.web.service.impl;

import bupt.staticllm.common.model.AnalysisCache;
import bupt.staticllm.web.mapper.AnalysisCacheMapper;
import bupt.staticllm.web.service.AnalysisCacheService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

@Service
public class AnalysisCacheServiceImpl extends ServiceImpl<AnalysisCacheMapper, AnalysisCache> implements AnalysisCacheService {
}
