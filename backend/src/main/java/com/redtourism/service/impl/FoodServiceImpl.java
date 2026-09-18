package com.redtourism.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.redtourism.entity.Food;
import com.redtourism.entity.FoodStore;
import com.redtourism.mapper.FoodMapper;
import com.redtourism.mapper.FoodStoreMapper;
import com.redtourism.service.FoodService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
public class FoodServiceImpl extends ServiceImpl<FoodMapper, Food> implements FoodService {

    @Autowired
    private FoodStoreMapper foodStoreMapper;

    @Override
    public IPage<Food> listFoods(int page, int size, String category, String keyword, String orderBy) {
        return listFoods(page, size, category, keyword, orderBy, null);
    }

    @Override
    public IPage<Food> listFoods(int page, int size, String category, String keyword, String orderBy, Long storeId) {
        LambdaQueryWrapper<Food> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(category)) {
            wrapper.eq(Food::getCategory, category);
        }
        if (storeId != null) {
            wrapper.eq(Food::getStoreId, storeId);
        }
        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w.like(Food::getName, keyword)
                    .or().like(Food::getDescription, keyword));
        }
        // 暂停接单只影响下单入口，不影响价格排序等列表逻辑
        if ("price".equals(orderBy)) {
            wrapper.orderByAsc(Food::getPrice);
        } else {
            wrapper.orderByDesc(Food::getCreateTime);
        }
        return page(new Page<>(page, size), wrapper);
    }

    @Override
    public Food getDetail(Long id) {
        return getById(id);
    }

    @Override
    public List<FoodStore> listStores(String keyword) {
        LambdaQueryWrapper<FoodStore> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            wrapper.like(FoodStore::getName, keyword)
                    .or().like(FoodStore::getLocation, keyword);
        }
        wrapper.orderByDesc(FoodStore::getCreateTime);
        return foodStoreMapper.selectList(wrapper);
    }

    @Override
    public FoodStore getStoreDetail(Long id) {
        return foodStoreMapper.selectById(id);
    }

    @Override
    public boolean saveStore(FoodStore store) {
        if (store.getId() != null) {
            return foodStoreMapper.updateById(store) > 0;
        }
        return foodStoreMapper.insert(store) > 0;
    }

    @Override
    public boolean deleteStore(Long id) {
        return foodStoreMapper.deleteById(id) > 0;
    }

    @Override
    public boolean pauseOrderTaking(Long storeId, String reason, String resumeTime) {
        FoodStore store = foodStoreMapper.selectById(storeId);
        if (store == null) {
            throw new RuntimeException("门店不存在");
        }
        if (!StringUtils.hasText(resumeTime)) {
            throw new RuntimeException("暂停接单必须注明恢复时段");
        }
        store.setOrderPaused(1);
        store.setPauseReason(StringUtils.hasText(reason) ? reason : "门店繁忙，暂停接单");
        store.setResumeTime(resumeTime);
        return foodStoreMapper.updateById(store) > 0;
    }

    @Override
    public boolean resumeOrderTaking(Long storeId) {
        FoodStore store = foodStoreMapper.selectById(storeId);
        if (store == null) {
            throw new RuntimeException("门店不存在");
        }
        store.setOrderPaused(0);
        store.setPauseReason(null);
        store.setResumeTime(null);
        return foodStoreMapper.updateById(store) > 0;
    }

    @Override
    public boolean toggleServing(Long storeId, boolean paused) {
        FoodStore store = foodStoreMapper.selectById(storeId);
        if (store == null) {
            throw new RuntimeException("门店不存在");
        }
        store.setServingPaused(paused ? 1 : 0);
        return foodStoreMapper.updateById(store) > 0;
    }
}
