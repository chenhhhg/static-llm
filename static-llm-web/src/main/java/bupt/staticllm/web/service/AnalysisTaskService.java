package bupt.staticllm.web.service;

import bupt.staticllm.common.model.AnalysisTask;
import bupt.staticllm.web.model.request.FileAnalysisRequest;
import com.baomidou.mybatisplus.extension.service.IService;

public interface AnalysisTaskService extends IService<AnalysisTask> {
    
    /**
     * 创建并异步执行分析任务
     * @param request 任务请求参数
     * @return 创建的任务ID
     */
    Long submitTask(FileAnalysisRequest request);

    /**
     * 取消任务
     * @param taskId 任务ID
     * @return 是否成功
     */
    boolean cancelTask(Long taskId);

    /**
     * 异步处理任务逻辑
     * @param task 任务实体
     */
    void processTaskAsync(AnalysisTask task);
}
