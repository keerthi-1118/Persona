package com.persona.service;

import nl.martijndwars.webpush.Notification;
import nl.martijndwars.webpush.PushService;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.security.Security;

@Service
public class WebPushService {

    @Value("${persona.vapid-public-key:}")
    private String vapidPublicKey;

    @Value("${persona.vapid-private-key:}")
    private String vapidPrivateKey;

    @Value("${persona.vapid-claims-email:}")
    private String vapidSubject;

    private PushService pushService;

    @PostConstruct
    public void init() {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
        if (!vapidPublicKey.isEmpty() && !vapidPrivateKey.isEmpty()) {
            try {
                pushService = new PushService(vapidPublicKey, vapidPrivateKey, vapidSubject);
            } catch (Exception e) {
                System.err.println("Failed to initialize WebPushService: " + e.getMessage());
            }
        }
    }

    public void sendNotification(String endpoint, String p256dh, String auth, String title, String body, String url) throws Exception {
        if (pushService == null) {
            throw new RuntimeException("VAPID keys not configured in application.properties");
        }
        
        String payload = String.format("{\"title\":\"%s\",\"body\":\"%s\",\"url\":\"%s\"}", 
            title.replace("\"", "\\\""), 
            body.replace("\"", "\\\""), 
            url.replace("\"", "\\\""));

        Notification notification = new Notification(endpoint, p256dh, auth, payload);
        pushService.send(notification);
    }
}
