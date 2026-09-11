package com.stoloto.balloongame;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan("com.stoloto.balloongame.config")
public class BalloonGameApplication {

    public static void main(String[] args) {
        SpringApplication.run(BalloonGameApplication.class, args);
    }
}
