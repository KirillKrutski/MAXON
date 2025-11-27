import io.javalin.Javalin;
import io.javalin.http.Context;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashMap;
import java.util.Map;
import java.util.List;

public class AdminController {
    private static final ObjectMapper objectMapper = new ObjectMapper();

    public static void setupRoutes(Javalin app) {
        // Получение всех жалоб
        app.get("/api/admin/reports", ctx -> {
            User user = ctx.sessionAttribute("user");
            if (user == null || !"ADMIN".equals(user.getRole())) {
                ctx.status(403).json(Map.of("error", "Access denied"));
                return;
            }

            List<Report> reports = DatabaseService.getPendingReports();
            ctx.json(reports);
        });

        // Обработка жалобы - ИСПРАВЛЕННАЯ ВЕРСИЯ
        app.post("/api/admin/reports/{id}/decide", ctx -> {
            User user = ctx.sessionAttribute("user");
            if (user == null || !"ADMIN".equals(user.getRole())) {
                ctx.status(403).json(Map.of("error", "Access denied"));
                return;
            }

            int reportId = Integer.parseInt(ctx.pathParam("id"));
            String decision = ctx.formParam("decision"); // ТОЛЬКО один параметр
            String daysParam = ctx.formParam("days"); // Отдельно получаем дни
            int days = daysParam != null ? Integer.parseInt(daysParam) : 0;

            boolean success = DatabaseService.processReport(reportId, decision, days, user.getId());
            ctx.json(Map.of("success", success));
        });

        // Получение всех пользователей
        app.get("/api/admin/users", ctx -> {
            User user = ctx.sessionAttribute("user");
            if (user == null || !"ADMIN".equals(user.getRole())) {
                ctx.status(403).json(Map.of("error", "Access denied"));
                return;
            }

            List<User> users = DatabaseService.getAllUsers();
            ctx.json(users);
        });

        // Разблокировка пользователя
        app.post("/api/admin/users/{id}/unblock", ctx -> {
            User user = ctx.sessionAttribute("user");
            if (user == null || !"ADMIN".equals(user.getRole())) {
                ctx.status(403).json(Map.of("error", "Access denied"));
                return;
            }

            int userId = Integer.parseInt(ctx.pathParam("id"));
            boolean success = DatabaseService.unblockUser(userId);
            ctx.json(Map.of("success", success));
        });

        // Добавьте этот метод в класс AuthController
        app.post("/api/forgot-password", ctx -> {
            String username = ctx.formParam("username");

            System.out.println("🔐 Запрос восстановления пароля для: " + username);

            if (username == null || username.trim().isEmpty()) {
                ctx.json(Map.of("success", false, "message", "Введите имя пользователя"));
                return;
            }

            // Ищем пользователя
            User user = DatabaseService.getUserByUsername(username);
            if (user == null) {
                // Для безопасности не сообщаем, что пользователь не существует
                ctx.json(Map.of("success", true, "message", "Если пользователь существует, на его email отправлен временный пароль"));
                return;
            }

            // Генерируем временный пароль
            String tempPassword = EmailService.generateTempPassword();

            // Обновляем пароль в базе данных
            boolean passwordUpdated = DatabaseService.updateUserPassword(user.getId(), tempPassword);

            if (passwordUpdated) {
                // Отправляем email с временным паролем
                boolean emailSent = EmailService.sendPasswordResetEmail(user.getEmail(), tempPassword);

                if (emailSent) {
                    ctx.json(Map.of("success", true, "message", "Временный пароль отправлен на ваш email"));
                } else {
                    ctx.json(Map.of("success", false, "message", "Ошибка отправки email. Обратитесь к администратору."));
                }
            } else {
                ctx.json(Map.of("success", false, "message", "Ошибка обновления пароля"));
            }
        });
    }
}