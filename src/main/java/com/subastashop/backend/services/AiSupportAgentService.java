package com.subastashop.backend.services;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.QuestionAnswerAdvisor;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.TextReader;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import jakarta.annotation.PostConstruct;
import java.util.List;

@Service
@ConditionalOnProperty(prefix = "app.ai", name = "enabled", havingValue = "true")
public class AiSupportAgentService {

    private final ChatClient chatClient;
    private final VectorStore vectorStore;

    @Value("classpath:terminos.txt")
    private Resource terminosResource;

    public AiSupportAgentService(ChatClient.Builder chatClientBuilder, VectorStore vectorStore) {
        // Configuramos el cliente de chat con un System Prompt predeterminado
        this.chatClient = chatClientBuilder
                .defaultSystem("Eres el agente experto de soporte de SubastaShop. Responde dudas " +
                        "de forma amable, profesional y precisa, basándote EXCLUSIVAMENTE en el contexto " +
                        "proporcionado sobre las reglas del sitio. Si te preguntan algo fuera del contexto, " +
                        "indica educadamente que no puedes ayudar con eso.")
                .build();
        this.vectorStore = vectorStore;
    }

    @PostConstruct
    public void initKnowledgeBase() {
        // Al arrancar, leemos el archivo y llenamos el SimpleVectorStore en memoria
        TextReader textReader = new TextReader(terminosResource);
        List<Document> documents = textReader.get();
        vectorStore.add(documents);
    }

    /**
     * Genera una respuesta en streaming (palabra por palabra) usando RAG.
     */
    public Flux<String> streamSupportChat(String userMessage) {
        return chatClient.prompt()
                .user(userMessage)
                // Usamos QuestionAnswerAdvisor que internamente hace la búsqueda semántica en vectorStore
                .advisors(new QuestionAnswerAdvisor(vectorStore, SearchRequest.defaults()))
                .stream()
                .content();
    }
}
