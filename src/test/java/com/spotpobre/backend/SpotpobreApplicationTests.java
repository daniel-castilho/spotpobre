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
        // Ephemeral port: the suite must not depend on 8080 being free (e.g. when the
        // blue/green compose stack is running on the host).
        try (ConfigurableApplicationContext context =
                     SpringApplication.run(SpotpobreApplication.class, "--server.port=0")) {
            assertThat(context).isNotNull();
            assertThat(context.isActive()).isTrue();
        }
    }
}
