-- MetadataIT 初始化脚本：覆盖特殊字符表名、视图、注释、索引
CREATE DATABASE IF NOT EXISTS `joyo_it` DEFAULT CHARACTER SET utf8mb4;
USE `joyo_it`;

CREATE TABLE `order item` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  `order_no` VARCHAR(64) NOT NULL COMMENT '订单号',
  `amount` DECIMAL(12,2) NULL DEFAULT NULL COMMENT '金额',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_order_no` (`order_no`),
  KEY `idx_amount` (`amount`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='订单表';

CREATE OR REPLACE VIEW `v_user` AS SELECT 1 AS dummy;
