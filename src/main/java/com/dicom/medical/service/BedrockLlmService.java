package com.dicom.medical.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.*;

/**
 * Bedrock Converse API 래퍼. 모델 무관하게 system + user 메시지로 텍스트 응답을 받는다.
 * 모델 ID는 application.yaml 의 aws.bedrock.model-id 로 교체 가능.
 */
@Service
@RequiredArgsConstructor
public class BedrockLlmService {

    private final BedrockRuntimeClient client;

    @Value("${aws.bedrock.model-id:apac.anthropic.claude-3-5-sonnet-20241022-v2:0}")
    private String modelId;

    @Value("${aws.bedrock.max-tokens:1200}")
    private int maxTokens;

    @Value("${aws.bedrock.temperature:0.2}")
    private float temperature;

    public String complete(String system, String userPrompt) {
        ConverseResponse resp = client.converse(r -> r
                .modelId(modelId)
                .system(SystemContentBlock.fromText(system))
                .messages(Message.builder()
                        .role(ConversationRole.USER)
                        .content(ContentBlock.fromText(userPrompt))
                        .build())
                .inferenceConfig(c -> c
                        .maxTokens(maxTokens)
                        .temperature(temperature)));

        return resp.output().message().content().get(0).text();
    }
}
