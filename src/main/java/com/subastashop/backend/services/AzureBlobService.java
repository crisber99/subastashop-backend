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
        // 1. Conectarse al servicio
        BlobServiceClient blobServiceClient = new BlobServiceClientBuilder()
                .connectionString(connectionString)
                .buildClient();

        // 2. Obtener el contenedor
        BlobContainerClient containerClient = blobServiceClient.getBlobContainerClient(containerName);

        // 3. Generar nombre único (para que no se sobrescriban)
        String nombreOriginal = archivo.getOriginalFilename();
        String nombreUnico = UUID.randomUUID().toString() + "-" + nombreOriginal;

        // 4. Subir
        BlobClient blobClient = containerClient.getBlobClient(nombreUnico);
        blobClient.upload(archivo.getInputStream(), archivo.getSize(), true);

        // 5. Devolver URL
        return blobClient.getBlobUrl();
    }
}