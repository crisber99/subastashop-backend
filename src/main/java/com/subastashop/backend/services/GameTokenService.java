package com.subastashop.backend.services;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Date;

@Service
public class GameTokenService {

    @Value("${jwt.secret:defaultSecretKeyThatShouldBeChangedInProduction}")
    private String secretKey;

    private static final String HMAC_ALGO = "HmacSHA256";

    /**
     * Genera un token estructurado "userId:contestId:timestamp:firma"
     */
    public String generarStartToken(Integer userId, Integer contestId, long serverTimestamp) {
        String data = userId + ":" + contestId + ":" + serverTimestamp;
        String signature = generarHmac(data);
        return Base64.getEncoder().encodeToString((data + ":" + signature).getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Valida el token y retorna el timestamp original si es válido.
     * Retorna -1 si el token es inválido o ha sido modificado.
     */
    public long extraerTimestampYValidar(String tokenBase64, Integer userId, Integer contestId) {
        try {
            String decodedToken = new String(Base64.getDecoder().decode(tokenBase64), StandardCharsets.UTF_8);
            String[] parts = decodedToken.split(":");
            
            if (parts.length != 4) return -1;
            
            Integer tokenUserId = Integer.parseInt(parts[0]);
            Integer tokenContestId = Integer.parseInt(parts[1]);
            long tokenTimestamp = Long.parseLong(parts[2]);
            String tokenSignature = parts[3];

            if (!tokenUserId.equals(userId) || !tokenContestId.equals(contestId)) {
                return -1; // Intento de usar el token para otra persona o concurso
            }

            String data = parts[0] + ":" + parts[1] + ":" + parts[2];
            String expectedSignature = generarHmac(data);

            if (expectedSignature.equals(tokenSignature)) {
                return tokenTimestamp;
            }

        } catch (Exception e) {
            return -1;
        }
        return -1;
    }

    private String generarHmac(String data) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGO);
            SecretKeySpec secretKeySpec = new SecretKeySpec(secretKey.getBytes(StandardCharsets.UTF_8), HMAC_ALGO);
            mac.init(secretKeySpec);
            byte[] hmacBytes = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hmacBytes);
        } catch (Exception e) {
            throw new RuntimeException("Error generador firma de juego", e);
        }
    }
}
