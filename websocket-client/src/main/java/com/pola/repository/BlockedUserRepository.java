package com.pola.repository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashSet;
import java.util.Set;

import com.pola.database.DatabaseManager;

public class BlockedUserRepository {
    private final DatabaseManager dbManager;

    public BlockedUserRepository() {
        this.dbManager = DatabaseManager.getInstance();
        createTable();
    }

    private void createTable() {
        String sql = "CREATE TABLE IF NOT EXISTS blocked_by_users (username TEXT PRIMARY KEY)";
        try (Connection conn = dbManager.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void add(String username) {
        String sql = "INSERT OR IGNORE INTO blocked_by_users (username) VALUES (?)";
        try (Connection conn = dbManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, username);
            stmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public Set<String> findAll() {
        Set<String> users = new HashSet<>();
        String sql = "SELECT username FROM blocked_by_users";
        try (Connection conn = dbManager.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                users.add(rs.getString("username"));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return users;
    }

    public void remove(String username) {
        String sql = "DELETE FROM blocked_by_users WHERE username = ?";
        try (Connection conn = dbManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, username);
            stmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}