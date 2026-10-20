package com.codeiary;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

@SpringBootApplication
@EnableAspectJAutoProxy
public class CodeiaryBeApplication {

	public static void main(String[] args) {
		SpringApplication.run(CodeiaryBeApplication.class, args);
	}

}
