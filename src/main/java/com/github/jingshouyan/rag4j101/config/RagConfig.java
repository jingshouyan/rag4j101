package com.github.jingshouyan.rag4j101.config;

import org.springframework.ai.chat.client.ChatClientBuilderCustomizer;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RAG pipeline configuration.
 *
 * Registers a QuestionAnswerAdvisor that enriches every chat request
 * with relevant documents retrieved from Qdrant.
 */
@Configuration
public class RagConfig {

    @Bean
    public ChatClientBuilderCustomizer ragAdvisorCustomizer(VectorStore vectorStore) {
        SearchRequest searchRequest = SearchRequest.builder()
                .topK(5)
                .similarityThreshold(0.5)
                .build();

        QuestionAnswerAdvisor advisor = QuestionAnswerAdvisor.builder(vectorStore)
                .searchRequest(searchRequest)
                .build();

        return builder -> builder.defaultAdvisors(advisor);
    }
}
