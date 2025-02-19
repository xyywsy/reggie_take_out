package com.itheima.reggie.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.itheima.reggie.common.CustomException;
import com.itheima.reggie.dto.DishDto;
import com.itheima.reggie.entity.Dish;
import com.itheima.reggie.entity.DishFlavor;
import com.itheima.reggie.entity.Setmeal;
import com.itheima.reggie.entity.SetmealDish;
import com.itheima.reggie.mapper.DishMapper;
import com.itheima.reggie.service.DishFlavorService;
import com.itheima.reggie.service.DishService;
import com.itheima.reggie.service.SetmealDishService;
import com.itheima.reggie.service.SetmealService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@Slf4j
public class DishServiceImpl extends ServiceImpl<DishMapper, Dish> implements DishService {

    @Autowired
    private DishFlavorService dishFlavorService;
    @Autowired
    private SetmealDishService setmealDishService;
    @Autowired
    private SetmealService setmealService;

    /**
     * 新增菜品，同时保存对应的口味数据
     *
     * @param dishDto
     */
    @Transactional
    public void saveWithFlavor(DishDto dishDto) {
        //保存菜品的基本信息到菜品表dish
        this.save(dishDto);

        Long dishId = dishDto.getId();//菜品id

        //菜品口味
        List<DishFlavor> flavors = dishDto.getFlavors();
        flavors = flavors.stream().map((item) -> {
            item.setDishId(dishId);
            return item;
        }).collect(Collectors.toList());

        //保存菜品口味数据到菜品口味表dish_flavor
        dishFlavorService.saveBatch(flavors);

    }

    /**
     * 根据id查询菜品信息和对应的口味信息
     *
     * @param id
     * @return
     */
    public DishDto getByIdWithFlavor(Long id) {
        //查询菜品基本信息，从dish表查询
        Dish dish = this.getById(id);

        DishDto dishDto = new DishDto();
        BeanUtils.copyProperties(dish, dishDto);

        //查询当前菜品对应的口味信息，从dish_flavor表查询
        LambdaQueryWrapper<DishFlavor> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(DishFlavor::getDishId, dish.getId());
        List<DishFlavor> flavors = dishFlavorService.list(queryWrapper);
        dishDto.setFlavors(flavors);

        return dishDto;
    }

    /**
     * 更新dish表基本信息
     *
     * @param dishDto
     */
    @Override
    @Transactional
    public void updateWithFlavor(DishDto dishDto) {
        //更新dish表基本信息
        this.updateById(dishDto);

        //清理当前菜品对应口味数据---dish_flavor表的delete操作
        LambdaQueryWrapper<DishFlavor> queryWrapper = new LambdaQueryWrapper();
        queryWrapper.eq(DishFlavor::getDishId, dishDto.getId());

        dishFlavorService.remove(queryWrapper);

        //添加当前提交过来的口味数据---dish_flavor表的insert操作
        List<DishFlavor> flavors = dishDto.getFlavors();

        flavors = flavors.stream().map((item) -> {
            item.setDishId(dishDto.getId());
            return item;
        }).collect(Collectors.toList());

        dishFlavorService.saveBatch(flavors);
    }

    /**
     * 更新菜品的销售状态
     *
     * @param ids
     * @param status
     */
    @Override
    public void updateStatus(Long[] ids, int status) {
        LambdaQueryWrapper<Dish> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.in(Dish::getId, ids);
        List<Dish> dishList = this.list(queryWrapper);
        for (Dish dish : dishList) {
            Long id = dish.getId();
            int statusValue = dish.getStatus();
            //如果改变之前是起售
            if (statusValue == 1) {
                //如果在起售的套餐里面则不可以更改
                LambdaQueryWrapper<SetmealDish> queryWrapperSetmealDish = new LambdaQueryWrapper<>();
                queryWrapperSetmealDish.eq(SetmealDish::getDishId, id);
                List<SetmealDish> SetmealDishList = setmealDishService.list(queryWrapperSetmealDish);
                List<Long> setmealIdList = SetmealDishList.stream().map(item -> {
                    return item.getSetmealId();
                }).collect(Collectors.toList());
                //菜品不在套餐里面
                if (setmealIdList.size()==0) {
                    dish.setStatus(0);
                    this.updateById(dish);
                    continue;
                }
                LambdaQueryWrapper<Setmeal> queryWrapperSetmeal = new LambdaQueryWrapper<>();
                queryWrapperSetmeal.in(Setmeal::getId, setmealIdList);
                List<Setmeal> SetmealList = setmealService.list(queryWrapperSetmeal);
                for (Setmeal setmeal : SetmealList) {
                    if (setmeal.getStatus() == 1) {
                        throw new CustomException("菜品正在售卖的套餐中，不能更改");
                    }else {
                        // 菜品所在套餐已经停售
                        dish.setStatus(0);
                        this.updateById(dish);
                    }
                }
            }
            //如果改变之前是停售, 则直接变成起售
            else {
                dish.setStatus(1);
                this.updateById(dish);
            }
        }
        // 更改状态
        LambdaUpdateWrapper<Dish> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.in(Dish::getId, ids);
        updateWrapper.set(Dish::getStatus, status);
        this.update(updateWrapper);
    }

    @Override
    public void removeDish(Long[] ids) {
        LambdaQueryWrapper<Dish> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.in(Dish::getId, ids);
        List<Dish> dishList = this.list(queryWrapper);
        for (Dish dish : dishList) {
            int statusValue = dish.getStatus();
            Long id = dish.getId();
            if (statusValue == 1) {
                log.info("菜品正在售卖中，不能删除");
                return;
//                throw new CustomException("菜品正在售卖中，不能删除");
            }
            // 停售状态才可以删除
            // 删除Dish表对应内容
            log.info("删除菜品ID:{}",id);
            this.removeById(id);
        }
        //删除Setmeal的对应内容
        LambdaQueryWrapper<SetmealDish> queryWrapperSetmealDish = new LambdaQueryWrapper<>();
        queryWrapperSetmealDish.in(SetmealDish::getDishId, ids);
        setmealDishService.remove(queryWrapperSetmealDish);

    }


}
