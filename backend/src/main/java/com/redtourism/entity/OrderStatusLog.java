package com.redtourism.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.io.Serializable;
import java.util.Date;

/**
 * 自取订单状态流转记录：
 * 支付完成 -> QUEUING（排队中）-> PREPARING（制作中）-> READY（待取餐/已叫号）
 * -> COMPLETED（已完成，用户确认取餐）/ VOID（已作废，超时未取等，注明原因）
 */
@Data
@TableName("order_status_log")
public class OrderStatusLog implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long orderId;
    private String fromStatus;
    private String toStatus;
    private String remark;
    /** 操作方：USER / ADMIN / SYSTEM */
    private String operator;
    @TableField(fill = FieldFill.INSERT)
    private Date createTime;
}
