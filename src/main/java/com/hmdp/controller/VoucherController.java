package com.hmdp.controller;


import com.hmdp.dto.Result;
import com.hmdp.entity.Voucher;
import com.hmdp.service.IVoucherService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;


@RestController
@RequestMapping("/voucher")
public class VoucherController {

    private static final Logger log = LoggerFactory.getLogger(VoucherController.class);
    @Resource
    private IVoucherService voucherService;

    /**
     * 新增普通券
     *
     * @param voucher 优惠券信息
     * @return 优惠券id
     */
    @PostMapping
    public Result addVoucher(@RequestBody Voucher voucher) {
        log.info("新增普通券：{}", voucher);
        voucherService.save(voucher);
        return Result.ok(voucher.getId());
    }

    /**
     * 新增秒杀券
     *
     * @param voucher 优惠券信息，包含秒杀信息
     * @return 优惠券id
     */
    @PostMapping("seckill")
    public Result addSeckillVoucher(@RequestBody Voucher voucher) {
        log.info("新增秒杀卷：{}", voucher);
        voucherService.addSeckillVoucher(voucher);
        return Result.ok(voucher.getId());
    }

    /**
     * 查询店铺的优惠券列表
     *
     * @param shopId 店铺id
     * @return 优惠券列表
     */
    @GetMapping("/list/{shopId}")
    public Result queryVoucherOfShop(@PathVariable("shopId") Long shopId) {
        log.info("查询店铺的优惠券列表：{}", shopId);
        return voucherService.queryVoucherOfShop(shopId);
    }
}
