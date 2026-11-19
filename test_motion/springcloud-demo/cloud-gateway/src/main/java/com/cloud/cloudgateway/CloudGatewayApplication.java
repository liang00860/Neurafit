package com.cloud.cloudgateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.netflix.eureka.EnableEurekaClient;

@SpringBootApplication
@EnableEurekaClient
public class CloudGatewayApplication {

	public static void main(String[] args) {

		SpringApplication.run(CloudGatewayApplication.class, args);
		System.out.println("Gateway网关启动成功！端口: 9527");
		System.out.println("访问地址: http://localhost:9527/api/order/get/1");
	}

}
