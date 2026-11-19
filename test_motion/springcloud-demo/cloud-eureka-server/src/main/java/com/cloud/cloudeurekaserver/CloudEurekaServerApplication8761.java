package com.cloud.cloudeurekaserver;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.netflix.eureka.server.EnableEurekaServer;

@SpringBootApplication
@EnableEurekaServer
public class CloudEurekaServerApplication8761 {

	public static void main(String[] args) {
		SpringApplication.run(CloudEurekaServerApplication8761.class, args);
		System.out.println("Eureka注册中心启动成功！访问: http://localhost:8761");
	}

}
