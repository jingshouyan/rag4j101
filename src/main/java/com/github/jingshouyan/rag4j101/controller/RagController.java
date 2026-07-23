package com.github.jingshouyan.rag4j101.controller;

import com.github.jingshouyan.rag4j101.controller.dto.*;
import com.github.jingshouyan.rag4j101.service.RagService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;

/**
 * REST API for the RAG demo.
 *
 * <pre>
 * POST /api/rag/ingest        — ingest raw text (uses TokenTextSplitter)
 * POST /api/rag/ingest/file   — upload a file (PDF / DOCX / MD / TXT / …)
 * POST /api/rag/ingest/path   — ingest a server-side file by absolute path
 * POST /api/rag/query         — ask a question with RAG context
 * GET  /api/rag/health        — health check
 * </pre>
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
        int chunks = ragService.ingestText(request.content(), metadata);
        return ResponseEntity.ok(new IngestResponse(chunks,
                "Successfully ingested " + chunks + " chunk(s) into Qdrant"));
    }

    @PostMapping("/ingest/file")
    public ResponseEntity<IngestResponse> ingestFile(@RequestParam("file") MultipartFile file) throws IOException {
        String originalName = file.getOriginalFilename();
        Map<String, Object> metadata = Map.of("source", originalName != null ? originalName : "upload");
        int chunks = ragService.ingestResource(file.getResource(), metadata);
        return ResponseEntity.ok(new IngestResponse(chunks,
                "Successfully ingested " + chunks + " chunk(s) from " + originalName));
    }

    @PostMapping("/ingest/path")
    public ResponseEntity<IngestResponse> ingestByPath(@RequestBody IngestPathRequest request) {
        Map<String, Object> metadata = request.metadata() != null ? request.metadata() : Map.of();
        if (request.path() != null) {
            metadata.put("source", request.path());
        }
        int chunks = ragService.ingestFile(request.path(), metadata);
        return ResponseEntity.ok(new IngestResponse(chunks,
                "Successfully ingested " + chunks + " chunk(s) from " + request.path()));
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
