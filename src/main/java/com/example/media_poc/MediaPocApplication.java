package com.example.media_poc;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;

@SpringBootApplication
@EnableCaching
public class MediaPocApplication {

	public static void main(String[] args) {
		SpringApplication.run(MediaPocApplication.class, args);
	}

}
