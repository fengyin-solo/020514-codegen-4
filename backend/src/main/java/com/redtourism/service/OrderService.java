package com.redtourism.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.redtourism.entity.OrderInfo;
import com.redtourism.entity.OrderStatusLog;

import java.util.List;
import java.util.Map;

public interface OrderService extends IService<OrderInfo> {
    OrderInfo createOrder(OrderInfo order);
    boolean cancelOrder(Long orderId, Long userId);
    boolean payOrder(Long orderId, String payMethod, Long userId);
    boolean refundOrder(Long orderId, Long userId);
    /** 用户确认取餐：待取餐 -> 已完成 */
    boolean pickupOrder(Long orderId, Long userId);

    /** 门店接单：排队中 -> 制作中 */
    boolean acceptOrder(Long orderId);
    /** 门店出餐叫号：制作中 -> 待取餐 */
    boolean readyOrder(Long orderId);
    /** 作废订单并注明原因（如超时未取） */
    boolean voidOrder(Long orderId, String reason, String operator);
    /** 管理端/门店完成订单（非自取 PAID 单，或待取餐兜底） */
    boolean completeOrder(Long orderId);
    /** 管理端取消/退款（带门店业务校验） */
    boolean adminCancelOrder(Long orderId);
    boolean adminRefundOrder(Long orderId);

    List<OrderStatusLog> listStatusLogs(Long orderId, Long userId);
    /** 当前用户进行中的自取排队信息（门店、排队号、前方等待数） */
    List<Map<String, Object>> myQueue(Long userId);

    IPage<OrderInfo> listUserOrders(int page, int size, Long userId, String orderType, String status);
    IPage<OrderInfo> listAllOrders(int page, int size, String orderType, String status);
}
