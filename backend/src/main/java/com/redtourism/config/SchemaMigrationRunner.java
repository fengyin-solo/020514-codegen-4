package com.redtourism.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 幂等数据库补丁：schema.sql 仅在数据库首次初始化时执行，
 * 对已存在的数据卷，启动时检测并补齐自取排队功能所需的列和流转日志表。
 */
@Component
@Order(10)
public class SchemaMigrationRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SchemaMigrationRunner.class);

    @Resource
    private JdbcTemplate jdbcTemplate;

    /** 表 -> (列名 -> DDL 片段) */
    private static final Map<String, Map<String, String>> NEW_COLUMNS = new LinkedHashMap<>();

    static {
        Map<String, String> storeCols = new LinkedHashMap<>();
        storeCols.put("order_paused", "ALTER TABLE food_store ADD COLUMN order_paused TINYINT DEFAULT 0 COMMENT '是否暂停接单：0正常 1暂停'");
        storeCols.put("pause_reason", "ALTER TABLE food_store ADD COLUMN pause_reason VARCHAR(255) COMMENT '暂停接单原因'");
        storeCols.put("resume_time", "ALTER TABLE food_store ADD COLUMN resume_time VARCHAR(100) COMMENT '预计恢复接单时段'");
        storeCols.put("serving_paused", "ALTER TABLE food_store ADD COLUMN serving_paused TINYINT DEFAULT 0 COMMENT '是否临时停止出餐：0正常 1停止'");
        NEW_COLUMNS.put("food_store", storeCols);

        Map<String, String> orderCols = new LinkedHashMap<>();
        orderCols.put("store_id", "ALTER TABLE order_info ADD COLUMN store_id BIGINT COMMENT '自取门店ID（FOOD订单）'");
        orderCols.put("queue_no", "ALTER TABLE order_info ADD COLUMN queue_no INT COMMENT '自取排队号（按门店当日递增）'");
        orderCols.put("queue_date", "ALTER TABLE order_info ADD COLUMN queue_date DATE COMMENT '排队号所属日期'");
        orderCols.put("accept_time", "ALTER TABLE order_info ADD COLUMN accept_time DATETIME COMMENT '门店接单时间（进入制作中）'");
        orderCols.put("ready_time", "ALTER TABLE order_info ADD COLUMN ready_time DATETIME COMMENT '出餐叫号时间（进入待取餐）'");
        orderCols.put("complete_time", "ALTER TABLE order_info ADD COLUMN complete_time DATETIME COMMENT '用户确认取餐时间'");
        orderCols.put("void_time", "ALTER TABLE order_info ADD COLUMN void_time DATETIME COMMENT '作废时间'");
        orderCols.put("void_reason", "ALTER TABLE order_info ADD COLUMN void_reason VARCHAR(255) COMMENT '作废原因'");
        NEW_COLUMNS.put("order_info", orderCols);
    }

    private static final String CREATE_LOG_TABLE =
            "CREATE TABLE IF NOT EXISTS order_status_log (" +
            "id BIGINT AUTO_INCREMENT PRIMARY KEY, " +
            "order_id BIGINT NOT NULL, " +
            "from_status VARCHAR(20), " +
            "to_status VARCHAR(20) NOT NULL, " +
            "change_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, " +
            "remark VARCHAR(255), " +
            "operator VARCHAR(50), " +
            "INDEX idx_order (order_id)" +
            ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4";

    @Override
    public void run(ApplicationArguments args) {
        NEW_COLUMNS.forEach((table, columns) -> columns.forEach((column, ddl) -> {
            if (!columnExists(table, column)) {
                log.info("Migration: adding column {}.{}", table, column);
                jdbcTemplate.execute(ddl);
            }
        }));
        jdbcTemplate.execute(CREATE_LOG_TABLE);
    }

    private boolean columnExists(String table, String column) {
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                    "SELECT COLUMN_NAME FROM information_schema.COLUMNS " +
                    "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND COLUMN_NAME = ?",
                    table, column);
            return !rows.isEmpty();
        } catch (Exception e) {
            log.warn("Check column existence failed for {}.{}: {}", table, column, e.getMessage());
            return true; // 查询失败时不执行 DDL，避免影响启动
        }
    }
}
