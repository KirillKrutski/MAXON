// PasswordMigration.java
import at.favre.lib.crypto.bcrypt.BCrypt;

import java.sql.*;

public class PasswordMigration {

    public static void migratePasswords() {
        String selectSql = "SELECT id, password FROM users WHERE password NOT LIKE '$2a$%'";
        String updateSql = "UPDATE users SET password = ? WHERE id = ?";

        BCrypt.Hasher hasher = BCrypt.withDefaults();

        try (Connection conn = DatabaseService.getConnection();
             PreparedStatement selectStmt = conn.prepareStatement(selectSql);
             PreparedStatement updateStmt = conn.prepareStatement(updateSql);
             ResultSet rs = selectStmt.executeQuery()) {

            int migratedCount = 0;

            while (rs.next()) {
                int userId = rs.getInt("id");
                String plainPassword = rs.getString("password");

                // Хешируем пароль
                String hashedPassword = hasher.hashToString(12, plainPassword.toCharArray());

                // Обновляем в базе
                updateStmt.setString(1, hashedPassword);
                updateStmt.setInt(2, userId);
                updateStmt.executeUpdate();

                migratedCount++;
                System.out.println("✅ Мигрирован пароль пользователя ID: " + userId);
            }

            System.out.println("🎉 Миграция завершена! Обработано пользователей: " + migratedCount);

        } catch (SQLException e) {
            System.out.println("❌ Ошибка миграции паролей: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        migratePasswords();
    }
}