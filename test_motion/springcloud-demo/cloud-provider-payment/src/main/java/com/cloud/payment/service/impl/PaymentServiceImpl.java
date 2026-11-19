package com.cloud.payment.service.impl;

import com.cloud.payment.entity.Payment;
import com.cloud.payment.service.PaymentService;
import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;


@Service
public class PaymentServiceImpl implements PaymentService {
    // 模拟数据库（ConcurrentHashMap线程安全）
    private static final ConcurrentHashMap<Long, Payment> DATABASE = new ConcurrentHashMap<>();
    private static final AtomicLong ID_GENERATOR = new AtomicLong(1);

    @Override
    public int create(Payment payment) {
        Long id = ID_GENERATOR.getAndIncrement();
        payment.setId(id);
        DATABASE.put(id, payment);
        return 1;  // 返回1表示插入成功
    }

    @Override
    public Payment getPaymentById(Long id) {
        return DATABASE.get(id);
    }
}
