package com.hospital.inpatient;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication(scanBasePackages = {"com.hospital.inpatient", "com.hospital.common"})
@EnableDiscoveryClient
@EnableFeignClients(basePackages = "com.hospital.common.feign")
public class InpatientServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(InpatientServiceApplication.class, args);
    }
}
