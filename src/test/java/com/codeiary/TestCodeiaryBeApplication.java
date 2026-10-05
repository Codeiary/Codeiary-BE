package com.codeiary;

import com.codeiary.support.TestcontainersConfiguration;
import org.springframework.boot.SpringApplication;

public class TestCodeiaryBeApplication {

    public static void main(String[] args) {
        SpringApplication.from(CodeiaryBeApplication::main)
                .with(TestcontainersConfiguration.class)
                .run(args);
    }
}
