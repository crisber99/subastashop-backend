package com.subastashop.backend.dto; // O el paquete que uses
import java.util.List;

public class OrdenRequest {
    private List<DetalleRequest> detalles;
    private String preferenciaEnvio;
    private String codigoCupon;

    // Getters y Setters
    public List<DetalleRequest> getDetalles() { return detalles; }
    public void setDetalles(List<DetalleRequest> detalles) { this.detalles = detalles; }
    public String getPreferenciaEnvio() { return preferenciaEnvio; }
    public void setPreferenciaEnvio(String preferenciaEnvio) { this.preferenciaEnvio = preferenciaEnvio; }
    public String getCodigoCupon() { return codigoCupon; }
    public void setCodigoCupon(String codigoCupon) { this.codigoCupon = codigoCupon; }
}