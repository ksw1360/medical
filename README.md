# medical

의료영상 DICOM과 AI 연동 개발

# MedicalApplication — 웹 DICOM AI 판독 보조 시스템

> 한울병원 시나리오 기반 — DICOM 의료영상에 AI 추론을 붙여 소견을 도출하고,
> 결과를 Secondary Capture(SC)로 PACS(Orthanc)에 회신해 뷰어에 표시하는 end-to-end 시스템.

DICOM 영상을 업로드하면 **수신 → 전처리 → 추론 → 후처리 → 회신 → 표시**의 6단계 파이프라인이
한 번에 돌아가며, AI 소견이 번인된 Secondary Capture 이미지를 자동 생성해 웹 뷰어에 표시한다.
추론 결과에 의사 소견을 더해 **Bedrock LLM이 한국어 판독 소견서**를 생성·확정하는 리포트
워크플로우까지 포함한다. 추론 파이프라인 전체가 **Java 단일 스택**(dcm4che + ONNX Runtime)으로
구현되어 있어 별도 Python 마이크로서비스 없이 동작한다.

---

## 주요 기능

- **DICOM 수신·저장** — multipart / ZIP / DICOMweb STOW-RS 업로드, 비식별화, Patient–Study–Series–Image 4계층 저장, **AWS S3 저장**(DB엔 S3 key만)
- **AI 추론 파이프라인** — 윈도잉·리사이즈·정규화 전처리부터 ONNX 추론까지 Java 단독 처리, 단일 영상 / Series / Study 단위 일괄 추론
- **SC 회신** — 추론 소견을 영상에 번인한 Secondary Capture를 새 SOP UID로 생성, S3 업로드 + **Orthanc STOW 회신**
- **LLM 판독 소견서** — AI 결과(SR) + 의사 소견 메모를 종합해 **AWS Bedrock**(Claude)이 한국어 소견서 생성 → 조회 → 의사 소견 저장 → 판독 확정
- **웹 뷰어** — 원본/SC를 PNG로 변환해 브라우저에 표시, 정상/이상 판독 결과 시각화
- **검사 관리** — 검사 목록·검색, 시리즈/영상 조회, 소프트 삭제·복구(휴지통), 환자 조회, DICOM 다운로드
- **관리자 기능** — 대시보드 통합 통계, 장애 모니터링, 스토리지(DB+S3)·모달리티·삭제 현황 통계

---

## 기술 스택

| 영역           | 기술                                                         |
| ------------ | ---------------------------------------------------------- |
| Backend      | Spring Boot 4.1, Java 21, Spring Data JPA, Spring Security |
| DICOM        | dcm4che 5.34 (core / imageio / deident / mime)             |
| AI Inference | ONNX Runtime 1.20 (Java) — 흉부 이진 분류 + X-ray 멀티라벨      |
| LLM          | AWS Bedrock Converse API (Claude)                          |
| PACS         | Orthanc (STOW 회신)                                         |
| Database     | MySQL                                                      |
| Storage      | AWS S3 (원본·SC 모두 S3, DB엔 메타데이터와 S3 key만)           |
| API Docs     | springdoc-openapi (Swagger UI, Basic Auth)                 |
| Auth         | JWT, OAuth2 (Kakao / Google / Naver)                       |

---

## 시스템 아키텍처

```
┌──────────────────────┐      REST / JSON      ┌──────────────────────┐
│      프론트엔드       │ ───────────────────►  │       게이트웨이      │
│   (별도 저장소)       │                       │  Security · JWT·CORS │
│  업로드 · 결과 · 뷰어 │                       │     (port 5000)      │
└──────────────────────┘                       └──────────┬───────────┘
                                                          │
              ┌───────────────────────────────────────────┴───────────────┐
              │                  Spring Boot 백엔드                        │
              │  ┌────────────────────────┐  ┌──────────────────────────┐  │
              │  │     수신 · 저장 계층    │  │     AI 파이프라인 계층    │  │
              │  │ Upload·Ingest·Deident  │  │ Preprocessor·Inference   │  │
              │  │ Storage · 4계층 엔티티  │  │ ScWriter · Preview       │  │
              │  └───────────┬────────────┘  └────────────┬─────────────┘  │
              │  ┌────────────────────────┐  ┌──────────────────────────┐  │
              │  │     리포트 계층         │  │     관리자 계층           │  │
              │  │ Report·BedrockLlm      │  │ Dashboard·Monitoring     │  │
              │  └───────────┬────────────┘  │ Stats·Lifecycle          │  │
              └──────────────┼───────────────┴────────────┬──────────────┘
                             ▼                             ▼
        ┌───────────┐  ┌───────────┐  ┌───────────┐  ┌───────────┐
        │   MySQL   │  │  AWS S3   │  │  Bedrock  │  │  Orthanc  │
        │ 메타·리포트│  │ 원본 · SC │  │ LLM 소견서│  │ PACS 회신 │
        └───────────┘  └───────────┘  └───────────┘  └───────────┘
```

### AI 추론 6단계 파이프라인

```
① 수신    업로드 · 비식별화 · S3 저장 · DB 저장   DicomUploadController · DicomIngestService
   ▼
② 전처리   윈도잉 · 리사이즈 · 정규화              Preprocessor
   ▼
③ 추론    ONNX Runtime · softmax                InferenceService · XrayInferenceService
   ▼
④ 후처리   소견 도출 · 라벨 · 확신도               InferenceService.InferenceResult
   ▼
⑤ 회신    SC 생성 · 새 UID · 소견 번인            ScWriter → S3 + Orthanc STOW
   ▼
⑥ 표시    PNG 변환 · 뷰어 렌더                   DicomPreviewController
```

---

## 프로젝트 구조

```
src/main/java/com/dicom/medical/
├── controller/
│   ├── DicomUploadController     # ① 업로드 (multipart / STOW-RS)
│   ├── DicomZipUploadController  # ① ZIP 일괄 업로드
│   ├── DicomPathController       # id → S3 key 조회
│   ├── InferenceController       # ③④⑤ 추론 (단일 / Series / Study 일괄)
│   ├── AiResultController        # SC+SR 통합 결과 + Report 저장
│   ├── ReportController          # LLM 소견서 생성·조회·소견·확정
│   ├── DicomPreviewController    # ⑥ DICOM → PNG 표시
│   ├── DicomImageController      # 이미지 메타 조회
│   ├── DicomSearchController     # 연월·모달리티 검색
│   ├── DicomDownloadController   # DICOM 다운로드
│   ├── StudyController           # 검사 목록·시리즈·영상 조회
│   ├── StudyMetadataController   # Study 메타데이터 (뷰어용)
│   ├── StudyLifecycleController  # 소프트 삭제·복구·휴지통
│   ├── PatientController         # 환자 정보 조회
│   ├── DashboardController       # 관리자 대시보드 통계
│   ├── MonitoringController      # 장애 모니터링
│   └── AdminStatsController      # 스토리지·모달리티·삭제 통계
├── service/
│   ├── DicomIngestService        # 수신·검증·계층 저장 오케스트레이션
│   ├── DicomDeidentifyService    # 비식별화 (dcm4che DeIdentifier)
│   ├── DicomStorageService       # S3 업로드/다운로드 · key 해석
│   ├── Preprocessor              # ② 전처리 (윈도잉·리사이즈·정규화)
│   ├── InferenceService          # ③ ONNX 추론 (흉부 이진 분류)
│   ├── XrayInferenceService      # ③ ONNX 추론 (X-ray 멀티라벨)
│   ├── ScWriter                  # ⑤ Secondary Capture 생성
│   ├── AiPipelineService         # 전처리+추론 파사드
│   ├── OrthancService            # Orthanc STOW 회신
│   ├── ReportService             # 판독 리포트 워크플로우
│   ├── BedrockLlmService         # Bedrock Converse API 래퍼
│   ├── DashboardService · StorageStatsService · UploadMonitor
│   └── StudyQueryService · StudyMetadataService · StudyLifecycleService
├── entity/                       # Patient · Study · Series · DicomImage · Report
├── repository/                   # Spring Data JPA 레포지토리
├── config/                       # Security · CORS · S3 · Bedrock
└── jwt/                          # JWT 인증 필터·토큰 프로바이더
```

---

## API

전체 명세는 Swagger UI(`/swagger-ui.html`, Basic Auth)에서 확인. 주요 흐름만 요약:

### ① 업로드

```
POST /dicomweb/upload            # multipart (key: files)
POST /dicomweb/upload-zip        # ZIP 일괄 업로드
POST /dicomweb/studies           # DICOMweb STOW-RS
→ [1, 2, ...]                    # 저장된 DicomImage id 리스트
```

### 경로(S3 key) 조회

```
GET /api/ai/path/{id}
→ { "id": 1, "sopUid": "2.25...", "path": "2026/07/CT/....dcm" }   # path = S3 key
```

### ③④⑤ 추론 + SC 회신

```
POST /api/ai/infer                        # 단일 영상. body: { "dicomPath": "<S3 key>" }
→ { "result": { "label": "...", "confidence": 0.58, ... }, "scFile": "ai-sc/....dcm" }

POST /api/ai/infer/series/{seriesId}      # Series 전체 슬라이스 일괄 추론·집계
POST /api/ai/infer/study/{studyId}        # Study 전체 (Series별 그룹핑 + 전체 집계)

POST /api/ai/result                       # SC+SR 통합 결과 — Report에 저장(upsert)되어
                                          # 재추론 없이 GET /api/reports/{studyId}로 재조회 가능
```

### 판독 소견서 (LLM)

```
POST /api/reports/generate                # body: { studyId, userMemo } → Bedrock이 한국어 소견서 생성
                                          # 선행 조건: 해당 study에 /api/ai/result 실행되어 있어야 함
GET  /api/reports/{studyId}               # 리포트 조회
PUT  /api/reports/{studyId}/opinion       # 의사 소견 저장/수정
POST /api/reports/{studyId}/confirm       # 판독 확정
```

### ⑥ 표시

```
GET /api/ai/preview?path={scKey}
→ image/png                               # 소견 번인된 PNG
```

### 검사·환자 관리

```
GET    /api/studies                       # 검사 목록 조회 / 검색
GET    /api/studies/{studyId}/series      # 시리즈 목록 (카드 UI용)
GET    /api/studies/{studyId}/images      # 영상 목록
GET    /api/studies/{studyId}/metadata    # 뷰어용 메타데이터
DELETE /api/studies/{studyId}             # 소프트 삭제
PATCH  /api/studies/{studyId}/restore     # 복구
GET    /api/studies/deleted               # 휴지통
GET    /api/patients/{patientId}          # 환자 정보 (해시 id → 별칭)
GET    /api/dicom/search                  # 연월·모달리티 폴더 검색
GET    /api/dicom/download                # DICOM 다운로드
```

### 관리자

```
GET /api/admin/dashboard                  # 통합 통계
GET /api/admin/monitoring                 # 장애 모니터링 현황
GET /api/admin/stats/storage              # DB + S3 용량 (GB)
GET /api/admin/stats/modality             # 모달리티별 검사 통계
GET /api/admin/stats/delflag              # 삭제/정상 건수 + 파일 용량
```

---

## 실행 방법

### 사전 요구사항

- JDK 21
- MySQL (기본 DB명 `Medical`, 미존재 시 자동 생성)
- AWS 자격 증명 (S3 · Bedrock)
- (선택) Orthanc — `http://localhost:8042`, 미기동 시 SC 회신만 스킵됨

> ONNX 모델(`chest_classifier.onnx`, `xray_multilabel.onnx`)은
> `src/main/resources/models/`에 포함되어 있어 별도 준비 불필요.

### 환경 변수 (`.env`)

```
DB_HOST=localhost
DB_PORT=3306
DB_NAME=Medical
DB_USER=...
DB_PASSWORD=...
JWT_SECRET=...
AWS_ACCESS_KEY=...
AWS_SECRET_KEY=...
AWS_S3_REGION=...
AWS_BEDROCK_REGION=ap-northeast-2
# OAuth(Kakao/Google/Naver), Swagger Basic Auth 등은 application.yaml 참조
```

### 백엔드

```
./gradlew bootRun            # http://localhost:5000
```

프론트엔드(Next.js)는 별도 저장소에서 관리.

---

## 동작 흐름 (데모)

1. 웹에서 DICOM(.dcm) 파일 선택 후 **AI 판독 시작**
2. 업로드(S3 저장) → key 조회 → 추론 → SC 생성·Orthanc 회신이 순차 실행
3. 판독 결과(정상/이상 + 확률)와 소견이 번인된 SC 이미지가 화면에 표시
4. **판독 소견서 작성** 버튼 → 저장된 AI 결과 + 의사 메모로 LLM 소견서 생성 → 확정

### 엔드포인트 호출 순서

```
① POST /dicomweb/upload                  # 업로드·비식별화·S3 저장 → 이미지 id 리스트
② GET  /api/ai/path/{id}                 # id → S3 key 조회
③ POST /api/ai/result                    # 추론 + SC 생성·회신 + Report 저장 (SR+SC 통합 응답)
   └ 또는 POST /api/ai/infer             #   결과 저장 없이 추론만 할 때
④ GET  /api/ai/preview?path={scKey}      # SC → PNG 변환해 뷰어 표시
⑤ POST /api/reports/generate             # (선택) LLM 판독 소견서 생성
⑥ PUT  /api/reports/{studyId}/opinion    # (선택) 의사 소견 저장
⑦ POST /api/reports/{studyId}/confirm    # (선택) 판독 확정
```

> 현재 더미 모델 기반 데모. 표시되는 확률은 파이프라인 검증용이며 의학적 판단 근거가 아니다.

---

## 설계 결정

- **Java 단일 스택** — 추론까지 Java(ONNX Runtime)로 처리해 Python 마이크로서비스 의존성 제거
- **SC over SR** — 추론 결과를 Secondary Capture(영상 번인)로 저장해 데모 시 시각적 확인 용이
- **DB엔 key만** — 픽셀은 S3에, DB엔 메타데이터와 S3 key만 저장
- **모델은 클래스패스에** — ONNX 모델을 jar에 포함해 배포 환경에서 별도 파일 배치 불필요
- **부분 실패 허용** — Series/Study 일괄 추론 시 일부 슬라이스가 원본 없음/추론 실패여도 해당 슬라이스만 실패 표시하고 전체는 정상 응답
- **표준 비식별화** — dcm4che `DeIdentifier`로 PS3.15 프로파일 기반 비식별화
