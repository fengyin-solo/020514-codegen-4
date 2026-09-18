package com.redtourism.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.redtourism.common.Constants;
import com.redtourism.common.Result;
import com.redtourism.entity.Food;
import com.redtourism.entity.FoodStore;
import com.redtourism.entity.User;
import com.redtourism.service.FoodService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpSession;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/food")
public class FoodController {

    @Autowired
    private FoodService foodService;

    @GetMapping("/list")
    public Result<IPage<Food>> list(@RequestParam(defaultValue = "1") int page,
                                     @RequestParam(defaultValue = "10") int size,
                                     @RequestParam(required = false) String category,
                                     @RequestParam(required = false) String keyword,
                                     @RequestParam(required = false) String orderBy) {
        return Result.success(foodService.listFoods(page, size, category, keyword, orderBy));
    }

    @GetMapping("/detail")
    public Result<Food> detail(@RequestParam Long id) {
        return Result.success(foodService.getDetail(id));
    }

    @GetMapping("/stores")
    public Result<List<FoodStore>> stores(@RequestParam(required = false) String keyword) {
        return Result.success(foodService.listStores(keyword));
    }

    @GetMapping("/storeDetail")
    public Result<FoodStore> storeDetail(@RequestParam Long id) {
        return Result.success(foodService.getStoreDetail(id));
    }

    @GetMapping("/categories")
    public Result<List<String>> categories() {
        List<String> categories = java.util.Arrays.asList(
                "酸汤系列", "辣子系列", "烧烤", "米粉面食", "小吃", "特色火锅", "民族菜");
        return Result.success(categories);
    }

    /** 门店实时排队信息（含当前登录用户的排队位置与叫号） */
    @GetMapping("/storeQueue")
    public Result<Map<String, Object>> storeQueue(@RequestParam Long storeId, HttpSession session) {
        User user = (User) session.getAttribute(Constants.SESSION_USER);
        return Result.success(foodService.storeQueueInfo(storeId, user != null ? user.getId() : null));
    }
}
