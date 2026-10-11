package com.sayibulmaca;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class SayiBulmacaApplication {

    public static void main(String[] args) {
        SpringApplication.run(SayiBulmacaApplication.class, args);
    }

}
