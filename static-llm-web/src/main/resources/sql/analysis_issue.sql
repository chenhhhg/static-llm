CREATE TABLE IF NOT EXISTS `analysis_issue` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `task_id` bigint(20) NOT NULL COMMENT '关联的任务ID',
  `tool_name` varchar(50) DEFAULT NULL COMMENT '工具名称',
  `rule_id` varchar(100) DEFAULT NULL COMMENT '规则ID',
  `severity` varchar(20) DEFAULT NULL COMMENT '严重程度',
  `file_path` varchar(500) DEFAULT NULL COMMENT '文件路径',
  `start_line` int(11) DEFAULT NULL COMMENT '起始行',
  `end_line` int(11) DEFAULT NULL COMMENT '结束行',
  `message` text COMMENT '原始报错信息',
  `code_snippet` text COMMENT '源码片段',
  `is_false_positive` tinyint(1) DEFAULT 0 COMMENT '是否误报',
  `ai_reasoning` text COMMENT 'AI分析依据',
  `ai_suggestion` text COMMENT 'AI修复建议',
  `created_time` datetime DEFAULT NULL COMMENT '创建时间',
  `updated_time` datetime DEFAULT NULL COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_task_id` (`task_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='分析后的缺陷详情表';
