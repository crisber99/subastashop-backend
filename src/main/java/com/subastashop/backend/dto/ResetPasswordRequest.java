package com.subastashop.backend.dto;

import com.subastashop.backend.validators.ValidUserSecurity;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
@ValidUserSecurity
public class ResetPasswordRequest {
    @NotBlank(message = "El email es obligatorio")
    @Email(message = "Formato de email inválido")
    private String email;

    @NotBlank(message = "El código es obligatorio")
    private String code;

    @NotBlank(message = "La contraseña es obligatoria")
    private String newPassword;
}
