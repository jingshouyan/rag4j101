package com.github.jingshouyan.rag4j101.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.document.DocumentReader;
import org.springframework.ai.reader.ExtractedTextFormatter;
import org.springframework.ai.reader.markdown.MarkdownDocumentReader;
import org.springframework.ai.reader.markdown.config.MarkdownDocumentReaderConfig;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * Core RAG service — handles document ingestion and question answering.
 *
 * <p>Supports three ingestion modes:
 * <ul>
 *   <li>Raw text → {@link TokenTextSplitter} → Qdrant</li>
 *   <li>Markdown files → {@link MarkdownDocumentReader} + split → Qdrant</li>
 *   <li>PDF / DOCX / HTML / etc. → {@link TikaDocumentReader} + split → Qdrant</li>
 * </ul>
 */
@Service
public class RagService {

    private static final Logger log = LoggerFactory.getLogger(RagService.class);

    private final ChatClient chatClient;
    private final VectorStore vectorStore;
    private final TokenTextSplitter splitter;

    public RagService(ChatClient.Builder chatClientBuilder, VectorStore vectorStore) {
        this.chatClient = chatClientBuilder.build();
        this.vectorStore = vectorStore;
        this.splitter = TokenTextSplitter.builder()
                .withChunkSize(500)
                .withMinChunkSizeChars(100)
                .withPunctuationMarks(List.of('.', '!', '?', '\n'))
                .build();
    }

    // ──────────────────────────────
    //  Text ingestion
    // ──────────────────────────────

    /**
     * Ingest raw text: split via {@link TokenTextSplitter}, embed, store.
     */
    public int ingestText(String content, Map<String, Object> metadata) {
        Document doc = new Document(content, metadata);
        List<Document> chunks = splitter.split(doc);
        vectorStore.add(chunks);
        log.info("Ingested {} text chunk(s) into Qdrant", chunks.size());
        return chunks.size();
    }

    // ──────────────────────────────
    //  File ingestion
    // ──────────────────────────────

    /**
     * Ingest a file by path.  Delegates to the right reader based on extension.
     */
    public int ingestFile(String filePath, Map<String, Object> metadata) {
        Resource resource = new FileSystemResource(filePath);
        return ingestResource(resource, metadata);
    }

    /**
     * Ingest a Spring {@link Resource} (multipart upload, classpath, etc.).
     */
    public int ingestResource(Resource resource, Map<String, Object> metadata) {
        DocumentReader reader = resolveReader(resource);
        List<Document> documents = reader.read();
        List<Document> chunks = splitter.split(documents);
        // Apply metadata to all chunks
        if (metadata != null && !metadata.isEmpty()) {
            chunks.forEach(doc -> doc.getMetadata().putAll(metadata));
        }
        vectorStore.add(chunks);
        log.info("Ingested {} chunk(s) from {} into Qdrant", chunks.size(), resource.getFilename());
        return chunks.size();
    }

    // ──────────────────────────────
    //  Query
    // ──────────────────────────────

    /**
     * Ask a question with multi-turn conversation support.
     *
     * @param question       the user's question
     * @param conversationId identifies the conversation session;
     *                       reuse the same ID across calls for multi-turn context
     * @return the generated answer
     */
    public String ask(String question, String conversationId) {
        return chatClient.prompt()
                .user(question)
                .advisors(spec -> spec.param("chat_memory_conversation_id", conversationId))
                .call()
                .content();
    }

    // ──────────────────────────────
    //  Helpers
    // ──────────────────────────────

    private DocumentReader resolveReader(Resource resource) {
        String filename = resource.getFilename();
        if (filename != null && filename.toLowerCase().endsWith(".md")) {
            return new MarkdownDocumentReader(resource, MarkdownDocumentReaderConfig.defaultConfig());
        }
        // Tika handles PDF, DOCX, HTML, TXT, PPTX, etc.
        return new TikaDocumentReader(resource, ExtractedTextFormatter.defaults());
    }
}
