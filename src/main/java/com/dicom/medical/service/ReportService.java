package com.dicom.medical.service;

import com.dicom.medical.dto.request.GenerateReportRequest;
import com.dicom.medical.dto.request.OpinionRequest;
import com.dicom.medical.dto.respond.ReportResponse;
import com.dicom.medical.entity.Report;
import com.dicom.medical.entity.Study;
import com.dicom.medical.repository.ReportRepository;
import com.dicom.medical.repository.StudyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.NoSuchElementException;

@Service
@RequiredArgsConstructor
public class ReportService {

    private final ReportRepository reportRepository;
    private final StudyRepository studyRepository;
    private final BedrockLlmService llm;

    private static final String SYSTEM_PROMPT = """
        당신은 영상의학과 판독의를 돕는 임상 판독 보조 AI입니다.
        제공된 AI 추론 결과(SR)와 의사 소견 메모, 검사 정보를 바탕으로
        한국어 판독 소견서 초안을 작성하세요.

        규칙:
        - 아래 4개 섹션으로 구성: [검사 정보] / [AI 분석 요약] / [판독 소견] / [권고 사항]
        - AI 확률은 진단이 아니라 참고 지표임을 전제로 서술한다. 확률이 낮은 소견을 단정하지 않는다.
        - 의사 소견 메모가 있으면 우선 반영하되, AI 결과와 충돌하면 둘 다 언급한다.
        - 과장·확정 진단을 피하고, 임상적 상관관계 확인이 필요하다는 취지를 유지한다.
        - 마지막에 "본 초안은 판독의의 최종 검토·확정이 필요합니다." 문구를 넣는다.
        - 불필요한 서론 없이 소견서 본문만 출력한다.
        """;

    /**
     * LLM 판독 소견서 생성.
     * 요청은 { studyId, userMemo } 만 받고, 이미 저장된 Report의 SR(aiResultJson)·SC(scKey)를 읽어 LLM에 전달.
     * AI 추론(/api/ai/result)이 선행되지 않았으면 오류.
     */
    @Transactional
    public ReportResponse generate(GenerateReportRequest req) {
        Long studyId = req.studyId();
        Study study = studyRepository.findById(studyId)
                .orElseThrow(() -> new NoSuchElementException("Study 없음: " + studyId));

        Report report = reportRepository.findByStudy_Id(studyId)
                .orElseThrow(() -> new IllegalStateException(
                        "AI 추론 결과가 없습니다. 먼저 /api/ai/result 로 추론을 실행하세요. studyId=" + studyId));

        if (report.getAiResultJson() == null || report.getAiResultJson().isBlank()) {
            throw new IllegalStateException(
                    "저장된 AI 분석(SR)이 없습니다. 먼저 /api/ai/result 로 추론을 실행하세요. studyId=" + studyId);
        }

        // 의사 소견 메모 반영
        if (req.userMemo() != null) {
            report.setDoctorOpinion(req.userMemo());
        }

        String prompt = buildPrompt(study, report);
        String text = llm.complete(SYSTEM_PROMPT, prompt);
        report.setAiReportText(text);

        reportRepository.save(report);
        return ReportResponse.from(report);
    }

    @Transactional(readOnly = true)
    public ReportResponse get(Long studyId) {
        Report report = reportRepository.findByStudy_Id(studyId)
                .orElseThrow(() -> new NoSuchElementException("리포트 없음: study " + studyId));
        return ReportResponse.from(report);
    }

    /** 의사 소견 저장/수정. */
    @Transactional
    public ReportResponse saveOpinion(Long studyId, OpinionRequest req) {
        Study study = studyRepository.findById(studyId)
                .orElseThrow(() -> new NoSuchElementException("Study 없음: " + studyId));
        Report report = reportRepository.findByStudy_Id(studyId)
                .orElseGet(() -> Report.builder().study(study).build());

        if (req.doctorName() != null)    report.setDoctorName(req.doctorName());
        if (req.doctorOpinion() != null) report.setDoctorOpinion(req.doctorOpinion());

        reportRepository.save(report);
        return ReportResponse.from(report);
    }

    /** 판독 확정. */
    @Transactional
    public ReportResponse confirm(Long studyId) {
        Report report = reportRepository.findByStudy_Id(studyId)
                .orElseThrow(() -> new NoSuchElementException("리포트 없음: study " + studyId));
        report.setConfirmed(true);
        report.setConfirmedAt(LocalDateTime.now());
        reportRepository.save(report);
        return ReportResponse.from(report);
    }

    // ── LLM 프롬프트 구성 (저장된 SR + 메모 + 검사정보) ──
    private String buildPrompt(Study study, Report report) {
        StringBuilder sb = new StringBuilder();
        sb.append("[검사 정보]\n");
        sb.append("- Study ID: ").append(study.getId()).append('\n');
        if (study.getStudyDescription() != null)
            sb.append("- 검사 설명: ").append(study.getStudyDescription()).append('\n');
        if (study.getStudyDate() != null)
            sb.append("- 검사 일시: ").append(study.getStudyDate()).append('\n');

        sb.append("\n[AI 분석 결과(SR)]\n");
        sb.append("- 종합: ").append(nvl(report.getAiOverall(), "정보 없음")).append('\n');
        sb.append("- 이상 여부(AI): ")
          .append(report.getAiAbnormal() == null ? "미상" : (report.getAiAbnormal() ? "이상 의심" : "정상")).append('\n');
        sb.append("- 결과 JSON:\n").append(nvl(report.getAiResultJson(), "{}")).append('\n');
        if (report.getScKey() != null)
            sb.append("- SC 이미지 key: ").append(report.getScKey()).append('\n');

        sb.append("\n[의사 소견 메모]\n");
        sb.append(nvl(report.getDoctorOpinion(), "(없음)")).append('\n');

        sb.append("\n위 정보를 종합해 한국어 판독 소견서 초안을 작성하세요.");
        return sb.toString();
    }

    private static String nvl(String v, String def) {
        return (v == null || v.isBlank()) ? def : v;
    }
}
