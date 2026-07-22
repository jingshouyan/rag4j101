package com.github.jingshouyan.rag4j101.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

/**
 * Custom EmbeddingModel that calls DeepSeek's embedding API directly.
 * DeepSeek's Spring AI module doesn't include an EmbeddingModel implementation,
 * so we implement one using RestClient.
 */
@Component
@ConditionalOnMissingBean(EmbeddingModel.class)
public class DeepSeekEmbeddingModel implements EmbeddingModel {

    private static final String DEFAULT_BASE_URL = "https://api.deepseek.com";
    private static final int EMBEDDING_DIMENSIONS = 1024;

    private final RestClient restClient;
    private final String model;

    public DeepSeekEmbeddingModel(
            @Value("${spring.ai.deepseek.api-key}") String apiKey,
            @Value("${spring.ai.deepseek.base-url:https://api.deepseek.com}") String baseUrl,
            @Value("${spring.ai.deepseek.embedding.options.model:deepseek-embedding}") String model) {
        this.model = model;
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl + "/v1/embeddings")
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .defaultHeader("Content-Type", "application/json")
                .build();
    }

    @Override
    public EmbeddingResponse call(EmbeddingRequest request) {
        List<String> inputs = request.getInstructions();
        DeepSeekEmbeddingResponse response = restClient.post()
                .body(Map.of(
                        "model", model,
                        "input", inputs.size() == 1 ? inputs.getFirst() : inputs
                ))
                .retrieve()
                .body(DeepSeekEmbeddingResponse.class);

        if (response == null || response.data() == null) {
            throw new RuntimeException("DeepSeek embedding API returned null response");
        }

        List<Embedding> embeddings = response.data().stream()
                .map(d -> new Embedding(toFloatArray(d.embedding()), d.index()))
                .toList();

        return new EmbeddingResponse(embeddings);
    }

    @Override
    public float[] embed(Document document) {
        return embed(document.getFormattedContent());
    }

    @Override
    public int dimensions() {
        return EMBEDDING_DIMENSIONS;
    }

    private float[] toFloatArray(List<Double> doubles) {
        float[] floats = new float[doubles.size()];
        for (int i = 0; i < doubles.size(); i++) {
            floats[i] = doubles.get(i).floatValue();
        }
        return floats;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record DeepSeekEmbeddingResponse(
            @JsonProperty("object") String object,
            @JsonProperty("data") List<EmbeddingData> data,
            @JsonProperty("model") String model,
            @JsonProperty("usage") Usage usage) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record EmbeddingData(
            @JsonProperty("object") String object,
            @JsonProperty("index") int index,
            @JsonProperty("embedding") List<Double> embedding) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Usage(
            @JsonProperty("prompt_tokens") int promptTokens,
            @JsonProperty("total_tokens") int totalTokens) {
    }
}
