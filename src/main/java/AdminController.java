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
    }
}