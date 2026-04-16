package com.subastashop.backend.config;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(prefix = "app.ai", name = "enabled", havingValue = "true")
public class AiConfig {

    @Bean
    public VectorStore vectorStore(EmbeddingModel embeddingModel) {
        // Configuramos un VectorStore simple en memoria para pruebas de RAG
        return new SimpleVectorStore(embeddingModel);
    }
}
