package com.eventledger.gateway.config;

import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class HttpClientConfiguration {

    @Bean
    RestClient accountServiceRestClient(
            RestClient.Builder builder,
            @Value("${account-service.base-url}")
            String baseUrl,
            @Value("${account-service.connect-timeout:2s}")
            Duration connectTimeout,
            @Value("${account-service.read-timeout:3s}")
            Duration readTimeout
    ) {
        SimpleClientHttpRequestFactory requestFactory =
                new SimpleClientHttpRequestFactory();

        requestFactory.setConnectTimeout(
                Math.toIntExact(connectTimeout.toMillis())
        );

        requestFactory.setReadTimeout(
                Math.toIntExact(readTimeout.toMillis())
        );

        return builder
                .requestFactory(requestFactory)
                .baseUrl(baseUrl)
                .build();
    }
}