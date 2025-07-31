package com.example.media_poc.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.core.retry.RetryPolicy;
import software.amazon.awssdk.core.retry.backoff.FullJitterBackoffStrategy;
import software.amazon.awssdk.core.retry.backoff.EqualJitterBackoffStrategy;
import software.amazon.awssdk.core.retry.conditions.RetryCondition;
import software.amazon.awssdk.http.apache.ApacheHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;
import software.amazon.awssdk.services.s3.S3Configuration;

import java.net.URI;
import java.time.Duration;

@Configuration
public class AwsConfig {

    @Value("${aws.region}")
    private String region;

    @Value("${aws.accessKeyId:}")
    private String accessKeyId;

    @Value("${aws.secretAccessKey:}")
    private String secretAccessKey;

    @Value("${aws.http.max-connections:250}")
    private int maxConnections;

    @Value("${aws.http.connection-timeout:5s}")
    private Duration connectionTimeout;

    @Value("${aws.http.socket-timeout:30s}")
    private Duration socketTimeout;

    @Value("${aws.retry.max-attempts:3}")
    private int maxAttempts;

    @Value("${aws.retry.base-delay:100ms}")
    private Duration baseDelay;

    @Value("${aws.retry.max-backoff:1s}")
    private Duration maxBackoff;

    @Value("${aws.retry.throttling-base-delay:500ms}")
    private Duration throttlingBaseDelay;

    @Value("${aws.retry.throttling-max-backoff:5s}")
    private Duration throttlingMaxBackoff;

    @Value("${aws.s3.endpoint:}")
    private String endpoint;

    /**
     * Создаёт провайдер учётных данных.
     * Если заданы явные ключи — использует StaticCredentialsProvider,
     * иначе — DefaultCredentialsProvider.builder().build().
     */
    @Bean
    public AwsCredentialsProvider awsCredentialsProvider() {
        if (!accessKeyId.isBlank() && !secretAccessKey.isBlank()) {
            return StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(accessKeyId, secretAccessKey)
            );
        }
        return DefaultCredentialsProvider.builder()
                .build();
    }

    /**
     * Настраивает HTTP-клиент Apache: пул соединений, таймауты, реапер.
     */
    @Bean
    public ApacheHttpClient.Builder apacheHttpClientBuilder() {
        return ApacheHttpClient.builder()
                .maxConnections(maxConnections)
                .connectionTimeout(connectionTimeout)
                .socketTimeout(socketTimeout)
                .connectionAcquisitionTimeout(Duration.ofSeconds(5))
                .connectionTimeToLive(Duration.ofMinutes(5))
                .useIdleConnectionReaper(true);
    }

    /**
     * Политика повторов с разными стратегиями backoff:
     * - full jitter для не-throttling ошибок
     * - equal jitter (half-jitter) для throttling (429/503)
     */
    @Bean
    public RetryPolicy retryPolicy() {
        return RetryPolicy.builder()
                .numRetries(maxAttempts)
                .retryCondition(RetryCondition.defaultRetryCondition())
                .backoffStrategy(
                        FullJitterBackoffStrategy.builder()
                                .baseDelay(baseDelay)
                                .maxBackoffTime(maxBackoff)
                                .build()
                )
                .throttlingBackoffStrategy(
                        EqualJitterBackoffStrategy.builder()
                                .baseDelay(throttlingBaseDelay)
                                .maxBackoffTime(throttlingMaxBackoff)
                                .build()
                )
                .build();
    }

    /**
     * Общая конфигурация AWS-клиента:
     * - общее время вызова
     * - таймаут на попытку
     * - retryPolicy
     */
    @Bean
    public ClientOverrideConfiguration clientOverrideConfig(RetryPolicy retryPolicy) {
        return ClientOverrideConfiguration.builder()
                .apiCallTimeout(Duration.ofSeconds(15))
                .apiCallAttemptTimeout(Duration.ofSeconds(8))
                .retryPolicy(retryPolicy)
                .build();
    }

    /**
     * Собирает и возвращает S3Client с указанным регионом, кредами,
     * HTTP-клиентом и override-конфигурацией.
     */
    @Bean
    public S3Client s3Client(
            AwsCredentialsProvider credentialsProvider,
            ApacheHttpClient.Builder httpClientBuilder,
            ClientOverrideConfiguration overrideConfig
    ) {
//        return S3Client.builder()
//                .region(Region.of(region))
//                .credentialsProvider(credentialsProvider)
//                .httpClientBuilder(httpClientBuilder.applyMutation(b -> b
//                        .maxConnections(maxConnections)
//                        .connectionTimeout(connectionTimeout)
//                        .socketTimeout(socketTimeout)
//                ))
//                .overrideConfiguration(overrideConfig)
//                .build();
        S3Configuration s3Config = S3Configuration.builder()
                .pathStyleAccessEnabled(true)
                .build();
        S3ClientBuilder builder = S3Client.builder()
                .credentialsProvider(credentialsProvider)
                .region(Region.of(region))
                .serviceConfiguration(s3Config)
                .httpClientBuilder(httpClientBuilder)
                .overrideConfiguration(overrideConfig);

        if (!endpoint.isBlank()) {
            builder.endpointOverride(URI.create(endpoint));
        }

        return builder.build();

    }
}