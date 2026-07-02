package com.dicom.medical.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;

/**
 * AWS Bedrock Runtime 클라이언트.
 * 인증은 DefaultCredentialsProvider → EB 인스턴스 역할(aws-elasticbeanstalk-ec2-role)을 사용.
 * (S3 와 동일하게, 역할에 bedrock:InvokeModel 권한이 있어야 함)
 */
@Configuration
@ConditionalOnProperty(name = "aws.bedrock.enabled", havingValue = "true", matchIfMissing = true)
public class BedrockConfig {

    @Value("${aws.bedrock.region:ap-northeast-2}")
    private String region;

    @Bean
    public BedrockRuntimeClient bedrockRuntimeClient() {
        return BedrockRuntimeClient.builder()
                .region(Region.of(region))
                .credentialsProvider(DefaultCredentialsProvider.create())
                .build();
    }
}
