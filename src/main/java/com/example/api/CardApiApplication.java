package com.example.api;

import com.example.api.config.properties.CryptoProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication(
        scanBasePackages = {
                "com.example.api",
        })
@EnableConfigurationProperties(CryptoProperties.class)
public class CardApiApplication {

	public static void main(String[] args) {
		SpringApplication.run(CardApiApplication.class, args);
	}

}
