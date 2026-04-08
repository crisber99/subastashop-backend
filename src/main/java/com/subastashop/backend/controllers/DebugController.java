package com.subastashop.backend.controllers;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/public/debug") // Public to avoid token issues during debugging
public class DebugController {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @GetMapping("/columns/{table}")
    public Object getColumns(@PathVariable String table) {
        try {
            String sql = "SELECT COLUMN_NAME, DATA_TYPE FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME = ?";
            return jdbcTemplate.queryForList(sql, table);
        } catch (Exception e) {
            Map<String, Object> error = new java.util.HashMap<>();
            error.put("error", e.getMessage());
            error.put("type", e.getClass().getName());
            return error;
        }
    }
    
    @GetMapping("/tables")
    public Object getTables() {
        try {
            String sql = "SELECT TABLE_NAME FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_TYPE = 'BASE TABLE'";
            return jdbcTemplate.queryForList(sql);
        } catch (Exception e) {
            Map<String, Object> error = new java.util.HashMap<>();
            error.put("error", e.getMessage());
            error.put("type", e.getClass().getName());
            return error;
        }
    }
}
