package com.redtourism.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.io.Serializable;
import java.util.Date;

@Data
@TableName("food_store")
public class FoodStore implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    private String location;
    private String category;
    private String hygieneLevel;
    private String phone;
    private String coverImage;
    private Double longitude;
    private Double latitude;
    /** 是否暂停接单：0 正常，1 暂停（忙碌时段） */
    private Integer orderPaused;
    /** 暂停接单原因 */
    private String pauseReason;
    /** 预计恢复接单时间 */
    private Date resumeTime;
    /** 暂停接单操作时间 */
    private Date pauseTime;
    @TableField(fill = FieldFill.INSERT)
    private Date createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Date updateTime;
}
