package com.subastashop.backend.dto;

public class DetalleRequest {
    private Long productoId;
    private Integer cantidad;
    private String tipoCompra; // DIRECTA, RIFA
    private String datosExtra; // "45" (Ticket)

    // Getters y Setters
    public Long getProductoId() { return productoId; }
    public void setProductoId(Long productoId) { this.productoId = productoId; }
    public Integer getCantidad() { return cantidad; }
    public void setCantidad(Integer cantidad) { this.cantidad = cantidad; }
    public String getTipoCompra() { return tipoCompra; }
    public void setTipoCompra(String tipoCompra) { this.tipoCompra = tipoCompra; }
    public String getDatosExtra() { return datosExtra; }
    public void setDatosExtra(String datosExtra) { this.datosExtra = datosExtra; }
}