package com.redtourism.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.redtourism.entity.Food;
import com.redtourism.entity.FoodStore;
import java.util.List;
import java.util.Map;

public interface FoodService extends IService<Food> {
    IPage<Food> listFoods(int page, int size, String category, String keyword, String orderBy);
    Food getDetail(Long id);
    List<FoodStore> listStores(String keyword);
    FoodStore getStoreDetail(Long id);
    boolean saveStore(FoodStore store);
    boolean deleteStore(Long id);

    /** 门店暂停接单（注明原因与预计恢复时段） */
    boolean pauseStore(Long storeId, String reason, java.util.Date resumeTime);
    /** 门店恢复接单 */
    boolean resumeStore(Long storeId);
    /** 门店实时排队概览：排队/制作/待取餐数量及当前叫号 */
    Map<String, Object> storeQueueInfo(Long storeId, Long userId);
}
