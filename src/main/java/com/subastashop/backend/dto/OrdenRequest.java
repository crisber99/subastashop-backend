package com.subastashop.backend.dto; // O el paquete que uses
import java.util.List;

public class OrdenRequest {
    private List<DetalleRequest> detalles;

    // Getters y Setters
    public List<DetalleRequest> getDetalles() { return detalles; }
    public void setDetalles(List<DetalleRequest> detalles) { this.detalles = detalles; }
}