package bupt.staticllm.web.controller;

import bupt.staticllm.common.model.AnalysisCache;
import bupt.staticllm.common.response.Result;
import bupt.staticllm.web.service.AnalysisCacheService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/cache")
@Tag(name = "Cache Management", description = "分析结果缓存管理接口")
public class AnalysisCacheController {

    @Autowired
    private AnalysisCacheService analysisCacheService;

    @Operation(summary = "查询所有缓存列表")
    @GetMapping("/list")
    public Result<List<AnalysisCache>> listCaches() {
        return Result.success(analysisCacheService.list());
    }

    @Operation(summary = "删除缓存")
    @DeleteMapping("/{id}")
    public Result<Void> deleteCache(@PathVariable Long id) {
        if (analysisCacheService.removeById(id)) {
            return Result.success();
        } else {
            return Result.fail("删除失败或缓存不存在");
        }
    }
}
