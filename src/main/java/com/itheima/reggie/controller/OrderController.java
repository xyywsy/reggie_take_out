package com.itheima.reggie.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.itheima.reggie.common.R;
import com.itheima.reggie.dto.DishDto;
import com.itheima.reggie.entity.Category;
import com.itheima.reggie.entity.Dish;
import com.itheima.reggie.entity.Orders;
import com.itheima.reggie.service.OrderService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 订单
 */
@Slf4j
@RestController
@RequestMapping("/order")
public class OrderController {

    @Autowired
    private OrderService orderService;

    @GetMapping("/page")
    public R<Page> page(int page, int pageSize) {

        //构造分页构造器对象
        Page<Orders> pageInfo = new Page<>(page, pageSize);

        //条件构造器
        LambdaQueryWrapper<Orders> queryWrapper = new LambdaQueryWrapper<>();
//        //添加过滤条件
//        queryWrapper.like(name != null, Orders::getName, name);
//        //添加排序条件
//        queryWrapper.orderByDesc(Dish::getUpdateTime);

        //执行分页查询
        orderService.page(pageInfo);

        return R.success(pageInfo);
    }

    /**
     * 用户下单
     *
     * @param orders
     * @return
     */
    @PostMapping("/submit")
    public R<String> submit(@RequestBody Orders orders) {
        log.info("订单数据：{}", orders);
        orderService.submit(orders);
        return R.success("下单成功");
    }
}