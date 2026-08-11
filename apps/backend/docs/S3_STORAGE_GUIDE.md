# S3 파일 저장소 운영 가이드

버킷은 비공개로 유지하고 Backend EC2에는 `s3:GetObject`, `s3:PutObject`, `s3:DeleteObject`,
`s3:PutObjectTagging` 최소 권한을 가진 Instance Profile을 연결합니다. S3 `HeadObject` 요청은
별도의 IAM 액션이 아니라 `s3:GetObject`로 인가됩니다. 장기 Access Key는 애플리케이션 환경변수에
저장하지 않습니다.

```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Action": [
        "s3:GetObject",
        "s3:PutObject",
        "s3:DeleteObject",
        "s3:PutObjectTagging"
      ],
      "Resource": [
        "arn:aws:s3:::ktb-chat-files-prod/chat/*",
        "arn:aws:s3:::ktb-chat-files-prod/profiles/*"
      ]
    }
  ]
}
```

예시의 버킷 이름은 실제 버킷 ARN으로 교체합니다. SSE-KMS를 사용하는 버킷이면 KMS 키 정책과
`kms:GenerateDataKey`, `kms:Decrypt` 권한도 별도로 필요합니다.

## 브라우저 직접 업로드 활성화 전 준비

아래 예시의 Origin을 실제 Frontend HTTPS 주소로 교체해 버킷 CORS에 적용합니다.

```json
[
  {
    "AllowedOrigins": ["https://chat.example.com"],
    "AllowedMethods": ["PUT", "GET", "HEAD"],
    "AllowedHeaders": ["Content-Type", "x-amz-tagging"],
    "ExposeHeaders": ["ETag"],
    "MaxAgeSeconds": 3600
  }
]
```

브라우저가 업로드하고 완료 API를 호출하지 않은 객체에는 `upload-state=pending` 태그가 유지됩니다.
이 태그가 붙은 객체만 정리하는 Lifecycle 정책을 적용합니다. 정상 완료 객체는 `completed`로 바뀌므로
prefix만으로 만료 규칙을 만들면 안 됩니다. 정리 기간은 업로드 세션 만료 시간(1시간)보다 길어야 합니다.

Backend IAM에는 기본 객체 권한 외에 완료 태그 갱신을 위한 `s3:PutObjectTagging` 권한도 필요합니다.

## 활성화 순서

1. CORS와 Lifecycle 적용을 확인합니다.
2. Backend에 `S3_DIRECT_UPLOAD_ENABLED=true`를 적용합니다.
3. Frontend 빌드에 `NEXT_PUBLIC_DIRECT_S3_UPLOAD=true`를 적용합니다.
4. 채팅 첨부와 프로필 이미지 업로드 E2E를 실행합니다.
5. 오류율과 Backend Network/CPU를 확인합니다.

문제가 발생하면 Frontend 플래그를 `false`로 되돌려 기존 multipart 업로드 경로를 사용합니다.
기존 multipart API는 호환 및 롤백 경로로 계속 유지됩니다.
