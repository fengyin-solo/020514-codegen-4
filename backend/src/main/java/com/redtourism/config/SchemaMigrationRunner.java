package com.redtourism.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.List;
import java.util.Map;

/**
 * 自取排队功能增量迁移：
 * schema.sql 仅在数据库首次初始化时执行，对已存在的 MySQL 数据卷，
 * 在应用启动时幂等地补齐新字段与状态流转表。
 */
@Component
@Order(0)
public class SchemaMigrationRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SchemaMigrationRunner.class);

    @Resource
    private JdbcTemplate jdbcTemplate;

    @Override
    public void run(ApplicationArguments args) {
        addColumnIfMissing("order_info", "store_id",
                "ALTER TABLE order_info ADD COLUMN store_id BIGINT COMMENT '自取订单所属门店' AFTER check_out_date");
        addColumnIfMissing("order_info", "queue_no",
                "ALTER TABLE order_info ADD COLUMN queue_no INT COMMENT '自取排队叫号' AFTER store_id");
        addColumnIfMissing("order_info", "accept_time",
                "ALTER TABLE order_info ADD COLUMN accept_time DATETIME COMMENT '门店接单时间' AFTER queue_no");
        addColumnIfMissing("order_info", "ready_time",
                "ALTER TABLE order_info ADD COLUMN ready_time DATETIME COMMENT '出餐/叫号时间' AFTER accept_time");
        addColumnIfMissing("order_info", "complete_time",
                "ALTER TABLE order_info ADD COLUMN complete_time DATETIME COMMENT '确认取餐时间' AFTER ready_time");
        addColumnIfMissing("order_info", "void_time",
                "ALTER TABLE order_info ADD COLUMN void_time DATETIME COMMENT '作废时间' AFTER complete_time");
        addColumnIfMissing("order_info", "void_reason",
                "ALTER TABLE order_info ADD COLUMN void_reason VARCHAR(255) COMMENT '作废原因' AFTER void_time");
        addColumnIfMissing("order_info", "refund_time",
                "ALTER TABLE order_info ADD COLUMN refund_time DATETIME COMMENT '退款时间' AFTER void_reason");
        addIndexIfMissing("order_info", "idx_queue",
                "ALTER TABLE order_info ADD INDEX idx_queue (order_type, target_id, status, queue_no)");

        addColumnIfMissing("food_store", "order_paused",
                "ALTER TABLE food_store ADD COLUMN order_paused INT DEFAULT 0 COMMENT '是否暂停接单：0正常 1暂停'");
        addColumnIfMissing("food_store", "pause_reason",
                "ALTER TABLE food_store ADD COLUMN pause_reason VARCHAR(255) COMMENT '暂停接单原因'");
        addColumnIfMissing("food_store", "resume_time",
                "ALTER TABLE food_store ADD COLUMN resume_time DATETIME COMMENT '预计恢复接单时间'");
        addColumnIfMissing("food_store", "pause_time",
                "ALTER TABLE food_store ADD COLUMN pause_time DATETIME COMMENT '暂停接单操作时间'");

        jdbcTemplate.execute(
                "CREATE TABLE IF NOT EXISTS order_status_log (" +
                "  id BIGINT AUTO_INCREMENT PRIMARY KEY," +
                "  order_id BIGINT NOT NULL," +
                "  from_status VARCHAR(20)," +
                "  to_status VARCHAR(20) NOT NULL," +
                "  remark VARCHAR(500)," +
                "  operator VARCHAR(50)," +
                "  create_time DATETIME DEFAULT CURRENT_TIMESTAMP," +
                "  INDEX idx_order (order_id)" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
    }

    private void addColumnIfMissing(String table, String column, String ddl) {
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                    "SELECT COUNT(*) AS c FROM information_schema.COLUMNS " +
                    "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND COLUMN_NAME = ?",
                    table, column);
            long count = ((Number) rows.get(0).get("c")).longValue();
            if (count == 0) {
                jdbcTemplate.execute(ddl);
                log.info("[Migration] {}.{} added", table, column);
            }
        } catch (Exception e) {
            log.warn("[Migration] skip {}.{}: {}", table, column, e.getMessage());
        }
    }

    private void addIndexIfMissing(String table, String indexName, String ddl) {
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                    "SELECT COUNT(*) AS c FROM information_schema.STATISTICS " +
                    "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND INDEX_NAME = ?",
                    table, indexName);
            long count = ((Number) rows.get(0).get("c")).longValue();
            if (count == 0) {
                jdbcTemplate.execute(ddl);
                log.info("[Migration] index {} on {} added", indexName, table);
            }
        } catch (Exception e) {
            log.warn("[Migration] skip index {} on {}: {}", indexName, table, e.getMessage());
        }
    }
}
