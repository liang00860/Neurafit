package com.cloud.order.feign;

import com.cloud.order.entity.CommonResult;
import com.cloud.order.entity.Payment;
import org.springframework.stereotype.Component;

/**
 * 支付模块Feign客户端的降级处理类
 * 当调用支付模块失败时，执行此类中的方法
 */
@Component
public class PaymentFeignFallback implements PaymentFeignClient {

    @Override
    public CommonResult<Integer> create(Payment payment) {
        return new CommonResult<>(503, "支付服务不可用，创建失败", null);
    }

    @Override
    public CommonResult<Payment> getPaymentById(Long id) {
        return new CommonResult<>(503, "支付服务不可用，查询失败", null);
    }

    @Override
    public CommonResult<String> timeout() {
        return new CommonResult<>(503, "支付服务响应超时", "fallback");
    }

    @Override
    public CommonResult<String> error() {
        return new CommonResult<>(503, "支付服务异常", "fallback");
    }
}