package com.redtourism.common;

/**
 * 订单状态。
 * 通用状态：PENDING 待支付 / PAID 已支付（酒店、景点等非自取订单）
 *           CANCELLED 已取消 / REFUNDED 已退款 / COMPLETED 已完成
 * 美食自取排队状态：QUEUING 排队中 -> MAKING 制作中 -> READY 待取餐 -> COMPLETED 已完成，
 *           进行中任一环节可 VOID 已作废（注明原因）。
 */
public class OrderStatus {
    public static final String PENDING = "PENDING";
    public static final String PAID = "PAID";
    public static final String QUEUING = "QUEUING";
    public static final String MAKING = "MAKING";
    public static final String READY = "READY";
    public static final String COMPLETED = "COMPLETED";
    public static final String CANCELLED = "CANCELLED";
    public static final String REFUNDED = "REFUNDED";
    public static final String VOID = "VOID";

    /** 自取流程中已支付、仍可继续流转/退款的状态 */
    public static boolean isFoodActive(String status) {
        return QUEUING.equals(status) || MAKING.equals(status) || READY.equals(status);
    }
}
