package com.spotpobre.backend;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ConfigurableApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class SpotpobreApplicationTests {

    @Autowired
    private ConfigurableApplicationContext applicationContext;

    @Test
    void contextLoads() {
        assertThat(applicationContext).isNotNull();
    }

    @Test
    void mainApplicationStarts() {
        try (ConfigurableApplicationContext context =
                     SpringApplication.run(SpotpobreApplication.class)) {
            assertThat(context).isNotNull();
            assertThat(context.isActive()).isTrue();
        }
    }
}
