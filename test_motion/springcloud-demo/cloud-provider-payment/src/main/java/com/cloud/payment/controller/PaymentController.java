package com.cloud.payment.controller;

import com.cloud.payment.entity.CommonResult;
import com.cloud.payment.entity.Payment;
import com.cloud.payment.service.PaymentService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/payment")
@Slf4j
public class PaymentController {
    @Autowired
    private PaymentService paymentService;

    @Value("${server.port}")
    private String serverPort;

    @PostMapping("/create")
    public CommonResult<Integer> create(@RequestBody Payment payment){
        Integer result= paymentService.create(payment);
        log.info("插入支付记录，结果：{}", result);
        if (result > 0) {
            return new CommonResult<>(200, "支付记录创建成功 [端口:" + serverPort + "]", result);
        } else {
            return new CommonResult<>(500, "支付记录创建失败");
        }
    }

    @GetMapping("/get/{id}")
    public CommonResult<Payment> getPaymentById(@PathVariable("id")Long id){
        Payment payment=  paymentService.getPaymentById(id);
        log.info("查询支付记录，id={}，结果：{}", id, payment);
        if (payment != null) {
            return new CommonResult<>(200, "查询成功 [端口:" + serverPort + "]", payment);
        } else {
            return new CommonResult<>(500, "未找到对应记录，id=" + id);
        }
    }

    /**
     * 测试超时接口 - 故意延迟5秒响应
     * Feign默认超时是1秒，会触发超时异常
     */
    @GetMapping("/timeout")
    public CommonResult<String> timeout() {
        try {
            log.info("支付模块收到超时测试请求，准备休眠5秒...");
            Thread.sleep(5000);
            log.info("支付模块超时测试完成");
            return new CommonResult<>(200, "超时测试成功 [端口:" + serverPort + "]，耗时5秒");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("超时测试被中断", e);
            return new CommonResult<>(500, "超时测试被中断");
        }
    }

    /**
     * 测试异常接口 - 故意抛出运行时异常
     * 用于测试Feign调用异常时的熔断降级机制
     */
    @GetMapping("/error")
    public CommonResult<String> error() {
        log.info("支付模块收到异常测试请求，准备抛出异常...");
        throw new RuntimeException("支付模块故意抛出的异常 [端口:" + serverPort + "]");
    }

}
