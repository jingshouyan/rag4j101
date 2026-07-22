package com.github.jingshouyan.rag4j101.controller.dto;

import java.util.Map;

public record IngestRequest(String content, Map<String, Object> metadata) {
}
