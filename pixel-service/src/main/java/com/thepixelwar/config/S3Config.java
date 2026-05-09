package com.thepixelwar.config;

import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration // 설정 파일임을 명시
public class S3Config {

    @Value("${cloud.aws.credentials.access-key}") // 환경변수를 위한 어노테이션
    private String accessKey;

    @Value("${cloud.aws.credentials.secret-key}")
    private String secretKey;

    @Value("${cloud.aws.region.static}")
    private String region;

    @Bean
    public AmazonS3 amazonS3Client() {
        // 환경변수에서 가져온 accessKey, secretKey로 S3 서버에 접근 가능한 변수를 만듦
        AWSCredentials credentials = new BasicAWSCredentials(accessKey, secretKey);

        return AmazonS3ClientBuilder
                .standard()
                .withCredentials(new AWSStaticCredentialsProvider(credentials)) // 권한 넘겨주고
                .withRegion(region) // S3 데이터센터 설정
                .build(); // S3와 통신할 준비를 마친 AmazonS3 클라이언트 객체가 탄생
    }
}