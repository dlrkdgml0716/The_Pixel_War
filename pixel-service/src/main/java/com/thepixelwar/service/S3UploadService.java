package com.thepixelwar.service;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.PutObjectRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class S3UploadService {

    private final AmazonS3 amazonS3;

    @Value("${cloud.aws.s3.bucket}")
    private String bucket;

    public String uploadBlueprint(MultipartFile multipartFile) throws IOException {
        // 1. 파일이 비어있는지 확인
        if (multipartFile.isEmpty()) {
            throw new IllegalArgumentException("이미지 파일이 존재하지 않습니다.");
        }

        // 2. 파일 이름 중복 방지를 위한 UUID 생성 (예: 1234abcd_akatsuki.png)
        String originalFilename = multipartFile.getOriginalFilename();
        String uniqueFileName = "blueprints/" + UUID.randomUUID() + "_" + originalFilename;

        // 3. 파일의 메타데이터(크기, 확장자 등) 설정
        ObjectMetadata metadata = new ObjectMetadata();
        metadata.setContentLength(multipartFile.getSize());
        metadata.setContentType(multipartFile.getContentType());

        // 4. S3 양동이에 파일 업로드! (bucket 이름, 파일 이름, 파일 데이터, 메타데이터)
        amazonS3.putObject(new PutObjectRequest(bucket, uniqueFileName, multipartFile.getInputStream(), metadata));

        // 5. 업로드된 파일의 S3 접근 URL을 텍스트로 반환 (이 URL이 DB에 저장됨)
        return amazonS3.getUrl(bucket, uniqueFileName).toString();
    }
}