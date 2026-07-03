# medical — 웹 DICOM AI 판독 보조 시스템

> DICOM 의료영상을 업로드하면 **수신 → 비식별화 → S3 저장 → AI 추론 → SC 회신 → 결과 표시**의
> 파이프라인이 돌아가고, AI 소견을 LLM이 판독 소견서로 정리해 주는 end-to-end 시스템.
> 추론(ONNX)과 소견서 생성(AWS Bedrock)까지 **Java 단일 스택**으로 구현되어 별도 Python 서비스가 없다.

---

## 주요 기능

- **DICOM 수신·저장** — multipart 업로드, dcm4che `DeIdentifier` 기반 비식별화, Patient–Study–Series–Image 4계층 저장, 원본은 **AWS S3**에 보관(DB엔 메타데이터 + S3 key만).
- **AI 추론 파이프라인** — 윈도잉·리사이즈·정규화 전처리부터 ONNX 추론까지 Java 단독 처리. Series/Study 단위 일괄 추론 지원(슬라이스 실패는 격리 처리).
- **SC 회신** — 추론 소견을 영상에 번인한 Secondary Capture를 새 SOP UID로 생성해 S3 저장.
- **SC+SR 결과창** — 결과 이미지(SC)와 추론 텍스트(SR)를 한 응답으로. 결과는 Report에 저장되어 재열람 가능.
- **LLM 판독 소견서** — AWS Bedrock(Claude)이 저장된 AI 결과 + 의사 소견 메모를 종합해 한국어 소견서 초안 작성.
- **뷰어 메타데이터 API** — Study 단위로 Series/Instance 메타데이터를 계층 반환, 픽셀은 URL로 지연 로딩.
- **운영 대시보드 / 장애 모니터링** — 스토리지·모달리티·DELFLAG 통계 + 시스템 헬스체크 + SC 업로드 성공/실패 집계.

---

## 기술 스택

| 영역 | 기술 |
| --- | --- |
| Backend | Spring Boot 4.1, Java 21, Spring Data JPA, Spring Security |
| DICOM | dcm4che 5.34 (core / imageio / deident / mime) |
| AI 추론 | ONNX Runtime 1.20 (Java) |
| LLM | AWS Bedrock (Claude, Converse API) |
| Storage | AWS S3 (원본 DICOM · SC) |
| Database | MySQL |
| Auth | JWT, OAuth2 (Kakao / Google / Naver) — 개발 단계 일부 permitAll |
| Infra | AWS Elastic Beanstalk, GitHub Actions CI/CD, `.ebextensions`(IAM 정책 관리) |

---

## 아키텍처

```
┌────────────────┐    REST/JSON    ┌───────────────────────────────┐
│    프론트엔드   │ ──────────────► │           백엔드               │
│ (검색/뷰어/     │                 │  Security · JWT · CORS         │
│  대시보드)      │ ◄────────────── │  Controllers · Services        │
└────────────────┘                 └───────────┬──────────┬────────┘
                                                │          │
                                      ┌─────────▼──┐   ┌───▼─────────┐
                                      │   MySQL     │   │   AWS S3    │
                                      │ 4계층 메타  │   │ 원본 · SC   │
                                      └────────────┘   └─────────────┘
                                                │
                                   ┌────────────┼──────────────┐
                                   ▼                            ▼
                          ONNX Runtime (추론)          AWS Bedrock (소견서 LLM)
```

### 파이프라인

```
① 수신     업로드 · 비식별화 · S3 저장 · 4계층 DB      DicomUploadController · DicomIngestService
② 전처리   윈도잉 · 리사이즈 · 정규화                  Preprocessor
③ 추론     ONNX Runtime · softmax                     InferenceService
④ 회신     SC 생성 · 새 UID · 소견 번인 · S3 저장       ScWriter · DicomStorageService
⑤ 결과     SR(텍스트)+SC(이미지 URL) · Report 저장      AiResultController
⑥ 소견서   저장된 SC/SR + 의사 메모 → LLM              ReportService · BedrockLlmService
```

---

## API 요약

### 검사/뷰어
```
GET  /api/studies?keyword=&modality=&from=&to=   검사 목록/검색
GET  /api/studies/{studyId}/images               영상 목록 (windowCenter/Width 포함)
GET  /api/studies/{studyId}/series               Series 목록 + 대표 슬라이스 (카드 UI)
GET  /api/studies/{studyId}/metadata             Study 메타데이터 풀 계층 (뷰어용, pixelDataUrl 지연로딩)
```

### 수신/다운로드
```
POST /dicomweb/upload            multipart 업로드 → [DicomImage id...]
GET  /api/ai/path/{id}           id → S3 key(path) 변환
GET  /api/dicom/download?path=   원본/SC 다운로드 (S3 key로 분기)
GET  /api/ai/preview?path=       DICOM → PNG 미리보기
```

### AI 추론
```
POST /api/ai/infer               단건 추론 + SC 회신
POST /api/ai/infer/series/{id}   Series 단위 추론
POST /api/ai/infer/study/{id}    Study 단위(Series별 그룹핑) 추론
POST /api/ai/result              SC+SR 통합 결과 + Report 저장
```

### 판독 리포트 (LLM)
```
POST /api/reports/generate       { studyId, userMemo } → 저장된 SC/SR + 메모로 소견서 생성
GET  /api/reports/{studyId}      리포트 조회
PUT  /api/reports/{studyId}/opinion   의사 소견 저장
POST /api/reports/{studyId}/confirm   판독 확정
```

### 운영/모니터링
```
GET  /api/admin/dashboard        통계 통합 (counts/modality/delFlag/storage/scUpload)
GET  /api/admin/monitoring       헬스체크(app/db/s3) + SC 업로드 성공/실패
```

---

## 데이터 모델 (4계층)

```
Patient ──1:N── Study ──1:N── Series ──1:N── DicomImage
                  │
                  └──1:1── Report (AI 결과 · SC key · LLM 소견서 · 의사 소견)
```

- 원본 픽셀은 DB에 넣지 않고 **S3 key(`<studyUID>/<seriesUID>/<sopUID>.dcm`)**만 저장.
- SC는 `ai-sc/<newSopUID>.dcm` key로 저장.
- 뷰어 메타데이터용 DICOM 태그(pixelSpacing, rescale, viewPosition 등)는 업로드 시 각 계층 엔티티에 저장.

---

## 실행 방법

### 사전 요구사항
- JDK 21
- MySQL (기본 DB `Medical`)
- ONNX 모델 → `src/main/resources/models/chest_classifier.onnx`
- AWS: S3 버킷(`medical-dicom-store`) + Bedrock 모델 접근

### 환경 변수 (주요)
```
DB_HOST / DB_PORT / DB_NAME / DB_USER / DB_PASSWORD
JWT_SECRET
AWS_S3_REGION / AWS_ACCESS_KEY / AWS_SECRET_KEY
AWS_BEDROCK_REGION      (기본 ap-northeast-2)
AWS_BEDROCK_MODEL_ID    (예: global.anthropic.claude-haiku-4-5-20251001-v1:0)
```

### 빌드/실행/테스트
```
./gradlew bootRun     # http://localhost:5000
./gradlew test        # 유닛 + 컨트롤러 테스트
```

---

## IAM 권한 (중요)

EC2 인스턴스 역할(`aws-elasticbeanstalk-ec2-role`)에 아래 권한 필요.
`.ebextensions`로 코드 관리해 재배포 시 유실 방지.

```
S3      : s3:PutObject, s3:GetObject, s3:DeleteObject  (arn:...:medical-dicom-store/*)
          s3:ListBucket                                (arn:...:medical-dicom-store)
Bedrock : bedrock:InvokeModel                          (*)
```

> Bedrock은 콘솔에서 Anthropic 모델 use case 제출(계정당 1회) 후, 리전 추론 프로파일 ID를 `AWS_BEDROCK_MODEL_ID`로 지정.

---

## 설계 결정

- **Java 단일 스택** — 추론(ONNX) + 소견서(Bedrock)까지 Java로 처리해 Python 마이크로서비스 의존성 제거.
- **DB엔 경로만** — 픽셀은 S3, DB엔 메타데이터 + S3 key. 뷰어는 metadata API로 한 번에 받고 픽셀은 URL로 지연 로딩.
- **표준 비식별화** — dcm4che `DeIdentifier`(PS3.15 프로파일). 환자 식별정보 제거·해시, UID 재생성. 기술 태그는 유지.
- **결과 저장·재열람** — AI 추론(SC/SR)과 LLM 소견서를 Report 1:1로 저장해 재추론 없이 조회.
- **장애 격리** — 슬라이스 단위 추론 실패(원본 없음 등)는 해당 슬라이스만 표시하고 전체는 정상 응답.

> 현재 더미/데모 모델 기반. 표시되는 확률·소견서는 파이프라인 검증용이며 의학적 판단 근거가 아니다.
