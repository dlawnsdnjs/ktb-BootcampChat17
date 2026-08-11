package com.ktb.chatapp.storage;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

@Slf4j
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "file.storage.type", havingValue = "s3")
public class S3StorageConfiguration {

    @Bean
    AwsCredentialsProvider awsCredentialsProvider(
            @Value("${file.storage.s3.access-key:}") String accessKey,
            @Value("${file.storage.s3.secret-key:}") String secretKey) {
        boolean hasAccessKey = accessKey != null && !accessKey.isBlank();
        boolean hasSecretKey = secretKey != null && !secretKey.isBlank();

        if (hasAccessKey != hasSecretKey) {
            throw new IllegalStateException(
                    "AWS_ACCESS_KEY_ID and AWS_SECRET_ACCESS_KEY must be configured together.");
        }
        if (!hasAccessKey) {
            log.info("S3 credentials: AWS default provider chain");
            return DefaultCredentialsProvider.create();
        }
        log.info("S3 credentials: static provider from application configuration");
        return StaticCredentialsProvider.create(
                AwsBasicCredentials.create(accessKey.trim(), secretKey.trim()));
    }

    @Bean
    S3Client s3Client(
            @Value("${file.storage.s3.region:}") String region,
            AwsCredentialsProvider credentialsProvider) {
        return S3Client.builder()
                .region(requiredRegion(region))
                .credentialsProvider(credentialsProvider)
                .build();
    }

    @Bean
    S3Presigner s3Presigner(
            @Value("${file.storage.s3.region:}") String region,
            AwsCredentialsProvider credentialsProvider) {
        return S3Presigner.builder()
                .region(requiredRegion(region))
                .credentialsProvider(credentialsProvider)
                .build();
    }

    private Region requiredRegion(String region) {
        if (region == null || region.isBlank()) {
            throw new IllegalStateException("S3 저장소를 사용하려면 AWS_REGION 설정이 필요합니다.");
        }
        return Region.of(region.trim());
    }
}
