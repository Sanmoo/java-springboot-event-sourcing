package com.sanmoo.eventsourcing.creditaccount;

import com.sanmoo.eventsourcing.creditaccount.projection.ProjectionProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties(ProjectionProperties.class)
public class CreditAccountApplication {
    public static void main(String[] args) {
        SpringApplication.run(CreditAccountApplication.class, args);
    }
}
