package com.redtourism.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

@Data
@TableName("food")
public class Food implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    private String description;
    private String category;
    private BigDecimal price;
    private String coverImage;
    private Long storeId;
    @TableField(fill = FieldFill.INSERT)
    private Date createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Date updateTime;

    /** 非数据库字段：所属门店名称 */
    @TableField(exist = false)
    private String storeName;
    /** 非数据库字段：门店卫生等级 */
    @TableField(exist = false)
    private String storeHygieneLevel;
    /** 非数据库字段：门店是否暂停接单（0 正常 1 暂停） */
    @TableField(exist = false)
    private Integer storeOrderPaused;
    /** 非数据库字段：暂停原因 */
    @TableField(exist = false)
    private String pauseReason;
    /** 非数据库字段：预计恢复时间 */
    @TableField(exist = false)
    private Date resumeTime;
}
