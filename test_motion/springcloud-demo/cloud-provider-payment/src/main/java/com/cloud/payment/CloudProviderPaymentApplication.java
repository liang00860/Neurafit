package com.cloud.payment;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.netflix.eureka.EnableEurekaClient;

@SpringBootApplication
@EnableEurekaClient
public class CloudProviderPaymentApplication {

	public static void main(String[] args) {

		SpringApplication.run(CloudProviderPaymentApplication.class, args);
		System.out.println("支付模块启动成功！端口: 8001");
	}

}
