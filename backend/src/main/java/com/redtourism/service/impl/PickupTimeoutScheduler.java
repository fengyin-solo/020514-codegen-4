package com.redtourism.service.impl;

import com.redtourism.service.OrderService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 自取订单超时扫描：待取餐（已叫号）超过规定时间未确认取餐的订单自动作废。
 */
@Component
public class PickupTimeoutScheduler {

    private static final Logger log = LoggerFactory.getLogger(PickupTimeoutScheduler.class);

    @Autowired
    private OrderService orderService;

    /** 每分钟扫描一次 */
    @Scheduled(fixedDelay = 60_000L, initialDelay = 30_000L)
    public void scanTimeoutOrders() {
        try {
            orderService.autoVoidTimeoutOrders();
        } catch (Exception e) {
            log.warn("[Queue] 超时扫描异常: {}", e.getMessage());
        }
    }
}
