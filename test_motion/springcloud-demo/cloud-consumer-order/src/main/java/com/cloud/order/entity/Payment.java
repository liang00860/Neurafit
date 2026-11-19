package com.cloud.order.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data                    // 生成getter/setter/toString
@AllArgsConstructor      // 全参构造
@NoArgsConstructor       // 无参构造
public class Payment implements Serializable {
    private Long id;
    private String serial;  // 支付流水号
}
