package bupt.staticllm.web.service.impl;

import bupt.staticllm.common.model.EvaluationDetail;
import bupt.staticllm.web.mapper.EvaluationDetailMapper;
import bupt.staticllm.web.service.EvaluationDetailService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

@Service
public class EvaluationDetailServiceImpl extends ServiceImpl<EvaluationDetailMapper, EvaluationDetail> implements EvaluationDetailService {
}
