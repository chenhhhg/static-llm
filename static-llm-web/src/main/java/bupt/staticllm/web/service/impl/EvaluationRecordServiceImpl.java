package bupt.staticllm.web.service.impl;

import bupt.staticllm.common.model.EvaluationRecord;
import bupt.staticllm.web.mapper.EvaluationRecordMapper;
import bupt.staticllm.web.service.EvaluationRecordService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

@Service
public class EvaluationRecordServiceImpl extends ServiceImpl<EvaluationRecordMapper, EvaluationRecord> implements EvaluationRecordService {
}
