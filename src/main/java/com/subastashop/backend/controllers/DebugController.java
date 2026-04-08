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
    public List<Map<String, Object>> getColumns(@PathVariable String table) {
        // Query to get column names from SQL Server
        String sql = "SELECT COLUMN_NAME, DATA_TYPE FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME = ?";
        return jdbcTemplate.queryForList(sql, table);
    }
    
    @GetMapping("/tables")
    public List<Map<String, Object>> getTables() {
        // Query to list all tables to see if we are using the right table names
        String sql = "SELECT TABLE_NAME FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_TYPE = 'BASE TABLE'";
        return jdbcTemplate.queryForList(sql);
    }
}
