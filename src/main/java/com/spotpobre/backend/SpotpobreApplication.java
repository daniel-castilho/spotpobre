package com.spotpobre.backend;

import io.awspring.cloud.autoconfigure.s3.S3AutoConfiguration; // Import S3AutoConfiguration
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;

@SpringBootApplication(exclude = S3AutoConfiguration.class) // Exclude S3AutoConfiguration
@EnableCaching
public class SpotpobreApplication {

	public static void main(String[] args) {
		SpringApplication.run(SpotpobreApplication.class, args);
	}

}
