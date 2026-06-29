package com.dicom.medical.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Orthanc(PACS)와 DICOMweb으로 통신하는 게이트웨이.
 * 지금은 STOW-RS(결과 회신 저장)만. 추후 QIDO/WADO도 여기에 추가.
 */
@Service
public class OrthancService {

    private final RestClient client;

    public OrthancService(
            @Value("${orthanc.base-url:http://localhost:8042}") String baseUrl,
            @Value("${orthanc.username:orthanc}") String username,
            @Value("${orthanc.password:orthanc}") String password) {
        this.client = RestClient.builder()
                .baseUrl(baseUrl)
                .defaultHeaders(h -> h.setBasicAuth(username, password))
                .build();
    }

    /**
     * STOW-RS — DICOM(예: AI 결과 SC) 한 건을 Orthanc에 회신 저장.
     * @param dicomBytes 저장할 .dcm 바이트
     * @return Orthanc의 STOW 응답(DICOM JSON 문자열)
     */
    public String stow(byte[] dicomBytes) {
        String boundary = "DICOMwebBoundary" + UUID.randomUUID();
        byte[] body = buildRelatedBody(dicomBytes, boundary);

        return client.post()
                .uri("/dicom-web/studies")
                .header(HttpHeaders.CONTENT_TYPE,
                        "multipart/related; type=\"application/dicom\"; boundary=" + boundary)
                .accept(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(String.class);
    }

    /** multipart/related; type="application/dicom" 본문을 바이트로 직접 구성 */
    private byte[] buildRelatedBody(byte[] dicom, String boundary) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            out.write(("--" + boundary + "\r\n"
                    + "Content-Type: application/dicom\r\n\r\n")
                    .getBytes(StandardCharsets.UTF_8));
            out.write(dicom);
            out.write(("\r\n--" + boundary + "--\r\n")
                    .getBytes(StandardCharsets.UTF_8));
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("STOW 본문 생성 실패", e);
        }
    }
}