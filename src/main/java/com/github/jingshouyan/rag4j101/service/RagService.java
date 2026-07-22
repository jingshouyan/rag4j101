package com.github.jingshouyan.rag4j101.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Core RAG service — handles document ingestion and question answering.
 */
@Service
public class RagService {

    private static final Logger log = LoggerFactory.getLogger(RagService.class);
    private static final int CHUNK_SIZE = 500;

    private final ChatClient chatClient;
    private final VectorStore vectorStore;

    public RagService(ChatClient.Builder chatClientBuilder, VectorStore vectorStore) {
        // Build the ChatClient with the RAG advisor already registered via ChatClientBuilderCustomizer
        this.chatClient = chatClientBuilder.build();
        this.vectorStore = vectorStore;
    }

    /**
     * Ingest text content: chunk, embed, and store in Qdrant.
     */
    public int ingest(String content, Map<String, Object> metadata) {
        List<Document> chunks = chunkText(content, metadata);
        vectorStore.add(chunks);
        log.info("Ingested {} document chunks into Qdrant", chunks.size());
        return chunks.size();
    }

    /**
     * Ask a question using RAG — the QuestionAnswerAdvisor automatically
     * retrieves relevant chunks from Qdrant and augments the prompt.
     */
    public String ask(String question) {
        return chatClient.prompt()
                .user(question)
                .call()
                .content();
    }

    /**
     * Simple text chunker — splits text into ~500-char overlapping chunks.
     */
    private List<Document> chunkText(String text, Map<String, Object> metadata) {
        List<Document> documents = new ArrayList<>();
        int start = 0;
        int chunkIndex = 0;

        while (start < text.length()) {
            int end = Math.min(start + CHUNK_SIZE, text.length());

            // Try to break at a sentence boundary or newline near the chunk end
            if (end < text.length()) {
                int breakPoint = findBreakPoint(text, end);
                if (breakPoint > start) {
                    end = breakPoint;
                }
            }

            String chunk = text.substring(start, end).trim();
            if (!chunk.isEmpty()) {
                Document doc = new Document(chunk, Map.of(
                        "chunk_index", chunkIndex,
                        "source_type", "ingested_text"
                ));
                if (metadata != null) {
                    doc.getMetadata().putAll(metadata);
                }
                documents.add(doc);
                chunkIndex++;
            }

            // Move start with overlap
            start = end - (CHUNK_SIZE / 5); // ~100 char overlap
            if (start >= end) {
                start = end;
            }
            if (start >= text.length()) {
                break;
            }
        }

        return documents;
    }

    private int findBreakPoint(String text, int near) {
        // Look backwards from `near` for a newline or sentence end
        int searchStart = Math.max(0, near - 100);
        for (int i = near; i >= searchStart; i--) {
            char c = text.charAt(i);
            if (c == '\n' || c == '\r') {
                return i + 1;
            }
        }
        for (int i = near; i >= searchStart; i--) {
            char c = text.charAt(i);
            if (c == '.' || c == '!' || c == '？' || c == '。') {
                return i + 1;
            }
        }
        return near;
    }
}
