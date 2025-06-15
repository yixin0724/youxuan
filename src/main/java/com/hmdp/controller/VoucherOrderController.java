package com.hmdp.controller;


import com.hmdp.dto.Result;
import com.hmdp.service.IVoucherOrderService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;


@RestController
@RequestMapping("/voucher-order")
public class VoucherOrderController {
    private static final Logger log = LoggerFactory.getLogger(VoucherOrderController.class);
    @Resource
    private IVoucherOrderService voucherOrderService;

    /**
     * 抢购秒杀优惠券
     * @param voucherId
     * @return
     */
    @PostMapping("seckill/{id}")
    public Result seckillVoucher(@PathVariable("id") Long voucherId) {
        log.info("用户正在抢购秒杀卷...");
        return voucherOrderService.seckillVoucher(voucherId);
    }
}
