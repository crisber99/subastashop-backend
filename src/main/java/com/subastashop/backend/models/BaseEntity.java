package com.subastashop.backend.models;

import com.subastashop.backend.config.TenantContext;
import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;
import lombok.Getter;
import lombok.Setter;
import java.io.Serializable;

@Getter
@Setter
@MappedSuperclass // Significa: "No soy una tabla, pero mis hijos sí"
public abstract class BaseEntity implements Serializable {

    @Column(name = "TenantId", updatable = false)
    private String tenantId;

    @PrePersist // Se ejecuta justo antes de hacer un INSERT en la BD
    public void onPrePersist() {
        // Si el tenantId no viene seteado, lo tomamos del contexto
        if (this.tenantId == null) {
            this.tenantId = TenantContext.getTenantId();
        }
    }
}