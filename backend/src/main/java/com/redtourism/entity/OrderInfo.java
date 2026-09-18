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
    /** 自取门店ID（FOOD 订单） */
    private Long storeId;
    /** 自取排队号（按门店当日递增） */
    private Integer queueNo;
    /** 排队号所属日期 */
    private Date queueDate;
    /** 门店接单时间（进入制作中） */
    private Date acceptTime;
    /** 出餐叫号时间（进入待取餐） */
    private Date readyTime;
    /** 用户确认取餐时间（已完成） */
    private Date completeTime;
    /** 作废时间 */
    private Date voidTime;
    /** 作废原因（如超时未取） */
    private String voidReason;
    @TableField(fill = FieldFill.INSERT)
    private Date createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Date updateTime;
}
