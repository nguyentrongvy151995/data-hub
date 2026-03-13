package com.anonymous.datahub.data_hub;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class DataHubApplication {

	public static void main(String[] args) {
		SpringApplication.run(DataHubApplication.class, args);
	}

}
