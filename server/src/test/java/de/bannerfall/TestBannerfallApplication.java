package de.bannerfall;

import org.springframework.boot.SpringApplication;

public class TestBannerfallApplication {

    public static void main(String[] args) {
        SpringApplication.from(BannerfallApplication::main)
                .with(TestcontainersConfiguration.class)
                .run(args);
    }
}
