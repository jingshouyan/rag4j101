package com.github.jingshouyan.rag4j101.controller;

import com.github.jingshouyan.rag4j101.controller.dto.*;
import com.github.jingshouyan.rag4j101.service.RagService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * REST API for the RAG demo.
 *
 * POST /api/rag/ingest  — ingest text content into the vector store
 * POST /api/rag/query   — ask a question with RAG context
 * GET  /api/rag/health  — health check
 */
@RestController
@RequestMapping("/api/rag")
public class RagController {

    private final RagService ragService;

    public RagController(RagService ragService) {
        this.ragService = ragService;
    }

    @PostMapping("/ingest")
    public ResponseEntity<IngestResponse> ingest(@RequestBody IngestRequest request) {
        Map<String, Object> metadata = request.metadata() != null ? request.metadata() : Map.of();
        int chunks = ragService.ingest(request.content(), metadata);
        return ResponseEntity.ok(new IngestResponse(chunks,
                "Successfully ingested " + chunks + " chunk(s) into Qdrant"));
    }

    @PostMapping("/query")
    public ResponseEntity<QueryResponse> query(@RequestBody QueryRequest request) {
        if (request.question() == null || request.question().isBlank()) {
            return ResponseEntity.badRequest().body(new QueryResponse("Question must not be empty"));
        }
        String answer = ragService.ask(request.question());
        return ResponseEntity.ok(new QueryResponse(answer));
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of(
                "status", "UP",
                "service", "rag4j101 RAG Demo"
        ));
    }
}
