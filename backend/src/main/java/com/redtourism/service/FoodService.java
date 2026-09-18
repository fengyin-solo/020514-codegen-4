package com.redtourism.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.redtourism.entity.Food;
import com.redtourism.entity.FoodStore;
import java.util.List;

public interface FoodService extends IService<Food> {
    IPage<Food> listFoods(int page, int size, String category, String keyword, String orderBy);
    /** 美食列表可按门店过滤；门店暂停接单不影响列表、价格排序与卫生等级展示 */
    IPage<Food> listFoods(int page, int size, String category, String keyword, String orderBy, Long storeId);
    Food getDetail(Long id);
    List<FoodStore> listStores(String keyword);
    FoodStore getStoreDetail(Long id);
    boolean saveStore(FoodStore store);
    boolean deleteStore(Long id);

    /** 门店忙时暂停接单，需注明恢复时段与原因 */
    boolean pauseOrderTaking(Long storeId, String reason, String resumeTime);
    /** 恢复接单 */
    boolean resumeOrderTaking(Long storeId);
    /** 临时停止出餐（已支付金额保持可退）；serving=true 停止，false 恢复 */
    boolean toggleServing(Long storeId, boolean paused);
}
