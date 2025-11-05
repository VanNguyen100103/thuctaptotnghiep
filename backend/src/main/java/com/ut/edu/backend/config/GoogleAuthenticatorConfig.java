package com.ut.edu.backend.config;

import com.warrenstrange.googleauth.GoogleAuthenticator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

@Configuration
public class GoogleAuthenticatorConfig {

    @Bean
    public GoogleAuthenticator googleAuthenticator() {
        com.warrenstrange.googleauth.GoogleAuthenticatorConfig.GoogleAuthenticatorConfigBuilder configBuilder =
                new com.warrenstrange.googleauth.GoogleAuthenticatorConfig.GoogleAuthenticatorConfigBuilder()
                        .setTimeStepSizeInMillis(TimeUnit.SECONDS.toMillis(30))
                        .setWindowSize(5)
                        .setCodeDigits(6);

        return new GoogleAuthenticator(configBuilder.build());
    }
}
