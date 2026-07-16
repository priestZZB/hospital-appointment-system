package com.hospital.clinic;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication(scanBasePackages = {"com.hospital.clinic", "com.hospital.common"})
@EnableDiscoveryClient
@EnableFeignClients(basePackages = "com.hospital.common.feign")
public class ClinicServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(ClinicServiceApplication.class, args);
    }
}
