package com.redtourism.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.redtourism.entity.Food;
import com.redtourism.entity.FoodStore;
import com.redtourism.entity.OrderInfo;
import com.redtourism.mapper.FoodMapper;
import com.redtourism.mapper.FoodStoreMapper;
import com.redtourism.mapper.OrderInfoMapper;
import com.redtourism.service.FoodService;
import com.redtourism.service.OrderService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.*;

@Service
public class FoodServiceImpl extends ServiceImpl<FoodMapper, Food> implements FoodService {

    @Autowired
    private FoodStoreMapper foodStoreMapper;
    @Autowired
    private OrderInfoMapper orderInfoMapper;
    @Autowired
    private OrderService orderService;

    @Override
    public IPage<Food> listFoods(int page, int size, String category, String keyword, String orderBy) {
        LambdaQueryWrapper<Food> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(category)) {
            wrapper.eq(Food::getCategory, category);
        }
        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w.like(Food::getName, keyword)
                    .or().like(Food::getDescription, keyword));
        }
        // 价格排序不受门店暂停接单影响
        if ("price".equals(orderBy)) {
            wrapper.orderByAsc(Food::getPrice);
        } else {
            wrapper.orderByDesc(Food::getCreateTime);
        }
        IPage<Food> result = page(new Page<>(page, size), wrapper);
        // 附带门店接单状态：暂停期间餐品卡片下单入口置灰（卫生等级、价格排序均不变）
        Map<Long, FoodStore> storeCache = new HashMap<>();
        for (Food f : result.getRecords()) {
            if (f.getStoreId() == null) continue;
            FoodStore store = storeCache.computeIfAbsent(f.getStoreId(), foodStoreMapper::selectById);
            if (store != null) {
                f.setStoreName(store.getName());
                f.setStoreOrderPaused(store.getOrderPaused() != null ? store.getOrderPaused() : 0);
                f.setPauseReason(store.getPauseReason());
                f.setResumeTime(store.getResumeTime());
                f.setStoreHygieneLevel(store.getHygieneLevel());
            }
        }
        return result;
    }

    @Override
    public Food getDetail(Long id) {
        Food food = getById(id);
        if (food != null && food.getStoreId() != null) {
            FoodStore store = foodStoreMapper.selectById(food.getStoreId());
            if (store != null) {
                food.setStoreName(store.getName());
                food.setStoreOrderPaused(store.getOrderPaused() != null ? store.getOrderPaused() : 0);
                food.setPauseReason(store.getPauseReason());
                food.setResumeTime(store.getResumeTime());
                food.setStoreHygieneLevel(store.getHygieneLevel());
            }
        }
        return food;
    }

    @Override
    public List<FoodStore> listStores(String keyword) {
        LambdaQueryWrapper<FoodStore> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            wrapper.like(FoodStore::getName, keyword)
                    .or().like(FoodStore::getLocation, keyword);
        }
        // 暂停的门店仍在列表中展示（卫生等级等信息不受影响），仅接单入口置灰
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
            // 仅更新门店资料字段，不改变暂停接单状态（暂停/恢复由专门接口处理）
            FoodStore exist = foodStoreMapper.selectById(store.getId());
            if (exist == null) {
                store.setOrderPaused(0);
                return foodStoreMapper.insert(store) > 0;
            }
            exist.setName(store.getName());
            exist.setLocation(store.getLocation());
            exist.setCategory(store.getCategory());
            exist.setHygieneLevel(store.getHygieneLevel());
            exist.setPhone(store.getPhone());
            exist.setCoverImage(store.getCoverImage());
            exist.setLongitude(store.getLongitude());
            exist.setLatitude(store.getLatitude());
            return foodStoreMapper.updateById(exist) > 0;
        }
        store.setOrderPaused(0);
        return foodStoreMapper.insert(store) > 0;
    }

    @Override
    public boolean deleteStore(Long id) {
        return foodStoreMapper.deleteById(id) > 0;
    }

    @Override
    public boolean pauseStore(Long storeId, String reason, Date resumeTime) {
        FoodStore store = foodStoreMapper.selectById(storeId);
        if (store == null) throw new RuntimeException("门店不存在");
        if (!StringUtils.hasText(reason)) throw new RuntimeException("暂停接单必须注明原因");
        store.setOrderPaused(1);
        store.setPauseReason(reason);
        store.setResumeTime(resumeTime);
        store.setPauseTime(new Date());
        return foodStoreMapper.updateById(store) > 0;
    }

    @Override
    public boolean resumeStore(Long storeId) {
        FoodStore store = foodStoreMapper.selectById(storeId);
        if (store == null) throw new RuntimeException("门店不存在");
        store.setOrderPaused(0);
        store.setPauseReason(null);
        store.setResumeTime(null);
        store.setPauseTime(null);
        return foodStoreMapper.updateById(store) > 0;
    }

    @Override
    public Map<String, Object> storeQueueInfo(Long storeId, Long userId) {
        Map<String, Object> info = new HashMap<>();
        java.util.Calendar c = java.util.Calendar.getInstance();
        c.set(java.util.Calendar.HOUR_OF_DAY, 0);
        c.set(java.util.Calendar.MINUTE, 0);
        c.set(java.util.Calendar.SECOND, 0);
        c.set(java.util.Calendar.MILLISECOND, 0);
        Date dayStart = c.getTime();
        long queuing = countStoreOrders(storeId, OrderServiceImpl.QUEUING, dayStart);
        long preparing = countStoreOrders(storeId, OrderServiceImpl.PREPARING, dayStart);
        long ready = countStoreOrders(storeId, OrderServiceImpl.READY, dayStart);
        info.put("queuingCount", queuing);
        info.put("preparingCount", preparing);
        info.put("readyCount", ready);
        info.put("waitingCount", queuing + preparing);
        // 当前最新叫号（当日最近出餐的订单）
        OrderInfo latest = orderInfoMapper.selectOne(new LambdaQueryWrapper<OrderInfo>()
                .eq(OrderInfo::getStoreId, storeId)
                .ge(OrderInfo::getCreateTime, dayStart)
                .in(OrderInfo::getStatus, OrderServiceImpl.READY, OrderServiceImpl.COMPLETED)
                .isNotNull(OrderInfo::getReadyTime)
                .orderByDesc(OrderInfo::getReadyTime)
                .orderByDesc(OrderInfo::getQueueNo)
                .last("LIMIT 1"));
        info.put("currentCallingNo", latest != null ? latest.getQueueNo() : null);
        // 当前登录用户在此门店的在等/待取订单
        if (userId != null) {
            OrderInfo mine = orderInfoMapper.selectOne(new LambdaQueryWrapper<OrderInfo>()
                    .eq(OrderInfo::getStoreId, storeId)
                    .eq(OrderInfo::getUserId, userId)
                    .in(OrderInfo::getStatus, OrderServiceImpl.QUEUING,
                            OrderServiceImpl.PREPARING, OrderServiceImpl.READY)
                    .orderByAsc(OrderInfo::getQueueNo).last("LIMIT 1"));
            if (mine != null) {
                Map<String, Object> my = new HashMap<>();
                my.put("orderId", mine.getId());
                my.put("queueNo", mine.getQueueNo());
                my.put("status", mine.getStatus());
                my.put("position", orderService.getQueuePosition(storeId, mine.getId()));
                my.put("readyTime", mine.getReadyTime());
                info.put("myQueue", my);
            }
        }
        return info;
    }

    private long countStoreOrders(Long storeId, String status, Date dayStart) {
        Long c = orderInfoMapper.selectCount(new LambdaQueryWrapper<OrderInfo>()
                .eq(OrderInfo::getStoreId, storeId)
                .ge(OrderInfo::getCreateTime, dayStart)
                .eq(OrderInfo::getStatus, status));
        return c == null ? 0 : c;
    }
}
