package com.redtourism.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.redtourism.entity.OrderInfo;

public interface OrderService extends IService<OrderInfo> {
    OrderInfo createOrder(OrderInfo order);
    boolean cancelOrder(Long orderId, Long userId);
    boolean payOrder(Long orderId, String payMethod, Long userId);
    boolean refundOrder(Long orderId, Long userId);
    IPage<OrderInfo> listUserOrders(int page, int size, Long userId, String orderType, String status);
    IPage<OrderInfo> listAllOrders(int page, int size, String orderType, String status);
    /** 对已查询出的分页结果补充排队位置、门店名称等扩展信息 */
    IPage<OrderInfo> listAllOrdersEnriched(IPage<OrderInfo> page);

    /** 门店接单：排队中 -> 制作中 */
    boolean acceptOrder(Long orderId);
    /** 出餐叫号：制作中 -> 待取餐 */
    boolean readyOrder(Long orderId);
    /** 用户确认取餐：待取餐 -> 已完成 */
    boolean confirmPickup(Long orderId, Long userId);
    /** 作废订单（超时未取等，需注明原因），已支付金额保持可退 */
    boolean voidOrder(Long orderId, String reason, String operator);
    /** 门店临时停止出餐：将在制订单（排队中/制作中）批量作废并注明原因，金额保持可退 */
    int voidStoreActiveOrders(Long storeId, String reason, String operator);
    /** 扫描待取餐超时的订单自动作废（定时任务调用） */
    int autoVoidTimeoutOrders();
    /** 查询订单状态流转时间线 */
    java.util.List<com.redtourism.entity.OrderStatusLog> listStatusLogs(Long orderId);
    /** 查询用户在某门店的实时排队位置（前面还有多少单待叫号），无在等订单返回 null */
    Integer getQueuePosition(Long storeId, Long orderId);
}
