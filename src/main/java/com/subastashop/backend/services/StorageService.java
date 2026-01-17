package com.subastashop.backend.services;

import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.BlobServiceClientBuilder;
import com.subastashop.backend.config.TenantContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.UUID;

@Service
public class StorageService {

    @Value("${azure.storage.connection-string}")
    private String connectionString;

    @Value("${azure.storage.container-name}")
    private String containerName;

    public String subirImagen(MultipartFile file) throws IOException {
        // 1. Obtener el tenant actual para organizar las carpetas
        String tenantId = TenantContext.getTenantId();
        
        // 2. Generar un nombre único para el archivo (ej: tienda-demo/foto-uuid.jpg)
        String originalFilename = file.getOriginalFilename();
        String extension = originalFilename != null && originalFilename.contains(".") 
                ? originalFilename.substring(originalFilename.lastIndexOf(".")) 
                : ".jpg";
        
        String fileName = tenantId + "/" + UUID.randomUUID().toString() + extension;

        // 3. Conectarse al cliente de Azure
        BlobServiceClient blobServiceClient = new BlobServiceClientBuilder()
                .connectionString(connectionString)
                .buildClient();

        BlobContainerClient containerClient = blobServiceClient.getBlobContainerClient(containerName);
        BlobClient blobClient = containerClient.getBlobClient(fileName);

        // 4. Subir el archivo
        blobClient.upload(file.getInputStream(), file.getSize(), true);

        // 5. Retornar la URL pública para guardarla en la BD
        return blobClient.getBlobUrl();
    }
}