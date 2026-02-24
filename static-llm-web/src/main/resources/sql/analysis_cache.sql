CREATE TABLE IF NOT EXISTS `analysis_cache` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `file_md5` varchar(64) NOT NULL COMMENT 'Jar包MD5',
  `report_path` varchar(512) NOT NULL COMMENT 'SpotBugs报告文件路径',
  `created_time` datetime DEFAULT NULL,
  `updated_time` datetime DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_file_md5` (`file_md5`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='静态分析结果缓存表';
