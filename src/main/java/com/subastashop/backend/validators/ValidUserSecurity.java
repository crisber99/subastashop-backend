package com.subastashop.backend.validators;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.*;

@Documented
@Constraint(validatedBy = UserSecurityValidator.class)
@Target({ElementType.TYPE, ElementType.ANNOTATION_TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidUserSecurity {
    String message() default "Error de seguridad en el usuario";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
