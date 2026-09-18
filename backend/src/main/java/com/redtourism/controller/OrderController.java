package com.redtourism.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.redtourism.common.Constants;
import com.redtourism.common.Result;
import com.redtourism.entity.OrderInfo;
import com.redtourism.entity.User;
import com.redtourism.service.OrderService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpSession;
import java.math.BigDecimal;

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

    /** 用户确认取餐：待取餐 -> 已完成 */
    @GetMapping("/confirmPickup")
    public Result<String> confirmPickup(@RequestParam Long orderId, HttpSession session) {
        User user = (User) session.getAttribute(Constants.SESSION_USER);
        if (user == null) return Result.error(401, "请先登录");
        orderService.confirmPickup(orderId, user.getId());
        return Result.success("取餐成功，欢迎再次光临", null);
    }

    /** 订单状态流转时间线（每个状态的变更时间） */
    @GetMapping("/statusLogs")
    public Result<java.util.List<com.redtourism.entity.OrderStatusLog>> statusLogs(@RequestParam Long orderId) {
        return Result.success(orderService.listStatusLogs(orderId));
    }

    /** 查询某门店当前订单的实时排队位置 */
    @GetMapping("/queuePosition")
    public Result<Integer> queuePosition(@RequestParam Long storeId,
                                          @RequestParam Long orderId,
                                          HttpSession session) {
        User user = (User) session.getAttribute(Constants.SESSION_USER);
        if (user == null) return Result.error(401, "请先登录");
        return Result.success(orderService.getQueuePosition(storeId, orderId));
    }
}
