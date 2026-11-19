package com.cloud.payment.service;

import com.cloud.payment.entity.Payment;

public interface PaymentService {
    int create(Payment payment);      // 新增支付记录
    Payment getPaymentById(Long id);  // 查询支付记录
}
