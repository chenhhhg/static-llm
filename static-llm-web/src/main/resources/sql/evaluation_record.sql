-- 评估记录主表：存储每次评估的汇总结果
CREATE TABLE IF NOT EXISTS `evaluation_record` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `task_id` bigint(20) NOT NULL COMMENT '关联的分析任务ID',
  `benchmark_type` varchar(50) DEFAULT NULL COMMENT '基准测试类型(如OWASP-1.2)',
  `benchmark_path` varchar(500) DEFAULT NULL COMMENT '基准测试文件路径',
  `eval_mode` varchar(20) NOT NULL COMMENT '评估模式: FULL=全量, AI_ONLY=仅AI分析, AI_MISJUDGMENT=AI误判分析',
  `tp_count` int(11) DEFAULT 0 COMMENT '真阳性数量',
  `fp_count` int(11) DEFAULT 0 COMMENT '假阳性数量',
  `fn_count` int(11) DEFAULT 0 COMMENT '假阴性数量',
  `tn_count` int(11) DEFAULT 0 COMMENT '真阴性数量',
  `precision_rate` double DEFAULT NULL COMMENT '精确率',
  `recall_rate` double DEFAULT NULL COMMENT '召回率',
  `f1_score` double DEFAULT NULL COMMENT 'F1分数',
  `benchmark_score` double DEFAULT NULL COMMENT 'Benchmark分数',
  `total_analyzed` int(11) DEFAULT NULL COMMENT 'AI误判分析-已分析总数',
  `matched_count` int(11) DEFAULT NULL COMMENT 'AI误判分析-匹配Benchmark的数量',
  `correct_count` int(11) DEFAULT NULL COMMENT 'AI误判分析-AI判断正确数量',
  `wrong_count` int(11) DEFAULT NULL COMMENT 'AI误判分析-AI判断错误数量',
  `accuracy` double DEFAULT NULL COMMENT 'AI误判分析-AI正确率',
  `created_time` datetime DEFAULT NULL COMMENT '创建时间',
  `updated_time` datetime DEFAULT NULL COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_task_id` (`task_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='评估记录表';

-- 评估详情表：存储每次评估中的逐条对比明细
CREATE TABLE IF NOT EXISTS `evaluation_detail` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `record_id` bigint(20) NOT NULL COMMENT '关联的评估记录ID',
  `issue_id` bigint(20) DEFAULT NULL COMMENT '关联的issue ID',
  `file_path` varchar(500) DEFAULT NULL COMMENT '文件路径',
  `rule_id` varchar(100) DEFAULT NULL COMMENT '规则ID',
  `normalized_category` varchar(50) DEFAULT NULL COMMENT '归一化后的漏洞类别',
  `benchmark_test_name` varchar(100) DEFAULT NULL COMMENT 'Benchmark测试用例名',
  `benchmark_category` varchar(50) DEFAULT NULL COMMENT 'Benchmark类别',
  `benchmark_is_real` tinyint(1) DEFAULT NULL COMMENT 'Benchmark标准答案:是否真实漏洞',
  `match_status` varchar(10) DEFAULT NULL COMMENT '匹配状态: TP/FP/FN/TN',
  `ai_is_false_positive` tinyint(1) DEFAULT NULL COMMENT 'AI判定:是否误报',
  `ai_reasoning` text COMMENT 'AI分析理由',
  `ai_correct` tinyint(1) DEFAULT NULL COMMENT 'AI判定是否正确',
  `error_type` varchar(100) DEFAULT NULL COMMENT '错误类型描述',
  `detail_info` text COMMENT '附加详情信息',
  `created_time` datetime DEFAULT NULL COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_record_id` (`record_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='评估详情表';
