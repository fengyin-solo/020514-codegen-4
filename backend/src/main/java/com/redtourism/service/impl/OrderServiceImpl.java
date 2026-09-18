package com.redtourism.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.redtourism.common.Constants;
import com.redtourism.common.OrderStatus;
import com.redtourism.entity.Food;
import com.redtourism.entity.FoodStore;
import com.redtourism.entity.OrderInfo;
import com.redtourism.entity.OrderStatusLog;
import com.redtourism.mapper.FoodMapper;
import com.redtourism.mapper.FoodStoreMapper;
import com.redtourism.mapper.OrderInfoMapper;
import com.redtourism.mapper.OrderStatusLogMapper;
import com.redtourism.service.MessageService;
import com.redtourism.service.OrderService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class OrderServiceImpl extends ServiceImpl<OrderInfoMapper, OrderInfo> implements OrderService {

    @Autowired
    private FoodMapper foodMapper;
    @Autowired
    private FoodStoreMapper foodStoreMapper;
    @Autowired
    private OrderStatusLogMapper statusLogMapper;
    @Autowired
    private MessageService messageService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OrderInfo createOrder(OrderInfo order) {
        order.setOrderNo("ORD" + System.currentTimeMillis() + UUID.randomUUID().toString().substring(0, 4).toUpperCase());
        order.setStatus(OrderStatus.PENDING);
        // 美食自取单：解析所属门店，暂停接单的门店不允许下单
        if (Constants.ORDER_FOOD.equals(order.getOrderType())) {
            Long storeId = order.getStoreId();
            if (storeId == null && order.getTargetId() != null) {
                Food food = foodMapper.selectById(order.getTargetId());
                if (food != null) storeId = food.getStoreId();
            }
            if (storeId != null) {
                FoodStore store = foodStoreMapper.selectById(storeId);
                if (store != null) {
                    if (store.getOrderPaused() != null && store.getOrderPaused() == 1) {
                        String tip = "门店「" + store.getName() + "」已暂停接单";
                        if (StringUtils.hasText(store.getResumeTime())) {
                            tip += "，预计恢复时段：" + store.getResumeTime();
                        }
                        throw new RuntimeException(tip);
                    }
                    order.setStoreId(storeId);
                }
            }
        }
        save(order);
        recordLog(order.getId(), null, OrderStatus.PENDING, "订单创建", "USER");
        return order;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean cancelOrder(Long orderId, Long userId) {
        OrderInfo order = mustOwnOrder(orderId, userId);
        if (!OrderStatus.PENDING.equals(order.getStatus())) {
            throw new RuntimeException("当前订单状态不可取消");
        }
        order.setStatus(OrderStatus.CANCELLED);
        boolean ok = updateById(order);
        recordLog(orderId, OrderStatus.PENDING, OrderStatus.CANCELLED, "用户取消", "USER");
        return ok;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean payOrder(Long orderId, String payMethod, Long userId) {
        OrderInfo order = mustOwnOrder(orderId, userId);
        if (!OrderStatus.PENDING.equals(order.getStatus())) {
            throw new RuntimeException("当前订单状态不可支付");
        }
        String fromStatus = order.getStatus();
        Date now = new Date();
        order.setPayMethod(payMethod);
        order.setPayTime(now);
        if (Constants.ORDER_FOOD.equals(order.getOrderType())) {
            // 支付完成后进入排队中，按门店当日生成排队号
            order.setStatus(OrderStatus.QUEUING);
            Date today = truncateDate(now);
            order.setQueueDate(today);
            order.setQueueNo(nextQueueNo(order.getStoreId(), today));
        } else {
            order.setStatus(OrderStatus.PAID);
        }
        boolean ok = updateById(order);
        recordLog(orderId, fromStatus, order.getStatus(),
                Constants.ORDER_FOOD.equals(order.getOrderType()) ? "支付完成，进入排队中" : "支付完成", "USER");
        if (Constants.ORDER_FOOD.equals(order.getOrderType()) && order.getQueueNo() != null) {
            messageService.sendMessage(userId, "排队已确认",
                    "您的自取订单已支付成功，取餐排队号 " + order.getQueueNo() + "，请留意门店叫号。");
        }
        return ok;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean refundOrder(Long orderId, Long userId) {
        OrderInfo order = mustOwnOrder(orderId, userId);
        boolean canRefund = OrderStatus.PAID.equals(order.getStatus())
                || OrderStatus.isFoodActive(order.getStatus())
                || (OrderStatus.VOID.equals(order.getStatus()) && order.getPayTime() != null);
        if (!canRefund) {
            throw new RuntimeException("当前订单状态不可退款");
        }
        String fromStatus = order.getStatus();
        order.setStatus(OrderStatus.REFUNDED);
        boolean ok = updateById(order);
        recordLog(orderId, fromStatus, OrderStatus.REFUNDED, "用户申请退款", "USER");
        return ok;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean pickupOrder(Long orderId, Long userId) {
        OrderInfo order = mustOwnOrder(orderId, userId);
        if (!OrderStatus.READY.equals(order.getStatus())) {
            throw new RuntimeException("当前订单未到待取餐状态，无法确认取餐");
        }
        order.setStatus(OrderStatus.COMPLETED);
        order.setCompleteTime(new Date());
        boolean ok = updateById(order);
        recordLog(orderId, OrderStatus.READY, OrderStatus.COMPLETED, "用户确认取餐", "USER");
        return ok;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean acceptOrder(Long orderId) {
        OrderInfo order = getById(orderId);
        if (order == null) throw new RuntimeException("订单不存在");
        if (!OrderStatus.QUEUING.equals(order.getStatus())) {
            throw new RuntimeException("仅排队中的订单可以接单");
        }
        order.setStatus(OrderStatus.MAKING);
        order.setAcceptTime(new Date());
        boolean ok = updateById(order);
        recordLog(orderId, OrderStatus.QUEUING, OrderStatus.MAKING, "门店已接单，开始制作", "STORE");
        messageService.sendMessage(order.getUserId(), "门店已接单",
                "您的排队号 " + safeQueueNo(order) + " 正在制作中，请耐心等待。");
        return ok;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean readyOrder(Long orderId) {
        OrderInfo order = getById(orderId);
        if (order == null) throw new RuntimeException("订单不存在");
        if (!OrderStatus.MAKING.equals(order.getStatus())) {
            throw new RuntimeException("仅制作中的订单可以出餐叫号");
        }
        order.setStatus(OrderStatus.READY);
        order.setReadyTime(new Date());
        boolean ok = updateById(order);
        recordLog(orderId, OrderStatus.MAKING, OrderStatus.READY, "已出餐，叫号待取餐", "STORE");
        messageService.sendMessage(order.getUserId(), "叫号取餐提醒",
                "排队号 " + safeQueueNo(order) + " 已出餐，请尽快到店取餐。");
        return ok;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean voidOrder(Long orderId, String reason, String operator) {
        OrderInfo order = getById(orderId);
        if (order == null) throw new RuntimeException("订单不存在");
        if (!StringUtils.hasText(reason)) {
            throw new RuntimeException("作废订单必须注明原因");
        }
        if (OrderStatus.COMPLETED.equals(order.getStatus())
                || OrderStatus.REFUNDED.equals(order.getStatus())
                || OrderStatus.VOID.equals(order.getStatus())
                || OrderStatus.CANCELLED.equals(order.getStatus())) {
            throw new RuntimeException("当前订单状态不可作废");
        }
        String fromStatus = order.getStatus();
        order.setStatus(OrderStatus.VOID);
        order.setVoidTime(new Date());
        order.setVoidReason(reason);
        boolean ok = updateById(order);
        recordLog(orderId, fromStatus, OrderStatus.VOID, "订单作废：" + reason,
                StringUtils.hasText(operator) ? operator : "STORE");
        messageService.sendMessage(order.getUserId(), "订单已作废",
                "您的订单（排队号 " + safeQueueNo(order) + "）已作废，原因：" + reason
                        + "。如已支付，可在订单页申请退款。");
        return ok;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean completeOrder(Long orderId) {
        OrderInfo order = getById(orderId);
        if (order == null) throw new RuntimeException("订单不存在");
        if (Constants.ORDER_FOOD.equals(order.getOrderType())) {
            if (!OrderStatus.READY.equals(order.getStatus())) {
                throw new RuntimeException("自取订单仅待取餐状态可完成");
            }
            order.setCompleteTime(new Date());
        } else if (!OrderStatus.PAID.equals(order.getStatus())) {
            throw new RuntimeException("当前订单状态不可完成");
        }
        String fromStatus = order.getStatus();
        order.setStatus(OrderStatus.COMPLETED);
        boolean ok = updateById(order);
        recordLog(orderId, fromStatus, OrderStatus.COMPLETED, "订单完成", "ADMIN");
        return ok;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean adminCancelOrder(Long orderId) {
        OrderInfo order = getById(orderId);
        if (order == null) throw new RuntimeException("订单不存在");
        String fromStatus = order.getStatus();
        if (Constants.ORDER_FOOD.equals(order.getOrderType()) && OrderStatus.isFoodActive(fromStatus)) {
            // 自取进行中订单管理端取消等同作废
            order.setStatus(OrderStatus.VOID);
            order.setVoidTime(new Date());
            order.setVoidReason("门店/管理员取消");
            updateById(order);
            recordLog(orderId, fromStatus, OrderStatus.VOID, "订单作废：门店/管理员取消", "ADMIN");
            return true;
        }
        if (!OrderStatus.PENDING.equals(fromStatus)) {
            throw new RuntimeException("当前订单状态不可取消");
        }
        order.setStatus(OrderStatus.CANCELLED);
        boolean ok = updateById(order);
        recordLog(orderId, fromStatus, OrderStatus.CANCELLED, "管理员取消", "ADMIN");
        return ok;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean adminRefundOrder(Long orderId) {
        OrderInfo order = getById(orderId);
        if (order == null) throw new RuntimeException("订单不存在");
        boolean canRefund = OrderStatus.PAID.equals(order.getStatus())
                || OrderStatus.isFoodActive(order.getStatus())
                || (OrderStatus.VOID.equals(order.getStatus()) && order.getPayTime() != null);
        if (!canRefund) {
            throw new RuntimeException("当前订单状态不可退款");
        }
        String fromStatus = order.getStatus();
        order.setStatus(OrderStatus.REFUNDED);
        boolean ok = updateById(order);
        recordLog(orderId, fromStatus, OrderStatus.REFUNDED, "管理员退款（已支付金额原路退回）", "ADMIN");
        return ok;
    }

    @Override
    public List<OrderStatusLog> listStatusLogs(Long orderId, Long userId) {
        OrderInfo order = getById(orderId);
        if (order == null) throw new RuntimeException("订单不存在");
        if (userId != null && !order.getUserId().equals(userId)) {
            throw new RuntimeException("无权查看此订单");
        }
        return statusLogMapper.selectList(new LambdaQueryWrapper<OrderStatusLog>()
                .eq(OrderStatusLog::getOrderId, orderId)
                .orderByAsc(OrderStatusLog::getChangeTime)
                .orderByAsc(OrderStatusLog::getId));
    }

    @Override
    public List<Map<String, Object>> myQueue(Long userId) {
        List<OrderInfo> orders = list(new LambdaQueryWrapper<OrderInfo>()
                .eq(OrderInfo::getUserId, userId)
                .eq(OrderInfo::getOrderType, Constants.ORDER_FOOD)
                .in(OrderInfo::getStatus, OrderStatus.QUEUING, OrderStatus.MAKING, OrderStatus.READY)
                .orderByAsc(OrderInfo::getQueueDate)
                .orderByAsc(OrderInfo::getQueueNo));
        List<Map<String, Object>> result = new ArrayList<>();
        for (OrderInfo order : orders) {
            Map<String, Object> item = new HashMap<>();
            item.put("orderId", order.getId());
            item.put("storeId", order.getStoreId());
            item.put("queueNo", order.getQueueNo());
            item.put("status", order.getStatus());
            item.put("targetName", order.getTargetName());
            item.put("readyTime", order.getReadyTime());
            FoodStore store = order.getStoreId() == null ? null : foodStoreMapper.selectById(order.getStoreId());
            item.put("storeName", store == null ? null : store.getName());
            item.put("aheadCount", countAhead(order));
            result.add(item);
        }
        return result;
    }

    /** 前方等待人数：同门店当日排队号更早、且仍在排队中/制作中的订单数 */
    private long countAhead(OrderInfo order) {
        if (order.getStoreId() == null || order.getQueueNo() == null || order.getQueueDate() == null) {
            return 0L;
        }
        return count(new LambdaQueryWrapper<OrderInfo>()
                .eq(OrderInfo::getStoreId, order.getStoreId())
                .eq(OrderInfo::getQueueDate, order.getQueueDate())
                .lt(OrderInfo::getQueueNo, order.getQueueNo())
                .in(OrderInfo::getStatus, OrderStatus.QUEUING, OrderStatus.MAKING));
    }

    @Override
    public IPage<OrderInfo> listUserOrders(int page, int size, Long userId, String orderType, String status) {
        LambdaQueryWrapper<OrderInfo> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(OrderInfo::getUserId, userId);
        if (StringUtils.hasText(orderType)) {
            wrapper.eq(OrderInfo::getOrderType, orderType);
        }
        if (StringUtils.hasText(status)) {
            wrapper.eq(OrderInfo::getStatus, status);
        }
        wrapper.orderByDesc(OrderInfo::getCreateTime);
        return page(new Page<>(page, size), wrapper);
    }

    @Override
    public IPage<OrderInfo> listAllOrders(int page, int size, String orderType, String status) {
        LambdaQueryWrapper<OrderInfo> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(orderType)) {
            wrapper.eq(OrderInfo::getOrderType, orderType);
        }
        if (StringUtils.hasText(status)) {
            wrapper.eq(OrderInfo::getStatus, status);
        }
        wrapper.orderByDesc(OrderInfo::getCreateTime);
        return page(new Page<>(page, size), wrapper);
    }

    // ==================== 内部方法 ====================

    private OrderInfo mustOwnOrder(Long orderId, Long userId) {
        OrderInfo order = getById(orderId);
        if (order == null) throw new RuntimeException("订单不存在");
        if (!order.getUserId().equals(userId)) throw new RuntimeException("无权操作此订单");
        return order;
    }

    private void recordLog(Long orderId, String from, String to, String remark, String operator) {
        OrderStatusLog log = new OrderStatusLog();
        log.setOrderId(orderId);
        log.setFromStatus(from);
        log.setToStatus(to);
        log.setChangeTime(new Date());
        log.setRemark(remark);
        log.setOperator(operator);
        statusLogMapper.insert(log);
    }

    private synchronized int nextQueueNo(Long storeId, Date day) {
        Long sid = storeId == null ? -1L : storeId;
        Long maxNo = baseMapper.selectObjs(new LambdaQueryWrapper<OrderInfo>()
                .select(OrderInfo::getQueueNo)
                .eq(OrderInfo::getStoreId, sid)
                .eq(OrderInfo::getQueueDate, day)
                .orderByDesc(OrderInfo::getQueueNo)
                .last("LIMIT 1"))
                .stream().filter(java.util.Objects::nonNull)
                .map(o -> ((Number) o).longValue()).findFirst().orElse(0L);
        return maxNo.intValue() + 1;
    }

    private Date truncateDate(Date date) {
        Calendar c = Calendar.getInstance();
        c.setTime(date);
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c.getTime();
    }

    private String safeQueueNo(OrderInfo order) {
        return order.getQueueNo() == null ? "-" : String.valueOf(order.getQueueNo());
    }
}
