package com.subastashop.backend.validators;

import com.subastashop.backend.dto.RegisterRequest;
import com.subastashop.backend.dto.ResetPasswordRequest;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public class UserSecurityValidator implements ConstraintValidator<ValidUserSecurity, Object> {

    @Override
    public boolean isValid(Object obj, ConstraintValidatorContext context) {
        String password;
        String email;
        String alias = null;

        if (obj instanceof RegisterRequest) {
            RegisterRequest req = (RegisterRequest) obj;
            password = req.getPassword();
            email = req.getEmail();
            alias = req.getAlias();
        } else if (obj instanceof ResetPasswordRequest) {
            ResetPasswordRequest req = (ResetPasswordRequest) obj;
            password = req.getNewPassword();
            email = req.getEmail();
        } else {
            return true;
        }

        if (password == null || email == null) return false;

        List<String> errors = new ArrayList<>();

        // REGLA 1: Longitud
        if (password.length() < 10) {
            errors.add("Mínimo 10 caracteres");
        }
        // REGLA 2: Mayúscula y Minúscula
        if (!Pattern.compile("[A-Z]").matcher(password).find()) {
            errors.add("Al menos una letra mayúscula");
        }
        if (!Pattern.compile("[a-z]").matcher(password).find()) {
            errors.add("Al menos una letra minúscula");
        }
        // REGLA 3: Número
        if (!Pattern.compile("[0-9]").matcher(password).find()) {
            errors.add("Al menos un número");
        }
        // REGLA 4: Carácter especial
        if (!Pattern.compile("[!@#$%^&*(),.?\":{}|<>]").matcher(password).find()) {
            errors.add("Al menos un carácter especial (@, #, $, %, etc.)");
        }

        // REGLA 5: No contener datos personales (email prefix o alias)
        String emailPrefix = email.split("@")[0].toLowerCase();
        if (password.toLowerCase().contains(emailPrefix)) {
            errors.add("La contraseña no puede contener tu nombre de usuario o parte de tu email");
        }
        if (alias != null && !alias.isBlank() && password.toLowerCase().contains(alias.toLowerCase())) {
            errors.add("La contraseña no puede contener tu alias");
        }

        if (!errors.isEmpty()) {
            context.disableDefaultConstraintViolation();
            for (String error : errors) {
                // Asociamos el error al campo de contraseña para que el frontend lo ubique fácil
                String fieldName = (obj instanceof RegisterRequest) ? "password" : "newPassword";
                context.buildConstraintViolationWithTemplate(error)
                       .addPropertyNode(fieldName)
                       .addConstraintViolation();
            }
            return false;
        }

        return true;
    }
}
