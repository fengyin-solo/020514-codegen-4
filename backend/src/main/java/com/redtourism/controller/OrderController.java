package com.redtourism.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.redtourism.common.Constants;
import com.redtourism.common.Result;
import com.redtourism.entity.OrderInfo;
import com.redtourism.entity.OrderStatusLog;
import com.redtourism.entity.User;
import com.redtourism.service.OrderService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpSession;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/order")
public class OrderController {

    @Autowired
    private OrderService orderService;

    @GetMapping("/create")
    public Result<OrderInfo> create(@RequestParam String orderType,
                                     @RequestParam Long targetId,
                                     @RequestParam String targetName,
                                     @RequestParam BigDecimal amount,
                                     @RequestParam(defaultValue = "1") Integer quantity,
                                     @RequestParam(required = false) String checkInDate,
                                     @RequestParam(required = false) String checkOutDate,
                                     @RequestParam(required = false) Long storeId,
                                     HttpSession session) {
        User user = (User) session.getAttribute(Constants.SESSION_USER);
        if (user == null) return Result.error(401, "请先登录");
        OrderInfo order = new OrderInfo();
        order.setUserId(user.getId());
        order.setOrderType(orderType);
        order.setTargetId(targetId);
        order.setTargetName(targetName);
        order.setAmount(amount);
        order.setQuantity(quantity);
        order.setStoreId(storeId);
        return Result.success("下单成功", orderService.createOrder(order));
    }

    @GetMapping("/cancel")
    public Result<String> cancel(@RequestParam Long orderId, HttpSession session) {
        User user = (User) session.getAttribute(Constants.SESSION_USER);
        if (user == null) return Result.error(401, "请先登录");
        orderService.cancelOrder(orderId, user.getId());
        return Result.success("取消成功", null);
    }

    @GetMapping("/pay")
    public Result<String> pay(@RequestParam Long orderId,
                               @RequestParam String payMethod,
                               HttpSession session) {
        User user = (User) session.getAttribute(Constants.SESSION_USER);
        if (user == null) return Result.error(401, "请先登录");
        orderService.payOrder(orderId, payMethod, user.getId());
        return Result.success("支付成功（模拟）", null);
    }

    @GetMapping("/refund")
    public Result<String> refund(@RequestParam Long orderId, HttpSession session) {
        User user = (User) session.getAttribute(Constants.SESSION_USER);
        if (user == null) return Result.error(401, "请先登录");
        orderService.refundOrder(orderId, user.getId());
        return Result.success("退款成功（模拟）", null);
    }

    /** 用户确认取餐：待取餐 -> 已完成 */
    @GetMapping("/pickup")
    public Result<String> pickup(@RequestParam Long orderId, HttpSession session) {
        User user = (User) session.getAttribute(Constants.SESSION_USER);
        if (user == null) return Result.error(401, "请先登录");
        orderService.pickupOrder(orderId, user.getId());
        return Result.success("已确认取餐", null);
    }

    /** 订单状态流转记录（每次状态变化及变更时间） */
    @GetMapping("/statusLog")
    public Result<List<OrderStatusLog>> statusLog(@RequestParam Long orderId, HttpSession session) {
        User user = (User) session.getAttribute(Constants.SESSION_USER);
        if (user == null) return Result.error(401, "请先登录");
        return Result.success(orderService.listStatusLogs(orderId, user.getId()));
    }

    /** 我的自取排队（门店、排队号、前方等待人数） */
    @GetMapping("/myQueue")
    public Result<List<Map<String, Object>>> myQueue(HttpSession session) {
        User user = (User) session.getAttribute(Constants.SESSION_USER);
        if (user == null) return Result.error(401, "请先登录");
        return Result.success(orderService.myQueue(user.getId()));
    }

    @GetMapping("/myList")
    public Result<IPage<OrderInfo>> myList(@RequestParam(defaultValue = "1") int page,
                                            @RequestParam(defaultValue = "10") int size,
                                            @RequestParam(required = false) String orderType,
                                            @RequestParam(required = false) String status,
                                            HttpSession session) {
        User user = (User) session.getAttribute(Constants.SESSION_USER);
        if (user == null) return Result.error(401, "请先登录");
        return Result.success(orderService.listUserOrders(page, size, user.getId(), orderType, status));
    }

    @GetMapping("/detail")
    public Result<OrderInfo> detail(@RequestParam Long id) {
        return Result.success(orderService.getById(id));
    }
}
