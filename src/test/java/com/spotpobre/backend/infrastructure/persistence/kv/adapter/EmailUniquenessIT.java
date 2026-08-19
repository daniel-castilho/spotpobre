package com.spotpobre.backend.infrastructure.persistence.kv.adapter;

import com.spotpobre.backend.AbstractIntegrationTest;
import com.spotpobre.backend.domain.user.model.User;
import com.spotpobre.backend.domain.user.model.UserProfile;
import com.spotpobre.backend.domain.user.port.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class EmailUniquenessIT extends AbstractIntegrationTest {

    @Autowired
    private UserRepository userRepository;

    @Test
    void shouldRejectDuplicateEmailRegistration() {
        String email = "duplicate-" + UUID.randomUUID() + "@example.com";
        User first = User.createWithLocalPassword(new UserProfile("First", email, "BR"), "pass");
        User second = User.createWithLocalPassword(new UserProfile("Second", email, "US"), "pass");

        assertTrue(userRepository.createIfEmailNotExists(first));
        assertFalse(userRepository.createIfEmailNotExists(second));
        assertTrue(userRepository.findByProfileEmail(email).isPresent());
    }

    @Test
    void onlyOneConcurrentRegistrationWithSameEmailSucceeds() throws Exception {
        String email = "concurrent-" + UUID.randomUUID() + "@example.com";
        int threads = 5;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            List<Future<Boolean>> results = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                final int index = i;
                results.add(pool.submit(() -> {
                    User user = User.createWithLocalPassword(
                            new UserProfile("User " + index, email, "BR"), "pass");
                    return userRepository.createIfEmailNotExists(user);
                }));
            }

            long successes = 0;
            for (Future<Boolean> result : results) {
                if (result.get()) {
                    successes++;
                }
            }
            assertEquals(1, successes);
        } finally {
            pool.shutdown();
        }
    }
}