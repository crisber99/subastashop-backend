package com.subastashop.backend.services;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@ConditionalOnProperty(prefix = "app.ai", name = "enabled", havingValue = "true")
public class DataAnalysisAgentService {

    private final ChatClient chatClient;
    private final JdbcTemplate jdbcTemplate;

    // Patrón para detectar comandos destructivos o de modificación
    private static final Pattern DESTRUCTIVE_SQL_PATTERN = Pattern.compile(
            "\\b(INSERT|UPDATE|DELETE|DROP|ALTER|TRUNCATE|GRANT|REVOKE|CREATE)\\b",
            Pattern.CASE_INSENSITIVE
    );

    @Autowired
    public DataAnalysisAgentService(ChatClient.Builder chatClientBuilder, JdbcTemplate jdbcTemplate) {
        this.chatClient = chatClientBuilder
                .defaultSystem("Eres un experto Analista de Datos para SubastaShop. " +
                        "Tu tarea es ayudar a los administradores a entender el estado del negocio. " +
                        "Tienes acceso a las siguientes tablas principales: " +
                        "1. AppUsers: Usuarios, correos, roles. " +
                        "2. Producto: Nombre, precio, stock, descripción. " +
                        "3. Tienda: Nombre, dueño, reputación. " +
                        "4. Orden: Pedidos realizados, fecha, total, estado. " +
                        "5. Puja: Historial de apuestas en subastas. " +
                        "IMPORTANTE: Solo genera consultas SQL de tipo SELECT. " +
                        "Responde siempre en formato: [CONSULTA: tu_sql_aquí] seguido de una breve explicación.")
                .build();
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Procesa una consulta de administrador, ejecuta el SQL generado de forma segura
     * y devuelve un flujo con la explicación y los datos resumidos.
     */
    public Flux<String> analyzeAndStream(String adminQuery) {
        // En una implementación real, podríamos usar Function Calling para que la IA
        // llame a una función 'executeQuery'. Por ahora, usaremos un flujo de razonamiento.
        
        return chatClient.prompt()
                .user(adminQuery)
                .stream()
                .content()
                .map(content -> {
                    // Esta es una implementación simplificada. 
                    // En el futuro podemos extraer el SQL del contenido si la IA lo genera.
                    return content;
                });
    }

    /**
     * Ejecuta una consulta SQL de forma segura (Solo lectura).
     */
    public List<Map<String, Object>> executeSafeQuery(String sql) {
        if (DESTRUCTIVE_SQL_PATTERN.matcher(sql).find()) {
            throw new SecurityException("Operación SQL no permitida: Solo se permiten consultas SELECT.");
        }
        return jdbcTemplate.queryForList(sql);
    }
}
