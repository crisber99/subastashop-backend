package com.subastashop.backend.dtos;

import java.time.LocalDateTime;

public class TiendaPublicDTO {
    private Long id;
    private String nombre;
    private String slug;
    private String logoUrl;
    private String colorPrimario;
    private Boolean activa;
    private boolean identidadVerificada;
    private LocalDateTime fechaCreacion;
    private String whatsapp;

    public TiendaPublicDTO(Long id, String nombre, String slug, String logoUrl, String colorPrimario, Boolean activa, boolean identidadVerificada, LocalDateTime fechaCreacion, String whatsapp) {
        this.id = id;
        this.nombre = nombre;
        this.slug = slug;
        this.logoUrl = logoUrl;
        this.colorPrimario = colorPrimario;
        this.activa = activa;
        this.identidadVerificada = identidadVerificada;
        this.fechaCreacion = fechaCreacion;
        this.whatsapp = whatsapp;
    }

    public Long getId() { return id; }
    public String getNombre() { return nombre; }
    public String getSlug() { return slug; }
    public String getLogoUrl() { return logoUrl; }
    public String getColorPrimario() { return colorPrimario; }
    public Boolean getActiva() { return activa; }
    public boolean isIdentidadVerificada() { return identidadVerificada; }
    public LocalDateTime getFechaCreacion() { return fechaCreacion; }
    public String getWhatsapp() { return whatsapp; }
}
