package com.hospital.medsupply;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication(scanBasePackages = {"com.hospital.medsupply", "com.hospital.common"})
@EnableDiscoveryClient
@EnableFeignClients(basePackages = "com.hospital.common.feign")
public class MedsupplyServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(MedsupplyServiceApplication.class, args);
    }
}
