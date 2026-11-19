package com.cloud.hystrixdashboard;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.netflix.hystrix.dashboard.EnableHystrixDashboard;

@SpringBootApplication
@EnableHystrixDashboard   // 开启Hystrix仪表盘
public class CloudHystrixDashboardApplication {

    public static void main(String[] args) {

        SpringApplication.run(CloudHystrixDashboardApplication.class, args);
        System.out.println("Hystrix Dashboard启动成功！访问: http://localhost:9001/hystrix");
    }

}
