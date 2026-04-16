package com.subastashop.backend.controllers;

import com.subastashop.backend.services.AiSupportAgentService;
import com.subastashop.backend.services.DataAnalysisAgentService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.ai", name = "enabled", havingValue = "true")
public class AiController {

    private final AiSupportAgentService supportAgentService;
    private final DataAnalysisAgentService dataAnalysisAgentService;

    /**
     * Endpoint SSE para el chat de soporte (RAG).
     * Accesible para cualquier usuario autenticado.
     */
    @GetMapping(value = "/support/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> streamSupportChat(@RequestParam String prompt) {
        return supportAgentService.streamSupportChat(prompt);
    }

    /**
     * Endpoint SSE para el análisis de datos (SQL).
     * Solo accesible para administradores.
     */
    @GetMapping(value = "/admin/analysis/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'ROLE_SUPER_ADMIN')")
    public Flux<String> streamDataAnalysis(@RequestParam String prompt) {
        return dataAnalysisAgentService.analyzeAndStream(prompt);
    }
}
