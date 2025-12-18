
import io.javalin.Javalin;
import io.javalin.http.Context;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.time.LocalDateTime;

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

        // Обработка жалобы
        app.post("/api/admin/reports/{id}/decide", ctx -> {
            User user = ctx.sessionAttribute("user");
            if (user == null || !"ADMIN".equals(user.getRole())) {
                ctx.status(403).json(Map.of("error", "Access denied"));
                return;
            }

            int reportId = Integer.parseInt(ctx.pathParam("id"));
            String decision = ctx.formParam("decision");
            String daysParam = ctx.formParam("days");
            int days = daysParam != null ? Integer.parseInt(daysParam) : 0;

            boolean success = DatabaseService.processReport(reportId, decision, days, user.getId());
            ctx.json(Map.of("success", success));
        });

        // Получение всех пользователей (ИСПРАВЛЕННЫЙ МЕТОД)
        app.get("/api/admin/users", ctx -> {
            User user = ctx.sessionAttribute("user");

            System.out.println("🔍 Запрос /api/admin/users от пользователя: " +
                    (user != null ? user.getUsername() : "null"));

            if (user == null || !"ADMIN".equals(user.getRole())) {
                System.out.println("❌ Доступ запрещен!");
                ctx.status(403).json(Map.of("error", "Access denied"));
                return;
            }

            System.out.println("✅ Доступ разрешен для администратора: " + user.getUsername());

            // ПРОСТО ВОЗВРАЩАЕМ ПОЛЬЗОВАТЕЛЕЙ
            List<User> users = DatabaseService.getAllUsers();
            System.out.println("📊 Найдено пользователей: " + users.size());

            ctx.json(users); // Просто возвращаем список пользователей
        });
        // Разблокировка пользователя
        app.post("/api/admin/users/{id}/unblock", ctx -> {
            User admin = ctx.sessionAttribute("user");
            if (admin == null || !"ADMIN".equals(admin.getRole())) {
                ctx.status(403).json(Map.of("error", "Access denied"));
                return;
            }

            int userId = Integer.parseInt(ctx.pathParam("id"));
            boolean success = DatabaseService.unblockUser(userId);
            ctx.json(Map.of("success", success));
        });

        // Блокировка пользователя (НОВЫЙ МЕТОД)
        app.post("/api/admin/users/{id}/block", ctx -> {
            User admin = ctx.sessionAttribute("user");
            if (admin == null || !"ADMIN".equals(admin.getRole())) {
                ctx.status(403).json(Map.of("error", "Access denied"));
                return;
            }

            int userId = Integer.parseInt(ctx.pathParam("id"));
            String type = ctx.formParam("type");
            String reason = ctx.formParam("reason");
            String daysParam = ctx.formParam("days");

            System.out.println("🔒 Блокировка пользователя " + userId +
                    ", тип: " + type +
                    ", причина: " + reason +
                    ", дней: " + daysParam);

            // ВЫЗЫВАЕМ ПРАВИЛЬНЫЙ МЕТОД - blockUser с правильными параметрами
            boolean success = DatabaseService.blockUser(userId, type, reason, daysParam, admin.getId());

            System.out.println("✅ Результат блокировки: " + success);

            if (success) {
                ctx.json(Map.of("success", true, "message", "Пользователь заблокирован"));
            } else {
                ctx.json(Map.of("success", false, "message", "Ошибка блокировки"));
            }
        });

        app.post("/api/admin/users/{id}/notify", ctx -> {
            User admin = ctx.sessionAttribute("user");
            if (admin == null || !"ADMIN".equals(admin.getRole())) {
                ctx.status(403).json(Map.of("error", "Access denied"));
                return;
            }

            int userId = Integer.parseInt(ctx.pathParam("id"));
            String action = ctx.bodyAsClass(Map.class).get("action").toString();

            System.out.println("📢 Уведомление для пользователя " + userId + ": " + action);

            // Здесь можно добавить логику уведомления через WebSocket
            // Пока просто логируем
            ctx.json(Map.of("success", true, "message", "Notification logged"));
        });

    }
}
