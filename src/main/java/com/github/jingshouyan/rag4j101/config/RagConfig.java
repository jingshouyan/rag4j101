package com.github.jingshouyan.rag4j101.config;

import org.springframework.ai.chat.client.ChatClientBuilderCustomizer;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RAG pipeline configuration.
 *
 * Registers two default advisors on every ChatClient:
 * <ol>
 *   <li>{@link QuestionAnswerAdvisor} — retrieves relevant docs from Qdrant</li>
 *   <li>{@link MessageChatMemoryAdvisor} — maintains multi-turn conversation history</li>
 * </ol>
 */
@Configuration
public class RagConfig {

    @Bean
    public MessageWindowChatMemory chatMemory() {
        return MessageWindowChatMemory.builder()
                .maxMessages(20)
                .build();
    }

    @Bean
    public ChatClientBuilderCustomizer ragAdvisorCustomizer(
            VectorStore vectorStore, MessageWindowChatMemory chatMemory) {

        SearchRequest searchRequest = SearchRequest.builder()
                .topK(5)
                .similarityThreshold(0.5)
                .build();

        QuestionAnswerAdvisor qaAdvisor = QuestionAnswerAdvisor.builder(vectorStore)
                .searchRequest(searchRequest)
                .build();

        MessageChatMemoryAdvisor memoryAdvisor = MessageChatMemoryAdvisor.builder(chatMemory)
                .build();

        return builder -> builder.defaultAdvisors(qaAdvisor, memoryAdvisor);
    }
}
