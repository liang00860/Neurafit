package com.cloud.order.feign;

import com.cloud.order.entity.CommonResult;
import com.cloud.order.entity.Payment;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * 支付模块的Feign客户端接口
 * 声明式调用：像调用本地接口一样调用远程支付服务
 */
//@FeignClient(name = "cloud-payment-service")
@FeignClient(
        name = "cloud-payment-service",
        fallback = PaymentFeignFallback.class    // 指定降级类
)
public interface PaymentFeignClient {
    /**
     * 调用支付模块的创建支付接口
     * 对应 支付模块的 PaymentController.create()
     */
    @PostMapping("/api/payment/create")
    CommonResult<Integer> create(@RequestBody Payment payment);

    /**
     * 调用支付模块的查询支付接口
     * 对应 支付模块的 PaymentController.getPaymentById()
     */
    @GetMapping("/api/payment/get/{id}")
    CommonResult<Payment> getPaymentById(@PathVariable("id") Long id);

    /**
     * 测试超时接口
     * 对应支付模块的 PaymentController.timeout()
     * 该接口会延迟5秒响应，用于测试Feign超时机制
     */
    @GetMapping("/api/payment/timeout")
    CommonResult<String> timeout();

    /**
     * 测试异常接口
     * 对应支付模块的 PaymentController.error()
     * 该接口会抛出RuntimeException，用于测试Feign异常处理机制
     */
    @GetMapping("/api/payment/error")
    CommonResult<String> error();
}
