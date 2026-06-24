# medical
의료영상 DICOM과 AI 연동 개발
# MedicalApplication — 웹 DICOM AI 판독 보조 시스템

> 한울병원 시나리오 기반 — DICOM 의료영상에 AI 추론을 붙여 소견을 도출하고,
> 결과를 Secondary Capture(SC)로 PACS에 회신해 뷰어에 표시하는 end-to-end 시스템.

DICOM 영상을 업로드하면 **수신 → 전처리 → 추론 → 후처리 → 회신 → 표시**의 6단계 파이프라인이
한 번에 돌아가며, AI 소견이 번인된 Secondary Capture 이미지를 자동 생성해 웹 뷰어에 표시한다.
추론 파이프라인 전체가 **Java 단일 스택**(dcm4che + ONNX Runtime)으로 구현되어 있어
별도 Python 마이크로서비스 없이 동작한다.

---

## 주요 기능

- **DICOM 수신·저장** — multipart / DICOMweb STOW-RS 업로드, 비식별화, Patient–Study–Series–Image 4계층 저장
- **AI 추론 파이프라인** — 윈도잉·리사이즈·정규화 전처리부터 ONNX 추론까지 Java 단독 처리
- **SC 회신** — 추론 소견을 영상에 번인한 Secondary Capture를 새 SOP UID로 생성
- **웹 뷰어** — 원본/SC를 PNG로 변환해 브라우저에 표시, 정상/이상 판독 결과 시각화

---

## 기술 스택

| 영역 | 기술 |
|------|------|
| Backend | Spring Boot 4.1, Java 21, Spring Data JPA, Spring Security |
| DICOM | dcm4che 5.34 (core / imageio / deident / mime) |
| AI Inference | ONNX Runtime 1.20 (Java) |
| Database | MySQL |
| Storage | 로컬 파일시스템 (`dicom-store/`), AWS S3 연동 준비 |
| Frontend | Next.js (App Router), React, Tailwind CSS |
| Auth | JWT, OAuth2 (Kakao / Google / Naver) |

---

## 시스템 아키텍처

```
┌──────────────────────┐      REST / JSON      ┌──────────────────────┐
│      프론트엔드       │ ───────────────────►  │       게이트웨이      │
│  Next.js · Tailwind  │                       │  Security · JWT·CORS │
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
              └──────────────┼────────────────────────────┼────────────────┘
                             ▼                             ▼
                   ┌───────────────────┐         ┌───────────────────┐
                   │      MySQL        │         │     로컬 저장소    │
                   │ Patient·Study·    │         │ dicom-store/      │
                   │ Series·Image 메타 │         │ models/ · SC      │
                   └───────────────────┘         └───────────────────┘
```

### AI 추론 6단계 파이프라인

```
① 수신    업로드 · 비식별화 · DB 저장        DicomUploadController · DicomIngestService
   ▼
② 전처리   윈도잉 · 리사이즈 · 정규화          Preprocessor
   ▼
③ 추론    ONNX Runtime · softmax            InferenceService
   ▼
④ 후처리   소견 도출 · 라벨 · 확신도           InferenceService.InferenceResult
   ▼
⑤ 회신    SC 생성 · 새 UID · 소견 번인         ScWriter
   ▼
⑥ 표시    PNG 변환 · 뷰어 렌더               DicomPreviewController
```

---

## 프로젝트 구조

```
src/main/java/com/dicom/medical/
├── controller/
│   ├── DicomUploadController     # ① 업로드 (multipart / STOW-RS)
│   ├── DicomPathController       # id → 파일 경로 조회
│   ├── InferenceController       # ③④⑤ 추론·후처리·SC 회신
│   ├── DicomPreviewController    # ⑥ DICOM → PNG 표시
│   └── DicomImageController      # 이미지 메타 조회
├── service/
│   ├── DicomIngestService        # 수신·검증·계층 저장 오케스트레이션
│   ├── DicomDeidentifyService    # 비식별화 (dcm4che DeIdentifier)
│   ├── DicomStorageService       # 로컬 파일 저장 / 경로 해석
│   ├── Preprocessor              # ② 전처리 (윈도잉·리사이즈·정규화)
│   ├── InferenceService          # ③ ONNX 추론
│   ├── ScWriter                  # ⑤ Secondary Capture 생성
│   └── AiPipelineService         # 전처리+추론 파사드
├── entity/                       # Patient · Study · Series · DicomImage
├── repository/                   # Spring Data JPA 레포지토리
├── config/                       # Security · CORS · S3
└── jwt/                          # JWT 인증 필터·토큰 프로바이더
```

---

## API

### ① 업로드
```
POST /dicomweb/upload
Content-Type: multipart/form-data    (key: files)
→ [1, 2, ...]                        # 저장된 DicomImage id 리스트
```

### 경로 조회
```
GET /api/ai/path/{id}
→ { "id": 1, "sopUid": "2.25...", "path": "./dicom-store/.../xxx.dcm" }
```

### ③④⑤ 추론 + SC 회신
```
POST /api/ai/infer
Content-Type: application/json
Body: { "dicomPath": "./dicom-store/.../xxx.dcm" }
→ {
    "result": { "label": "이상(Abnormal)", "confidence": 0.58,
                "probabilities": [0.42, 0.58] },
    "scFile": "dicom-store/ai-sc/2.25....dcm"
  }
```

### ⑥ 표시
```
GET /api/ai/preview?path={scFilePath}
→ image/png                          # 소견 번인된 PNG
```

---

## 실행 방법

### 사전 요구사항
- JDK 21
- MySQL (기본 DB명 `Medical`, 미존재 시 자동 생성)
- ONNX 모델 파일 → `models/chest_classifier.onnx` (입력 `[1,1,224,224]` / 출력 `[1,2]`)

### 환경 변수 (`.env`)
```properties
DB_HOST=localhost
DB_PORT=3306
DB_NAME=Medical
DB_USER=...
DB_PASSWORD=...
JWT_SECRET=...
# OAuth(Kakao/Google/Naver), AWS S3 키 등은 application.yaml 참조
```

### 백엔드
```bash
./gradlew bootRun            # http://localhost:5000
```

### 프론트엔드
```bash
cd frontend
npm install
npm run dev                  # http://localhost:3000/ai-demo
```

---

## 동작 흐름 (데모)

1. 웹에서 DICOM(.dcm) 파일 선택 후 **AI 판독 시작**
2. 업로드 → 경로 조회 → 추론 → SC 생성이 순차 실행
3. 판독 결과(정상/이상 + 확률)와 소견이 번인된 SC 이미지가 화면에 표시

> 현재 더미 모델 기반 데모. 표시되는 확률은 파이프라인 검증용이며 의학적 판단 근거가 아니다.

---

## 설계 결정

- **Java 단일 스택** — 추론까지 Java(ONNX Runtime)로 처리해 Python 마이크로서비스 의존성 제거
- **SC over SR** — 추론 결과를 Secondary Capture(영상 번인)로 저장해 데모 시 시각적 확인 용이
- **DB엔 경로만** — 픽셀은 파일시스템(`dicom-store/`)에, DB엔 메타데이터와 상대 경로(`s3Key`)만 저장
- **표준 비식별화** — dcm4che `DeIdentifier`로 PS3.15 프로파일 기반 비식별화
