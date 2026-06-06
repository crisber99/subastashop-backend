package com.subastashop.backend.services;

import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.BlobServiceClientBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.UUID;
import java.io.ByteArrayOutputStream;
import com.sksamuel.scrimage.ImmutableImage;
import com.sksamuel.scrimage.webp.WebpWriter;

import jakarta.annotation.PostConstruct;

@Service
public class AzureBlobService {

    @Value("${azure.storage.connection-string}")
    private String connectionString;

    private final String containerName = "productos-img";
    private BlobContainerClient containerClient;

    @PostConstruct
    public void init() {
        if (connectionString != null && !connectionString.contains("placeholder")) {
            BlobServiceClient blobServiceClient = new BlobServiceClientBuilder()
                    .connectionString(connectionString)
                    .buildClient();
            containerClient = blobServiceClient.getBlobContainerClient(containerName);
            if (!containerClient.exists()) {
                containerClient.create();
            }
        }
    }

    public String subirImagen(MultipartFile archivo) throws IOException {
        System.out.println("DEBUG: Azure Connection String detected: " + (connectionString != null && connectionString.contains("AccountName") ? "REAL KEY LOADED" : "PLACEHOLDER/NULL"));
        
        if (connectionString == null || connectionString.contains("placeholder")) {
            return "https://placehold.co/600x400?text=Imagen+No+Disponible";
        }

        String nombreOriginal = archivo.getOriginalFilename();
        if (nombreOriginal == null) nombreOriginal = "imagen";
        String baseName = nombreOriginal;
        if (nombreOriginal.lastIndexOf('.') != -1) {
            baseName = nombreOriginal.substring(0, nombreOriginal.lastIndexOf('.'));
        }
        
        String nombreUnico = UUID.randomUUID().toString() + "-" + baseName + ".webp";

        // Comprimir y convertir a WebP
        byte[] webpBytes = ImmutableImage.loader().fromStream(archivo.getInputStream())
                .max(1280, 1280) // Redimensionar si es muy grande
                .bytes(WebpWriter.DEFAULT.withQ(80));

        BlobClient blobClient = containerClient.getBlobClient(nombreUnico);
        try (java.io.ByteArrayInputStream bis = new java.io.ByteArrayInputStream(webpBytes)) {
            blobClient.upload(bis, webpBytes.length, true);
        }

        return blobClient.getBlobUrl();
    }

    public String subirImagenDirecta(MultipartFile archivo) throws IOException {
        System.out.println("DEBUG: Azure Direct Upload");
        if (connectionString == null || connectionString.contains("placeholder")) {
            return "https://placehold.co/600x400?text=Imagen+No+Disponible";
        }

        String nombreOriginal = archivo.getOriginalFilename();
        if (nombreOriginal == null) nombreOriginal = "imagen";
        String baseName = nombreOriginal.replaceAll("[^a-zA-Z0-9.-]", "_");
        
        String extension = "";
        int i = nombreOriginal.lastIndexOf('.');
        if (i > 0) {
            extension = nombreOriginal.substring(i);
        }

        String nombreUnico = "avatar-" + UUID.randomUUID().toString() + extension;

        BlobClient blobClient = containerClient.getBlobClient(nombreUnico);
        blobClient.upload(archivo.getInputStream(), archivo.getSize(), true);

        return blobClient.getBlobUrl();
    }

    public String subirImagenBase64(String base64, String nombreBase) throws IOException {
        if (connectionString == null || connectionString.contains("placeholder")) {
            return "https://placehold.co/100x100?text=Premio+Demo";
        }

        // Limpiar el prefijo data:image/...;base64,
        String base64Real = base64.contains(",") ? base64.split(",")[1] : base64;
        byte[] bytes = java.util.Base64.getDecoder().decode(base64Real);

        // Comprimir y convertir a WebP
        byte[] webpBytes = ImmutableImage.loader().fromBytes(bytes)
                .max(800, 800)
                .bytes(WebpWriter.DEFAULT.withQ(80));

        String nombreUnico = UUID.randomUUID().toString() + "-" + nombreBase + ".webp";
        BlobClient blobClient = containerClient.getBlobClient(nombreUnico);
        
        try (java.io.ByteArrayInputStream bis = new java.io.ByteArrayInputStream(webpBytes)) {
            blobClient.upload(bis, webpBytes.length, true);
        }

        return blobClient.getBlobUrl();
    }

    /**
     * Elimina todos los archivos del contenedor (para limpieza).
     */
    public void eliminarTodo() {
        if (connectionString == null || connectionString.contains("placeholder")) return;

        if (containerClient.exists()) {
            containerClient.listBlobs().forEach(blobItem -> {
                containerClient.getBlobClient(blobItem.getName()).delete();
            });
        }
    }
}