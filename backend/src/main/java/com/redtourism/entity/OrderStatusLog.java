package com.redtourism.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.io.Serializable;
import java.util.Date;

/**
 * 自取订单状态流转记录：每次状态变化都保留变更时间
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
    private Date changeTime;
    private String remark;
    /** 触发者：USER / STORE / ADMIN */
    private String operator;
}
