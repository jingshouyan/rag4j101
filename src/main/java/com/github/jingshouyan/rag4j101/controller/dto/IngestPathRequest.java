package com.github.jingshouyan.rag4j101.controller.dto;

import java.util.Map;

public record IngestPathRequest(String path, Map<String, Object> metadata) {
}
