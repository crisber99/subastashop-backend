package com.subastashop.backend.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.*;

class JwtServiceTest {

    private JwtService jwtService;
    private final String testSecret = "404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970";

    @BeforeEach
    void setUp() {
        jwtService = new JwtService();
        // Inyectamos el secreto manualmente para el test unitario
        ReflectionTestUtils.setField(jwtService, "SECRET_KEY", testSecret);
    }

    @Test
    void shouldGenerateValidToken() {
        UserDetails userDetails = new User("test@example.com", "password", new ArrayList<>());
        String token = jwtService.generateToken(userDetails);

        assertNotNull(token);
        assertEquals("test@example.com", jwtService.extractUsername(token));
    }

    @Test
    void shouldValidateCorrectToken() {
        UserDetails userDetails = new User("test@example.com", "password", new ArrayList<>());
        String token = jwtService.generateToken(userDetails);

        assertTrue(jwtService.isTokenValid(token, userDetails));
    }

    @Test
    void shouldInvalidateTokenForDifferentUser() {
        UserDetails userDetails1 = new User("user1@example.com", "password", new ArrayList<>());
        UserDetails userDetails2 = new User("user2@example.com", "password", new ArrayList<>());
        
        String token1 = jwtService.generateToken(userDetails1);

        assertFalse(jwtService.isTokenValid(token1, userDetails2));
    }
}
