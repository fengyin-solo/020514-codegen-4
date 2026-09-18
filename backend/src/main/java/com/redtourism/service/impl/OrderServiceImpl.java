package com.redtourism.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class OrderServiceImpl extends ServiceImpl<OrderInfoMapper, OrderInfo> implements OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderServiceImpl.class);

    /** 通用订单状态 */
    public static final String PENDING = "PENDING";
    public static final String PAID = "PAID";
    public static final String CANCELLED = "CANCELLED";
    public static final String REFUNDED = "REFUNDED";
    public static final String COMPLETED = "COMPLETED";
    /** 美食自取排队状态 */
    public static final String QUEUING = "QUEUING";      // 支付完成，排队中
    public static final String PREPARING = "PREPARING";  // 门店已接单，制作中
    public static final String READY = "READY";          // 已出餐叫号，待取餐
    public static final String VOID = "VOID";            // 已作废（超时未取等）

    /** 已支付但仍可退款的状态（含已作废：金额保持可退） */
    private static final List<String> REFUNDABLE = java.util.Arrays.asList(
            PAID, QUEUING, PREPARING, READY, VOID);

    @Autowired
    private OrderStatusLogMapper statusLogMapper;
    @Autowired
    private FoodMapper foodMapper;
    @Autowired
    private FoodStoreMapper foodStoreMapper;
    @Autowired
    private MessageService messageService;

    /** 待取餐超时时间（分钟），超时自动作废 */
    @Value("${order.pickup-timeout-minutes:30}")
    private long pickupTimeoutMinutes;

    /** 按门店加锁分配排队号，避免并发同号 */
    private final ConcurrentHashMap<Long, Object> storeLocks = new ConcurrentHashMap<>();

    @Override
    public OrderInfo createOrder(OrderInfo order) {
        order.setOrderNo("ORD" + System.currentTimeMillis() + UUID.randomUUID().toString().substring(0, 4).toUpperCase());
        order.setStatus(PENDING);
        // 美食订单落库所属门店，后续排队按门店编排
        if ("FOOD".equals(order.getOrderType()) && order.getTargetId() != null) {
            Food food = foodMapper.selectById(order.getTargetId());
            if (food != null) {
                order.setStoreId(food.getStoreId());
            }
        }
        save(order);
        return order;
    }

    @Override
    public boolean cancelOrder(Long orderId, Long userId) {
        OrderInfo order = getById(orderId);
        if (order == null) {
            throw new RuntimeException("订单不存在");
        }
        if (!order.getUserId().equals(userId)) {
            throw new RuntimeException("无权操作此订单");
        }
        if (!PENDING.equals(order.getStatus())) {
            throw new RuntimeException("当前订单状态不可取消");
        }
        order.setStatus(CANCELLED);
        updateById(order);
        writeLog(orderId, PENDING, CANCELLED, "用户取消订单", "USER");
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean payOrder(Long orderId, String payMethod, Long userId) {
        OrderInfo order = getById(orderId);
        if (order == null) {
            throw new RuntimeException("订单不存在");
        }
        if (!order.getUserId().equals(userId)) {
            throw new RuntimeException("无权操作此订单");
        }
        if (!PENDING.equals(order.getStatus())) {
            throw new RuntimeException("当前订单状态不可支付");
        }
        order.setPayMethod(payMethod);
        order.setPayTime(new Date());

        if ("FOOD".equals(order.getOrderType())) {
            // 美食自取：支付完成 -> 排队中，并按门店分配排队号
            if (order.getStoreId() == null && order.getTargetId() != null) {
                Food food = foodMapper.selectById(order.getTargetId());
                if (food != null) order.setStoreId(food.getStoreId());
            }
            if (order.getStoreId() == null) {
                throw new RuntimeException("美食门店信息缺失，无法排队");
            }
            // 暂停接单的门店不允许新订单进入（双保险，前端入口已置灰）
            FoodStore store = foodStoreMapper.selectById(order.getStoreId());
            if (store != null && Integer.valueOf(1).equals(store.getOrderPaused())) {
                throw new RuntimeException("门店暂停接单中，暂无法下单");
            }
            order.setQueueNo(nextQueueNo(order.getStoreId()));
            order.setStatus(QUEUING);
            updateById(order);
            writeLog(orderId, PENDING, QUEUING, "支付完成，进入排队，叫号：" + order.getQueueNo(), "USER");
            messageService.sendMessage(userId, "支付成功，已进入排队",
                    "您的订单【" + safe(order.getTargetName()) + "】已支付成功，排队号 " + order.getQueueNo()
                            + "，请留意叫号通知。");
        } else {
            order.setStatus(PAID);
            updateById(order);
            writeLog(orderId, PENDING, PAID, "支付完成", "USER");
        }
        return true;
    }

    @Override
    public boolean refundOrder(Long orderId, Long userId) {
        OrderInfo order = getById(orderId);
        if (order == null) {
            throw new RuntimeException("订单不存在");
        }
        if (!order.getUserId().equals(userId)) {
            throw new RuntimeException("无权操作此订单");
        }
        doRefund(order, "用户申请退款", "USER");
        return true;
    }

    /** 统一退款入口：已支付金额（含排队中/制作中/待取餐/已作废）始终保持可退 */
    private void doRefund(OrderInfo order, String remark, String operator) {
        if (!REFUNDABLE.contains(order.getStatus())) {
            throw new RuntimeException("当前订单状态不可退款");
        }
        String from = order.getStatus();
        order.setStatus(REFUNDED);
        order.setRefundTime(new Date());
        updateById(order);
        writeLog(order.getId(), from, REFUNDED, remark, operator);
        if (order.getUserId() != null) {
            messageService.sendMessage(order.getUserId(), "订单退款通知",
                    "您的订单【" + safe(order.getTargetName()) + "】已退款，金额 ¥" + order.getAmount() + "。");
        }
    }

    @Override
    public boolean acceptOrder(Long orderId) {
        OrderInfo order = getById(orderId);
        if (order == null) throw new RuntimeException("订单不存在");
        if (!QUEUING.equals(order.getStatus())) {
            throw new RuntimeException("仅排队中的订单可以接单");
        }
        order.setStatus(PREPARING);
        order.setAcceptTime(new Date());
        updateById(order);
        writeLog(orderId, QUEUING, PREPARING, "门店已接单，开始制作", "ADMIN");
        if (order.getUserId() != null) {
            messageService.sendMessage(order.getUserId(), "门店已接单",
                    "您的订单【" + safe(order.getTargetName()) + "】（号 " + order.getQueueNo() + "）正在制作中。");
        }
        return true;
    }

    @Override
    public boolean readyOrder(Long orderId) {
        OrderInfo order = getById(orderId);
        if (order == null) throw new RuntimeException("订单不存在");
        if (!PREPARING.equals(order.getStatus())) {
            throw new RuntimeException("仅制作中的订单可以出餐叫号");
        }
        order.setStatus(READY);
        order.setReadyTime(new Date());
        updateById(order);
        writeLog(orderId, PREPARING, READY, "已出餐，等待用户取餐（叫号）", "ADMIN");
        if (order.getUserId() != null) {
            messageService.sendMessage(order.getUserId(), "请取餐：" + order.getQueueNo() + " 号",
                    "您的订单【" + safe(order.getTargetName()) + "】已出餐，请尽快到店取餐。超时未取订单将被作废。");
        }
        return true;
    }

    @Override
    public boolean confirmPickup(Long orderId, Long userId) {
        OrderInfo order = getById(orderId);
        if (order == null) throw new RuntimeException("订单不存在");
        if (userId != null && !order.getUserId().equals(userId)) {
            throw new RuntimeException("无权操作此订单");
        }
        if (!READY.equals(order.getStatus())) {
            throw new RuntimeException("仅待取餐的订单可以确认取餐");
        }
        order.setStatus(COMPLETED);
        order.setCompleteTime(new Date());
        updateById(order);
        writeLog(orderId, READY, COMPLETED, "用户已确认取餐", "USER");
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean voidOrder(Long orderId, String reason, String operator) {
        OrderInfo order = getById(orderId);
        if (order == null) throw new RuntimeException("订单不存在");
        String from = order.getStatus();
        // 仅排队链路中的已支付订单可作废；作废不退款，金额保持可退（用户/门店可再走退款）
        if (!QUEUING.equals(from) && !PREPARING.equals(from) && !READY.equals(from)) {
            throw new RuntimeException("当前订单状态不可作废");
        }
        if (!StringUtils.hasText(reason)) {
            throw new RuntimeException("作废必须注明原因");
        }
        order.setStatus(VOID);
        order.setVoidTime(new Date());
        order.setVoidReason(reason);
        updateById(order);
        writeLog(orderId, from, VOID, "订单作废：" + reason, operator);
        if (order.getUserId() != null) {
            messageService.sendMessage(order.getUserId(), "订单已作废",
                    "您的订单【" + safe(order.getTargetName()) + "】（号 " + order.getQueueNo()
                            + "）已作废。原因：" + reason + "。已支付金额可申请退款。");
        }
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int voidStoreActiveOrders(Long storeId, String reason, String operator) {
        LambdaQueryWrapper<OrderInfo> w = new LambdaQueryWrapper<>();
        w.eq(OrderInfo::getStoreId, storeId)
                .in(OrderInfo::getStatus, QUEUING, PREPARING);
        List<OrderInfo> actives = list(w);
        int count = 0;
        for (OrderInfo o : actives) {
            try {
                voidOrder(o.getId(), reason, operator);
                count++;
            } catch (Exception e) {
                log.warn("作废门店在制订单失败 orderId={}: {}", o.getId(), e.getMessage());
            }
        }
        return count;
    }

    @Override
    public int autoVoidTimeoutOrders() {
        Date deadline = new Date(System.currentTimeMillis() - pickupTimeoutMinutes * 60_000L);
        LambdaQueryWrapper<OrderInfo> w = new LambdaQueryWrapper<>();
        w.eq(OrderInfo::getStatus, READY).lt(OrderInfo::getReadyTime, deadline);
        List<OrderInfo> timeoutList = list(w);
        int count = 0;
        for (OrderInfo o : timeoutList) {
            try {
                voidOrder(o.getId(), "出餐后超时未取（超过" + pickupTimeoutMinutes + "分钟），系统自动作废", "SYSTEM");
                count++;
            } catch (Exception e) {
                log.warn("自动作废超时订单失败 orderId={}: {}", o.getId(), e.getMessage());
            }
        }
        if (count > 0) log.info("[Queue] 自动作废超时未取订单 {} 笔", count);
        return count;
    }

    @Override
    public List<OrderStatusLog> listStatusLogs(Long orderId) {
        return statusLogMapper.selectList(new LambdaQueryWrapper<OrderStatusLog>()
                .eq(OrderStatusLog::getOrderId, orderId)
                .orderByAsc(OrderStatusLog::getCreateTime)
                .orderByAsc(OrderStatusLog::getId));
    }

    @Override
    public Integer getQueuePosition(Long storeId, Long orderId) {
        OrderInfo order = getById(orderId);
        if (order == null || order.getQueueNo() == null) return null;
        String status = order.getStatus();
        if (READY.equals(status)) return 0;      // 已叫号：轮到取餐
        if (COMPLETED.equals(status) || VOID.equals(status) || REFUNDED.equals(status)
                || CANCELLED.equals(status) || PENDING.equals(status)) return null;
        // 前方尚未叫号的当日同门店订单数 + 1
        Date dayStart = atStartOfDay(new Date());
        Long ahead = baseMapper.selectCount(new LambdaQueryWrapper<OrderInfo>()
                .eq(OrderInfo::getStoreId, storeId)
                .ge(OrderInfo::getCreateTime, dayStart)
                .in(OrderInfo::getStatus, QUEUING, PREPARING)
                .lt(OrderInfo::getQueueNo, order.getQueueNo()));
        return (ahead == null ? 0 : ahead.intValue()) + 1;
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
        IPage<OrderInfo> result = page(new Page<>(page, size), wrapper);
        result.getRecords().forEach(this::fillQueueInfo);
        return result;
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
        IPage<OrderInfo> result = page(new Page<>(page, size), wrapper);
        result.getRecords().forEach(this::fillQueueInfo);
        return result;
    }

    @Override
    public IPage<OrderInfo> listAllOrdersEnriched(IPage<OrderInfo> result) {
        result.getRecords().forEach(this::fillQueueInfo);
        return result;
    }

    /** 填充排队位置与门店名称（仅排队链路中的美食订单） */
    private void fillQueueInfo(OrderInfo o) {
        if (o.getStoreId() != null) {
            FoodStore store = foodStoreMapper.selectById(o.getStoreId());
            if (store != null) o.setStoreName(store.getName());
            if (QUEUING.equals(o.getStatus()) || PREPARING.equals(o.getStatus()) || READY.equals(o.getStatus())) {
                o.setQueuePosition(getQueuePosition(o.getStoreId(), o.getId()));
            }
        }
    }

    private Integer nextQueueNo(Long storeId) {
        Object lock = storeLocks.computeIfAbsent(storeId, k -> new Object());
        synchronized (lock) {
            // 每日按门店从 1 开始递增；用创建时间界定当日，避免把支付中的本单计入
            Date dayStart = atStartOfDay(new Date());
            LambdaQueryWrapper<OrderInfo> w = new LambdaQueryWrapper<>();
            w.eq(OrderInfo::getStoreId, storeId)
                    .ge(OrderInfo::getCreateTime, dayStart)
                    .isNotNull(OrderInfo::getQueueNo)
                    .orderByDesc(OrderInfo::getQueueNo).last("LIMIT 1");
            OrderInfo last = getOne(w);
            return (last != null && last.getQueueNo() != null) ? last.getQueueNo() + 1 : 1;
        }
    }

    private Date atStartOfDay(Date date) {
        java.util.Calendar c = java.util.Calendar.getInstance();
        c.setTime(date);
        c.set(java.util.Calendar.HOUR_OF_DAY, 0);
        c.set(java.util.Calendar.MINUTE, 0);
        c.set(java.util.Calendar.SECOND, 0);
        c.set(java.util.Calendar.MILLISECOND, 0);
        return c.getTime();
    }

    private void writeLog(Long orderId, String from, String to, String remark, String operator) {
        OrderStatusLog l = new OrderStatusLog();
        l.setOrderId(orderId);
        l.setFromStatus(from);
        l.setToStatus(to);
        l.setRemark(remark);
        l.setOperator(operator);
        statusLogMapper.insert(l);
    }

    private String safe(String s) {
        return s == null ? "" : s;
    }
}
