package org.dnd.configuration;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

@TestConfiguration
public class TestJwtConfiguration {
    @Bean
    public JwtConfiguration jwtConfiguration() {
        return new JwtConfiguration() {
            @Override
            public String getSecret() {
                return "dGhpc2lzYXRlc3RzZWNyZXRrZXl0aGF0aXN2ZXJ5bG9uZ2FuZHNlY3VyZQ==";
            }

            @Override
            public Long getExpiration() {
                return 3600000L;
            }
        };
    }
}

