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

@Service
public class AzureBlobService {

    // Lee la conexión desde application.properties
    @Value("${azure.storage.connection-string}")
    private String connectionString;

    // Nombre del contenedor que creaste en el Portal de Azure
    private final String containerName = "productos-img";

    public String subirImagen(MultipartFile archivo) throws IOException {
        System.out.println("DEBUG: Azure Connection String detected: " + (connectionString != null && connectionString.contains("AccountName") ? "REAL KEY LOADED" : "PLACEHOLDER/NULL"));
        
        // Fallback si no hay conexión real a Azure (modo demo)
        if (connectionString == null || connectionString.contains("placeholder")) {
            return "https://placehold.co/600x400?text=Imagen+No+Disponible";
        }

        // 1. Conectarse al servicio
        BlobServiceClient blobServiceClient = new BlobServiceClientBuilder()
                .connectionString(connectionString)
                .buildClient();

        // 2. Obtener el contenedor (y crearlo si no existe)
        BlobContainerClient containerClient = blobServiceClient.getBlobContainerClient(containerName);
        if (!containerClient.exists()) {
            containerClient.create();
        }

        // 3. Generar nombre único (para que no se sobrescriban)
        String nombreOriginal = archivo.getOriginalFilename();
        String nombreUnico = UUID.randomUUID().toString() + "-" + nombreOriginal;

        // 4. Subir
        BlobClient blobClient = containerClient.getBlobClient(nombreUnico);
        blobClient.upload(archivo.getInputStream(), archivo.getSize(), true);

        // 5. Devolver URL
        return blobClient.getBlobUrl();
    }

    public String subirImagenBase64(String base64, String nombreBase) throws IOException {
        if (connectionString == null || connectionString.contains("placeholder")) {
            return "https://placehold.co/100x100?text=Premio+Demo";
        }

        // Limpiar el prefijo data:image/...;base64,
        String base64Real = base64.contains(",") ? base64.split(",")[1] : base64;
        byte[] bytes = java.util.Base64.getDecoder().decode(base64Real);

        BlobServiceClient blobServiceClient = new BlobServiceClientBuilder()
                .connectionString(connectionString)
                .buildClient();

        BlobContainerClient containerClient = blobServiceClient.getBlobContainerClient(containerName);
        if (!containerClient.exists()) containerClient.create();

        String nombreUnico = UUID.randomUUID().toString() + "-" + nombreBase + ".jpg";
        BlobClient blobClient = containerClient.getBlobClient(nombreUnico);
        
        try (java.io.ByteArrayInputStream bis = new java.io.ByteArrayInputStream(bytes)) {
            blobClient.upload(bis, bytes.length, true);
        }

        return blobClient.getBlobUrl();
    }

    /**
     * Elimina todos los archivos del contenedor (para limpieza).
     */
    public void eliminarTodo() {
        if (connectionString == null || connectionString.contains("placeholder")) return;

        BlobContainerClient containerClient = new BlobServiceClientBuilder()
                .connectionString(connectionString)
                .buildClient()
                .getBlobContainerClient(containerName);

        if (containerClient.exists()) {
            containerClient.listBlobs().forEach(blobItem -> {
                containerClient.getBlobClient(blobItem.getName()).delete();
            });
        }
    }
}