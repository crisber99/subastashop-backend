package com.subastashop.backend.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class TenantInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        
        // 1. Buscamos el header "X-Tenant-ID"
        String tenantId = request.getHeader("X-Tenant-ID");

        // 2. Validamos que venga (para este MVP es obligatorio)
        if (tenantId == null || tenantId.isEmpty()) {
            response.setStatus(400); // Bad Request
            response.getWriter().write("Error: Falta el header X-Tenant-ID");
            return false; // Cortamos la petición aquí
        }

        // 3. Lo guardamos en el contexto para usarlo después
        TenantContext.setTenantId(tenantId);
        
        return true; // Dejamos pasar la petición
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        // 4. Limpieza obligatoria al terminar (para no mezclar datos en el siguiente request)
        TenantContext.clear();
    }
}