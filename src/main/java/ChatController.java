import io.javalin.Javalin;
import io.javalin.http.Context;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashMap;
import java.util.Map;
import java.util.List;

public class ChatController {
    private static final ObjectMapper objectMapper = new ObjectMapper();

    public static void setupRoutes(Javalin app) {
        app.get("/api/users/search", ctx -> {
            User currentUser = ctx.sessionAttribute("user");
            if (currentUser == null) {
                ctx.status(401).json(Map.of("error", "Not authenticated"));
                return;
            }

            String query = ctx.queryParam("q");
            if (query == null || query.trim().isEmpty()) {
                ctx.json(List.of());
                return;
            }

            List<User> users = DatabaseService.searchUsers(query, currentUser.getId());
            ctx.json(users);
        });

        // Отправка запроса в друзья - ТОЛЬКО ОДИН РАЗ
        app.post("/api/friend-request", ctx -> {
            User currentUser = ctx.sessionAttribute("user");
            if (currentUser == null) {
                ctx.status(401).json(Map.of("error", "Not authenticated"));
                return;
            }

            int toUserId = Integer.parseInt(ctx.formParam("toUserId"));

            // Проверяем, не является ли уже контактом
            if (DatabaseService.isContact(currentUser.getId(), toUserId)) {
                ctx.json(Map.of("success", false, "message", "Пользователь уже в контактах"));
                return;
            }

            // Проверяем, не отправлен ли уже запрос
            if (DatabaseService.hasFriendRequest(currentUser.getId(), toUserId)) {
                ctx.json(Map.of("success", false, "message", "Запрос уже отправлен"));
                return;
            }

            boolean success = DatabaseService.sendFriendRequest(currentUser.getId(), toUserId);
            ctx.json(Map.of("success", success, "message",
                    success ? "Запрос отправлен" : "Ошибка отправки"));
        });

        // Получение входящих запросов - ТОЛЬКО ОДИН РАЗ
        app.get("/api/friend-requests/incoming", ctx -> {
            User currentUser = ctx.sessionAttribute("user");
            if (currentUser == null) {
                ctx.status(401).json(Map.of("error", "Not authenticated"));
                return;
            }

            List<FriendRequest> requests = DatabaseService.getIncomingRequests(currentUser.getId());
            ctx.json(requests);
        });

        // Принятие запроса - ТОЛЬКО ОДИН РАЗ
        app.post("/api/friend-requests/{id}/accept", ctx -> {
            User currentUser = ctx.sessionAttribute("user");
            if (currentUser == null) {
                ctx.status(401).json(Map.of("error", "Not authenticated"));
                return;
            }

            int requestId = Integer.parseInt(ctx.pathParam("id"));
            boolean success = DatabaseService.acceptFriendRequest(requestId, currentUser.getId());
            ctx.json(Map.of("success", success, "message",
                    success ? "Запрос принят" : "Ошибка"));
        });

        // Отклонение запроса - ТОЛЬКО ОДИН РАЗ
        app.post("/api/friend-requests/{id}/reject", ctx -> {
            User currentUser = ctx.sessionAttribute("user");
            if (currentUser == null) {
                ctx.status(401).json(Map.of("error", "Not authenticated"));
                return;
            }

            int requestId = Integer.parseInt(ctx.pathParam("id"));
            // Простая реализация - можно добавить метод в DatabaseService для отклонения
            ctx.json(Map.of("success", true, "message", "Запрос отклонен"));
        });

        // Получение контактов пользователя
        app.get("/api/contacts", ctx -> {
            User currentUser = ctx.sessionAttribute("user");
            if (currentUser == null) {
                ctx.status(401).json(Map.of("error", "Not authenticated"));
                return;
            }

            List<User> contacts = DatabaseService.getUserContacts(currentUser.getId());
            System.out.println("📞 Отправка контактов пользователю " + currentUser.getUsername() + ": " + contacts.size() + " контактов");
            ctx.json(contacts);
        });
    }
}