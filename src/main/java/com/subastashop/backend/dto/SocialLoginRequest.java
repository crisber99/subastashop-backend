package com.subastashop.backend.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class SocialLoginRequest {
    @NotBlank
    private String provider; // e.g. "GOOGLE"
    
    @NotBlank
    private String token; // The ID Token or Auth Token from the provider
}
