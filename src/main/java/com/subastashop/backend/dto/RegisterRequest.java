package com.subastashop.backend.dto;

import lombok.Data;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import com.subastashop.backend.validators.ValidUserSecurity;

@Data
@ValidUserSecurity
public class RegisterRequest {
    @NotBlank(message = "El email es obligatorio")
    @Email(message = "Formato de email inválido")
    private String email;

    @NotBlank(message = "La contraseña es obligatoria")
    private String password;

    @NotBlank(message = "El nombre es obligatorio")
    private String nombre;

    @NotBlank(message = "El alias es obligatorio")
    @Size(min = 3, max = 20, message = "El alias debe tener entre 3 y 20 caracteres")
    private String alias;

    private String telefono;
    private String direccion;
}
