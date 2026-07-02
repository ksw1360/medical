# medical

의료영상 DICOM과 AI 연동 개발

# MedicalApplication — 웹 DICOM AI 판독 보조 시스템

> 한울병원 시나리오 기반 — DICOM 의료영상에 AI 추론을 붙여 소견을 도출하고,
> 결과를 Secondary Capture(SC)로 PACS(Orthanc)에 회신하며,
> LLM(Bedrock Claude)으로 판독 소견서 초안까지 생성하는 end-to-end 시스템.

DICOM 영상을 업로드하면 **수신 → 전처리 → 추론 → 후처리 → 회신 → 표시**의 6단계 파이프라인이
한 번에 돌아가고, 추론 결과는 Report로 DB에 저장되어 재추론 없이 다시 볼 수 있다.
의사가 소견 메모를 남기면 AI 결과와 함께 LLM에 전달해 한국어 판독 소견서 초안을 생성하고,
판독의가 검토 후 확정하는 워크플로우까지 지원한다.
추론 파이프라인 전체가 **Java 단일 스택**(dcm4che + ONNX Runtime)으로 구현되어 있어
별도 Python 마이크로서비스 없이 동작한다.

---

## 주요 기능

- **DICOM 수신·저장** — multipart / DICOMweb STOW-RS / ZIP 일괄 업로드, PS3.15 비식별화, S3 저장, Patient–Study–Series–Image 4계층 메타데이터 영속화
- **AI 추론 파이프라인** — 윈도잉·리사이즈·정규화 전처리부터 ONNX 추론까지 Java 단독 처리. 단건 / Series 단위 / Study 단위 일괄 추론 지원 (슬라이스 단위 실패 허용)
- **SC 회신** — 추론 소견을 영상에 번인한 Secondary Capture를 새 SOP UID로 생성해 S3에 저장하고, Orthanc(PACS)에 STOW-RS로 회신
- **판독 리포트** — AI 결과를 Study별 Report로 DB 저장(upsert), 의사 소견 입력, LLM(Bedrock Claude) 판독 소견서 초안 생성, 판독 확정
- **조회·운영** — 검사 목록 검색(키워드/모달리티/기간), S3 경로 기반 검색, DICOM 다운로드, PNG 미리보기, 스토리지·모달리티 통계
- **API 문서** — Swagger(OpenAPI) 어노테이션 기반 문서화

---

## 기술 스택

| 영역           | 기술                                                          |
| ------------ | ----------------------------------------------------------- |
| Backend      | Spring Boot 4.1, Java 21, Spring Data JPA, Spring Security  |
| DICOM        | dcm4che 5.34 (core / imageio / deident / mime)              |
| AI Inference | ONNX Runtime 1.20 (Java)                                    |
| LLM          | AWS Bedrock (Claude) — 판독 소견서 초안 생성                   |
| PACS         | Orthanc — DICOMweb STOW-RS 회신                              |
| Database     | MySQL                                                        |
| Storage      | AWS S3 (원본 + AI 결과 SC)                                    |
| Auth         | JWT, OAuth2 (Kakao / Google / Naver)                        |

---

## 시스템 아키텍처

```
┌──────────────────────┐      REST / JSON      ┌──────────────────────┐
│      프론트엔드        │ ───────────────────► │      게이트웨이        │
│  업로드 · 결과 · 뷰어  │                       │  Security · JWT·CORS │
└──────────────────────┘                       │     (port 5000)      │
                                               └──────────┬───────────┘
                                                          │
        ┌─────────────────────────────────────────────────┴──────────────────┐
        │                        Spring Boot 백엔드                           │
        │  ┌──────────────────┐ ┌───────────────────┐ ┌───────────────────┐  │
        │  │   수신·저장 계층   │ │  AI 파이프라인 계층 │ │   리포트 계층      │  │
        │  │ Upload·Ingest    │ │ Preprocessor      │ │ ReportService     │  │
        │  │ Deident·Storage  │ │ Inference·ScWriter│ │ BedrockLlmService │  │
        │  │ 4계층 엔티티       │ │ Preview           │ │ (LLM 소견서)      │  │
        │  └────────┬─────────┘ └─────────┬─────────┘ └─────────┬─────────┘  │
        └───────────┼─────────────────────┼─────────────────────┼────────────┘
                    ▼                     ▼                     ▼
          ┌─────────────────┐   ┌─────────────────┐   ┌─────────────────┐
          │      MySQL      │   │     AWS S3      │   │  Orthanc (PACS) │
          │ Patient·Study·  │   │ 원본 dcm ·       │   │  SC STOW-RS 회신│
          │ Series·Image·   │   │ ai-sc/ (SC dcm) │   │                 │
          │ Report          │   │                 │   │ AWS Bedrock     │
          └─────────────────┘   └─────────────────┘   │ (Claude LLM)    │
                                                      └─────────────────┘
```

### AI 추론 6단계 파이프라인

```
① 수신    업로드 · 비식별화 · S3/DB 저장       DicomUploadController · DicomIngestService
   ▼
② 전처리   윈도잉 · 리사이즈 · 정규화           Preprocessor
   ▼
③ 추론    ONNX Runtime · softmax             InferenceService
   ▼
④ 후처리   소견 도출 · 라벨 · 확신도            InferenceService.InferenceResult
   ▼
⑤ 회신    SC 생성(새 UID·소견 번인) · S3 저장   ScWriter · DicomStorageService
          Orthanc STOW-RS 회신 · Report 저장   OrthancService · AiResultController
   ▼
⑥ 표시    PNG 실시간 변환 · 뷰어 렌더           DicomPreviewController
```

> PNG는 파일로 저장하지 않는다. 조회 시점에 S3의 dcm을 내려받아 실시간 변환해 응답한다.

### 판독 리포트 워크플로우

```
AI 추론 (/api/ai/result)
   ▼  Report upsert (Study 1:1)
의사 소견 입력 (PUT /api/reports/{studyId}/opinion)
   ▼
LLM 소견서 초안 생성 (POST /api/reports/{studyId}/generate)
   │  AI 확률 + 의사 메모 + 검사 정보 → Bedrock Claude → 한국어 소견서
   ▼
판독 확정 (POST /api/reports/{studyId}/confirm)
```

---

## 프로젝트 구조

```
src/main/java/com/dicom/medical/
├── controller/
│   ├── DicomUploadController     # ① 업로드 (multipart / STOW-RS)
│   ├── DicomZipUploadController  # ① ZIP 일괄 업로드 (zip-slip 방어)
│   ├── DicomPathController       # id → S3 key 조회
│   ├── InferenceController       # ③④⑤ 추론 (단건 / Series / Study 일괄)
│   ├── AiResultController        # SC+SR 통합 결과 + Report 저장
│   ├── ReportController          # LLM 소견서 생성 · 의사 소견 · 확정
│   ├── DicomPreviewController    # ⑥ DICOM → PNG 실시간 변환
│   ├── DicomDownloadController   # 원본/SC .dcm 다운로드
│   ├── DicomSearchController     # S3 경로 기반 검색
│   ├── StudyController           # 검사 목록 · 영상/시리즈 조회
│   ├── DicomImageController      # SOP 메타데이터 조회
│   └── AdminStatsController      # 스토리지·모달리티·DELFLAG 통계
├── service/
│   ├── DicomIngestService        # 수신·검증·비식별·계층 저장 오케스트레이션
│   ├── DicomDeidentifyService    # 비식별화 (dcm4che DeIdentifier, PS3.15)
│   ├── DicomStorageService       # S3 저장 / 임시 다운로드 / 스트림
│   ├── Preprocessor              # ② 전처리 (윈도잉·리사이즈·정규화)
│   ├── InferenceService          # ③ ONNX 추론
│   ├── ScWriter                  # ⑤ Secondary Capture 생성
│   ├── OrthancService            # PACS STOW-RS 회신 게이트웨이
│   ├── ReportService             # 리포트 생성·조회·소견·확정
│   ├── BedrockLlmService         # Bedrock Converse API 래퍼
│   ├── StudyQueryService         # 검사 목록 검색
│   ├── StorageStatsService       # DB+S3 용량 집계
│   └── AiPipelineService         # 전처리+추론 파사드
├── entity/                       # Patient · Study · Series · DicomImage · Report
├── repository/                   # Spring Data JPA 레포지토리
├── dto/                          # request / respond DTO
├── config/                       # Security · CORS · S3 · Bedrock
├── exception/                    # 전역 예외 처리 (ErrorCode · CustomException)
└── jwt/                          # JWT 인증 필터 · 토큰 프로바이더
```

---

## API

### ① 수신 (업로드)

```
POST /dicomweb/upload                # multipart/form-data (key: files)
→ [1, 2, ...]                        # 저장된 DicomImage id 리스트

POST /dicomweb/studies               # DICOMweb STOW-RS (multipart/related)
→ [1, 2, ...]

POST /dicomweb/upload-zip            # ZIP 업로드 (key: file) — 내부 .dcm 재귀 일괄 ingest
→ { "ingested": 12, "failed": 0, "skipped": 2, "ids": [...] }
```

### 경로 조회

```
GET /api/ai/path/{id}
→ { "id": 1, "sopUid": "2.25...", "path": "<study>/<series>/<sop>.dcm" }   # S3 key
```

### ③④⑤ 추론 + SC 회신

```
POST /api/ai/infer                   # 단건 — SC 생성·S3 저장·Orthanc 회신
Body: { "dicomPath": "<S3 key>" }
→ { "result": { "label": "이상(Abnormal)", "confidence": 0.58, ... },
    "scFile": "ai-sc/2.25....dcm" }

POST /api/ai/infer/series/{seriesId} # Series 전체 일괄 추론·집계
POST /api/ai/infer/study/{studyId}   # Study 전체 (Series별 그룹핑 집계)
→ { "total": 30, "abnormalCount": 4, "maxAbnormal": 0.91, "overall": "이상 의심", "slices": [...] }
```

### SC+SR 통합 결과 (Report 저장)

```
POST /api/ai/result
Body: { "dicomPath": "<S3 key>" }
→ { "studyId": 3, "saved": true,
    "sr":  { "label": ..., "abnormal": ..., "confidence": ..., "overall": ... },
    "sc":  { "key": "ai-sc/....dcm", "previewUrl": "/api/ai/preview?path=..." },
    "original": { "key": "...", "previewUrl": "..." } }
# 결과는 Report에 upsert — GET /api/reports/{studyId} 로 재추론 없이 재열람
```

### 판독 리포트

```
POST /api/reports/{studyId}/generate   # LLM(Bedrock Claude) 소견서 초안 생성·저장
GET  /api/reports/{studyId}            # 리포트 조회
PUT  /api/reports/{studyId}/opinion    # 의사 소견(doctorOpinion)·이름 저장/수정
POST /api/reports/{studyId}/confirm    # 판독 확정
```

### 검사 조회

```
GET /api/studies?keyword=&modality=&from=&to=   # 검사 목록 검색 (최신순)
GET /api/studies/{studyId}/images               # 검사별 영상 목록
GET /api/studies/{studyId}/series               # 검사별 시리즈 목록
GET /api/dicom/search?yearMonth=201702&modality=CT   # S3 경로 기반 검색
GET /image/{uuid}                               # SOP 메타데이터 (rows·columns·windowing 등)
```

### ⑥ 표시 · 다운로드

```
GET /api/ai/preview?path={S3 key}    → image/png    # 원본/SC 실시간 PNG 변환
GET /api/dicom/download?path={S3 key} → .dcm 첨부파일
```

### 운영 통계

```
GET /api/admin/stats/storage         # MySQL + S3 용량 합산
GET /api/admin/stats/modality        # 모달리티별 검사 통계
GET /api/admin/stats/delflag         # DELFLAG 현황
```

---

## 데이터 모델

```
Patient 1 ── N Study 1 ── N Series 1 ── N DicomImage (s3Key)
                │
                └── 1:1 Report
                     ├── AI 결과: aiAbnormal · aiOverall · aiResultJson · aiInferredAt · scKey
                     ├── LLM 소견서: aiReportText
                     └── 의사: doctorName · doctorOpinion · confirmed · confirmedAt
```

DB엔 메타데이터와 S3 key만 저장하고, 픽셀 데이터(원본 dcm·SC dcm)는 전부 S3에 둔다.

---

## 실행 방법

### 사전 요구사항

- JDK 21
- MySQL (기본 DB명 `Medical`, 미존재 시 자동 생성)
- AWS 계정 — S3 버킷(`medical-dicom-store`), Bedrock 모델 접근 권한
- Orthanc (선택 — 없으면 STOW 회신만 실패 로그, 추론은 정상 동작)
- ONNX 모델 파일 → `models/chest_classifier.onnx` (입력 `[1,1,224,224]` / 출력 `[1,2]`)

### 환경 변수 (`.env`)

```
DB_HOST=localhost
DB_PORT=3306
DB_NAME=Medical
DB_USER=...
DB_PASSWORD=...
JWT_SECRET=...
JWT_REFRESH_EXPIRATION=...
# AWS 자격증명(S3·Bedrock), OAuth(Kakao/Google/Naver), 메일 설정은 application.yaml 참조
```

### 백엔드

```
./gradlew bootRun            # http://localhost:5000
```

---

## 동작 흐름 (데모)

1. DICOM(.dcm 또는 ZIP) 업로드 → 비식별화 → S3 저장 + 4계층 DB 영속화
2. 추론 실행 → SC 생성 → S3 저장 + Orthanc 회신 + Report DB 저장
3. 판독 결과(정상/이상 + 확률)와 소견 번인 SC 이미지를 미리보기로 확인
4. 의사 소견 입력 → LLM 소견서 초안 생성 → 검토 후 판독 확정

> 현재 더미 모델 기반 데모. 표시되는 확률은 파이프라인 검증용이며 의학적 판단 근거가 아니다.

---

## 설계 결정

- **Java 단일 스택** — 추론까지 Java(ONNX Runtime)로 처리해 Python 마이크로서비스 의존성 제거
- **SC over SR** — 추론 결과를 Secondary Capture(영상 번인)로 저장해 뷰어에서 시각적 확인 용이. 추론 텍스트는 Report(`aiResultJson`)로 별도 보존
- **DB엔 key만** — 픽셀은 S3에, DB엔 메타데이터와 S3 key만 저장
- **표준 비식별화** — dcm4che `DeIdentifier`로 PS3.15 프로파일 기반 비식별화
- **Report 1:1 upsert** — Study당 리포트 하나로 AI 결과·의사 소견·LLM 소견서를 한 곳에 누적, 재추론 없이 재열람
- **부분 실패 허용** — 일괄 추론 시 원본 유실/추론 실패 슬라이스만 실패 표시(`abnormal=-1`)하고 전체는 정상 응답
