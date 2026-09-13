package com.stoloto.balloongame;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
@ConfigurationPropertiesScan("com.stoloto.balloongame.config")
public class BalloonGameApplication {

    public static void main(String[] args) {
        SpringApplication.run(BalloonGameApplication.class, args);
    }
}
