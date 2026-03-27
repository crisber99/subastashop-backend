package com.subastashop.backend.services;

import nl.martijndwars.webpush.Notification;
import nl.martijndwars.webpush.PushService;
import nl.martijndwars.webpush.Utils;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.subastashop.backend.models.SuscripcionPush;
import com.subastashop.backend.repositories.SuscripcionPushRepository;

import jakarta.annotation.PostConstruct;
import java.security.Security;
import java.util.List;

@Service
public class WebPushService {

    // VAPID Keys dinámicas (las generaremos en Angular) o estáticas
    // Por simplicidad en producción, es mejor tener un par estático inyectado o definido
    @Value("${vapid.public.key:BEl62iUYgUivxIkv69yViEuiBIa-Ib9-SkvMeAtA3LFgDzkrxZJjSgSnfckjBJuB-3qND7BroqhVWeJ2Q-nU8bI}")
    private String publicKey;
    
    @Value("${vapid.private.key:A18P2P0z7_QyKXX-cXX9XKXbZ3y0vQxx8eZXX7_YXX0}")
    private String privateKey;

    private PushService pushService;
    private final SuscripcionPushRepository suscripcionRepository;

    public WebPushService(SuscripcionPushRepository suscripcionRepository) {
        this.suscripcionRepository = suscripcionRepository;
    }

    @PostConstruct
    public void init() throws Exception {
        Security.addProvider(new BouncyCastleProvider());
        // Inicializamos el PushService de la librería
        // Nota: En un entorno real se usan llaves válidas generadas con web-push
        try {
            pushService = new PushService()
                .setPublicKey(publicKey)
                .setPrivateKey(privateKey)
                .setSubject("mailto:admin@subastashop.com");
        } catch(Exception e) {
            System.err.println("Advertencia: No se pudo inicializar Web Push (llaves inválidas por defecto).");
        }
    }

    public void enviarNotificacion(SuscripcionPush suscripcion, String title, String body, String url) {
        if (pushService == null) return;
        try {
            String payload = String.format("{\"notification\":{\"title\":\"%s\",\"body\":\"%s\",\"vibrate\":[100,50,100],\"data\":{\"url\":\"%s\"}}}", 
                title, body, url);
            
            Notification notification = new Notification(
                suscripcion.getEndpoint(),
                suscripcion.getP256dh(),
                suscripcion.getAuth(),
                payload.getBytes("UTF-8")
            );
            
            pushService.send(notification);
        } catch (Exception e) {
            System.err.println("Error al enviar notificación push: " + e.getMessage());
            // Si el endpoint expiró (410 Gone), eliminarlo de BD
            if (e.getMessage() != null && e.getMessage().contains("410")) {
                suscripcionRepository.delete(suscripcion);
            }
        }
    }

    public void enviarGlobal(String title, String body, String url) {
        List<SuscripcionPush> todas = suscripcionRepository.findAll();
        for (SuscripcionPush sub : todas) {
            enviarNotificacion(sub, title, body, url);
        }
    }
}
