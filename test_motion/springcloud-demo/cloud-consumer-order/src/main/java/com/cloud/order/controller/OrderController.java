package com.cloud.order.controller;


import com.cloud.order.entity.CommonResult;
import com.cloud.order.entity.Payment;
import com.cloud.order.feign.PaymentFeignClient;
import com.netflix.hystrix.contrib.javanica.annotation.HystrixCommand;
import com.netflix.hystrix.contrib.javanica.annotation.HystrixProperty;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

@RestController
@RequestMapping("/api/order")
@Slf4j
public class OrderController {
    // 支付模块的地址（硬编码，第2章会改为服务名）
    //private static final String PAYMENT_URL = "http://localhost:8001";
    //改为服务名（必须与 spring.application.name 完全一致）
    private static final String PAYMENT_URL = "http://cloud-payment-service";
//    @Autowired
//    private RestTemplate restTemplate;
    // 注入Feign客户端（像调用本地接口一样）
    @Autowired
    private PaymentFeignClient paymentFeignClient;

    @PostMapping("/create")
    public CommonResult<Integer> createOrder(@RequestBody Payment payment) {
        log.info("订单模块（OpenFeign方式）调用支付模块");

        // 像调用本地方法一样！代码非常简洁
        CommonResult<Integer> result = paymentFeignClient.create(payment);

        return result;
    }

    @GetMapping("/get/{id}")
    public CommonResult<Payment> getOrder(@PathVariable("id") Long id) {
        log.info("订单模块（OpenFeign方式）查询支付记录");

        // 像调用本地方法一样！
        CommonResult<Payment> result = paymentFeignClient.getPaymentById(id);

        return result;
    }

    /**
     * 测试超时 - 带降级保护
     * 超时阈值设置为1.5秒，支付模块需要3秒，触发降级
     */
    @GetMapping("/timeout/fallback")
    @HystrixCommand(
            fallbackMethod = "timeoutFallback",
            commandProperties = {
                    @HystrixProperty(name = "execution.isolation.thread.timeoutInMilliseconds", value = "1500")
            }
    )
    public CommonResult<String> timeoutWithFallback() {
        log.info("订单模块调用支付模块（超时测试，有Hystrix保护）");
        return paymentFeignClient.timeout();
    }

    /**
     * 降级方法（必须和原方法返回值类型相同，参数列表相同+可选Throwable）
     */
    public CommonResult<String> timeoutFallback() {
        log.warn("支付模块调用超时，执行降级方法");
        return new CommonResult<>(503, "支付服务繁忙，请稍后重试", "fallback-data");
    }

    /**
     * 测试异常 - 带降级保护
     */
    @GetMapping("/error/fallback")
    @HystrixCommand(
            fallbackMethod = "errorFallback",
            ignoreExceptions = {NullPointerException.class}  // 忽略NPE，不触发降级
    )
    public CommonResult<String> errorWithFallback() {
        log.info("订单模块调用支付模块（异常测试，有Hystrix保护）");
        return paymentFeignClient.error();
    }

    /**
     * 降级方法
     */
    public CommonResult<String> errorFallback(Throwable e) {
        log.warn("支付模块调用异常，执行降级方法，异常信息：{}", e.getMessage());
        return new CommonResult<>(503, "支付服务出现异常，请稍后重试", "fallback-error");
    }


    /**
     * 测试熔断器效果
     *
     * 配置说明：
     * - 滚动窗口10秒内，请求数>=10次
     * - 错误率达到50%时触发熔断
     * - 熔断后5秒尝试恢复
     */
    @GetMapping("/circuit/{id}")
    @HystrixCommand(
            fallbackMethod = "circuitFallback",
            commandProperties = {
                    // 熔断相关配置
                    @HystrixProperty(name = "circuitBreaker.enabled", value = "true"),
                    @HystrixProperty(name = "circuitBreaker.requestVolumeThreshold", value = "10"),  // 10次请求开始统计
                    @HystrixProperty(name = "circuitBreaker.errorThresholdPercentage", value = "50"), // 错误率50%熔断
                    @HystrixProperty(name = "circuitBreaker.sleepWindowInMilliseconds", value = "5000"), // 5秒休眠窗口

                    // 超时配置（让接口快速失败）
                    @HystrixProperty(name = "execution.isolation.thread.timeoutInMilliseconds", value = "2000")
            }
    )
    public CommonResult<String> circuitBreaker(@PathVariable("id") Long id) {
        log.info("熔断测试，传入id={}", id);

        if (id < 0) {
            // 传入负数，故意制造异常
            throw new RuntimeException("id不能为负数！");
        }

        // 正常调用支付模块
        return paymentFeignClient.timeout();
    }

    public CommonResult<String> circuitFallback(Long id, Throwable e) {
        log.warn("熔断降级触发，id={}，异常={}", id, e.getMessage());
        return new CommonResult<>(503, "服务熔断降级，请稍后重试", "circuit-fallback");
    }
//    /**
//     * 创建订单 -> 调用支付模块创建支付记录
//     */
//    @PostMapping("/create")
//    public CommonResult<Payment> createOrder(@RequestBody Payment payment) {
//        log.info("订单模块收到请求，准备调用支付模块: {}", payment);
//
//        try {
//            // 通过RestTemplate调用支付模块的接口
//            CommonResult<Payment> result = restTemplate.postForObject(
//                    PAYMENT_URL + "/api/payment/create",
//                    payment,
//                    CommonResult.class
//            );
//
//            log.info("支付模块返回结果: {}", result);
//            return result;
//        } catch (Exception e) {
//            log.error("调用支付模块失败", e);
//            return new CommonResult<>(500, "调用支付模块失败: " + e.getMessage());
//        }
//    }
//
//    /**
//     * 查询订单 -> 调用支付模块查询支付记录
//     */
//    @GetMapping("/get/{id}")
//    public CommonResult<Payment> getOrder(@PathVariable("id") Long id) {
//        log.info("订单模块查询订单，id={}，调用支付模块", id);
//
//        try {
//            CommonResult<Payment> result = restTemplate.getForObject(
//                    PAYMENT_URL + "/api/payment/get/" + id,
//                    CommonResult.class
//            );
//
//            return result;
//        } catch (Exception e) {
//            log.error("查询订单失败", e);
//            return new CommonResult<>(500, "查询订单失败: " + e.getMessage());
//        }
//    }
//
//    // 添加一个测试负载均衡的接口
//    @GetMapping("/test/loadbalance/{id}")
//    public String testLoadBalance(@PathVariable("id") Long id) {
//        StringBuilder result = new StringBuilder();
//        result.append("<h2>负载均衡测试结果</h2>");
//        result.append("请求ID: ").append(id).append("<br>");
//        result.append("调用次数: 10次<br><hr>");
//
//        for (int i = 0; i < 10; i++) {
//            try {
//                CommonResult<Payment> res = restTemplate.getForObject(
//                        PAYMENT_URL + "/api/payment/get/" + id,
//                        CommonResult.class
//                );
//
//                if (res != null) {
//                    String message = res.getMessage() != null ? res.getMessage() : "无消息";
//                    result.append("<div style='margin: 5px 0;'>")
//                          .append("<strong>第").append(i + 1).append("次调用</strong> - ")
//                          .append("状态码: ").append(res.getCode()).append(", ")
//                          .append("响应: ").append(message)
//                          .append("</div><br>");
//
//                    log.info("第{}次调用成功: {}", i + 1, message);
//                } else {
//                    result.append("<div style='color: orange;'>")
//                          .append("第").append(i + 1).append("次调用 - 返回null")
//                          .append("</div><br>");
//                }
//
//            } catch (Exception e) {
//                result.append("<div style='color: red;'>")
//                      .append("第").append(i + 1).append("次调用 - 异常: ")
//                      .append(e.getClass().getSimpleName())
//                      .append(": ").append(e.getMessage())
//                      .append("</div><br>");
//
//                log.error("第{}次调用失败", i + 1, e);
//            }
//        }
//
//        result.append("<hr><p>提示: 观察【端口】变化，如果看到不同端口号说明负载均衡生效</p>");
//        return result.toString();
//    }
}
