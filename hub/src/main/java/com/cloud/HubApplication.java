package com.cloud;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class HubApplication {

    public static void main(String[] args) {
        String tableConfig = System.getProperty("hub.table-config-dir");
        if (tableConfig != null && !tableConfig.isEmpty()) {
            System.setProperty("table.config.dir", tableConfig);
            System.setProperty("tableConfigDir", tableConfig);
        }
        SpringApplication.run(HubApplication.class, args);
    }
}
