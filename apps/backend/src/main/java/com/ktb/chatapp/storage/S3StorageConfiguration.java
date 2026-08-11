package com.ktb.chatapp.storage;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "file.storage.type", havingValue = "s3")
public class S3StorageConfiguration {

    @Bean
    S3Client s3Client(@Value("${file.storage.s3.region:}") String region) {
        return S3Client.builder()
                .region(requiredRegion(region))
                .build();
    }

    @Bean
    S3Presigner s3Presigner(@Value("${file.storage.s3.region:}") String region) {
        return S3Presigner.builder()
                .region(requiredRegion(region))
                .build();
    }

    private Region requiredRegion(String region) {
        if (region == null || region.isBlank()) {
            throw new IllegalStateException("S3 저장소를 사용하려면 AWS_REGION 설정이 필요합니다.");
        }
        return Region.of(region.trim());
    }
}
