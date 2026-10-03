package com.hmdp;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.scheduling.annotation.EnableScheduling;

//@EnableAspectJAutoProxy(exposeProxy = true)  // 用 RocketMQ 替代 Redis Stream 后不再用 AopContext，注释保留
@MapperScan("com.hmdp.mapper")
@SpringBootApplication
@EnableScheduling  // 订单超时兜底扫描（sweepExpiredOrders）
public class HmDianPingApplication {

    public static void main(String[] args) {
        SpringApplication.run(HmDianPingApplication.class, args);
    }

}
