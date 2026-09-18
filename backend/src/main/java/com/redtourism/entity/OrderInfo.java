package com.redtourism.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

@Data
@TableName("order_info")
public class OrderInfo implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String orderType;
    private Long targetId;
    private String orderNo;
    private BigDecimal amount;
    private String status;
    private String payMethod;
    private Date payTime;
    private String targetName;
    private Integer quantity;
    private Date checkInDate;
    private Date checkOutDate;
    /** 自取订单所属门店（美食订单支付时落库，排队按门店编排） */
    private Long storeId;
    /** 自取排队叫号（美食订单支付后分配，按门店递增） */
    private Integer queueNo;
    /** 门店接单时间（排队中 -> 制作中） */
    private Date acceptTime;
    /** 出餐时间（制作中 -> 待取餐，即叫号时间） */
    private Date readyTime;
    /** 用户确认取餐时间（待取餐 -> 已完成） */
    private Date completeTime;
    /** 作废时间（超时未取 / 门店临时停止出餐等） */
    private Date voidTime;
    /** 作废原因 */
    private String voidReason;
    /** 退款时间 */
    private Date refundTime;
    @TableField(fill = FieldFill.INSERT)
    private Date createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Date updateTime;

    /** 非数据库字段：当前排队位置（叫号后用户在门店列表查看） */
    @TableField(exist = false)
    private Integer queuePosition;
    /** 非数据库字段：门店名称 */
    @TableField(exist = false)
    private String storeName;
    /** 非数据库字段：下单用户名（管理端展示） */
    @TableField(exist = false)
    private String username;
}
