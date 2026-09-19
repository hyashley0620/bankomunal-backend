package com.bankomunal;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class BankomunalApplication {
    public static void main(String[] args) {
        SpringApplication.run(BankomunalApplication.class, args);
    }
}
