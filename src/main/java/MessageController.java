import io.javalin.Javalin;
import io.javalin.http.Context;
import io.javalin.http.UploadedFile;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import java.time.LocalDateTime;

public class MessageController {
    private static final ObjectMapper objectMapper = new ObjectMapper();

    public static void setupRoutes(Javalin app) {

        // Отправка сообщения
        app.post("/api/messages/send", ctx -> {
            User currentUser = ctx.sessionAttribute("user");
            if (currentUser == null) {
                ctx.status(401).json(Map.of("success", false, "message", "Not authenticated"));
                return;
            }

            // ПРОВЕРЯЕМ, МОЖЕТ ЛИ ПОЛЬЗОВАТЕЛЬ ОТПРАВЛЯТЬ СООБЩЕНИЯ
            if (!currentUser.canSendMessages()) {
                ctx.json(Map.of("success", false, "message", "Вы заблокированы и не можете отправлять сообщения"));
                return;
            }

            String chatIdParam = ctx.formParam("chatId");
            String content = ctx.formParam("content");

            if (chatIdParam == null || content == null || content.trim().isEmpty()) {
                ctx.json(Map.of("success", false, "message", "Неверные параметры"));
                return;
            }

            try {
                int chatId = Integer.parseInt(chatIdParam);
                boolean success = DatabaseService.sendMessage(chatId, currentUser.getId(), content.trim());

                if (success) {
                    ctx.json(Map.of("success", true, "message", "Сообщение отправлено"));
                } else {
                    ctx.json(Map.of("success", false, "message", "Ошибка отправки сообщения"));
                }
            } catch (NumberFormatException e) {
                ctx.json(Map.of("success", false, "message", "Неверный ID чата"));
            }
        });

        // Получение сообщений чата
        app.get("/api/chats/{chatId}/messages", ctx -> {
            User currentUser = ctx.sessionAttribute("user");
            if (currentUser == null) {
                ctx.status(401).json(Map.of("error", "Not authenticated"));
                return;
            }

            try {
                int chatId = Integer.parseInt(ctx.pathParam("chatId"));
                List<Message> messages = DatabaseService.getChatMessages(chatId);
                ctx.json(messages);
            } catch (NumberFormatException e) {
                ctx.status(400).json(Map.of("error", "Invalid chat ID"));
            }
        });

        // Создание приватного чата
        app.post("/api/chats/private", ctx -> {
            User currentUser = ctx.sessionAttribute("user");
            if (currentUser == null) {
                ctx.status(401).json(Map.of("success", false, "message", "Not authenticated"));
                return;
            }

            String otherUserIdParam = ctx.formParam("otherUserId");
            if (otherUserIdParam == null) {
                ctx.json(Map.of("success", false, "message", "Не указан ID пользователя"));
                return;
            }

            try {
                int otherUserId = Integer.parseInt(otherUserIdParam);
                int chatId = DatabaseService.createPrivateChat(currentUser.getId(), otherUserId);

                if (chatId != -1) {
                    ctx.json(Map.of("success", true, "chatId", chatId));
                } else {
                    ctx.json(Map.of("success", false, "message", "Ошибка создания чата"));
                }
            } catch (NumberFormatException e) {
                ctx.json(Map.of("success", false, "message", "Неверный ID пользователя"));
            }
        });

        // Получение всех чатов пользователя
        app.get("/api/chats", ctx -> {
            User currentUser = ctx.sessionAttribute("user");
            if (currentUser == null) {
                ctx.status(401).json(Map.of("error", "Not authenticated"));
                return;
            }

            List<Chat> chats = DatabaseService.getUserChats(currentUser.getId());
            ctx.json(chats);
        });

        // Загрузка аватарки
        app.post("/api/profile/upload-avatar", ctx -> {
            User currentUser = ctx.sessionAttribute("user");
            if (currentUser == null) {
                ctx.status(401).json(Map.of("success", false, "message", "Not authenticated"));
                return;
            }

            UploadedFile file = ctx.uploadedFile("avatar");
            if (file == null) {
                ctx.json(Map.of("success", false, "message", "Файл не выбран"));
                return;
            }

            // Проверяем тип файла
            String contentType = file.contentType();
            if (!isValidImageType(contentType)) {
                ctx.json(Map.of("success", false, "message", "Допустимы только JPG, JPEG и PNG файлы"));
                return;
            }

            // Проверяем размер файла (максимум 5MB)
            if (file.size() > 5 * 1024 * 1024) {
                ctx.json(Map.of("success", false, "message", "Файл слишком большой (макс. 5MB)"));
                return;
            }

            boolean success = DatabaseService.updateUserAvatar(currentUser.getId(), file);
            if (success) {
                // Обновляем пользователя в сессии
                User updatedUser = DatabaseService.getUserById(currentUser.getId());
                ctx.sessionAttribute("user", updatedUser);
                ctx.json(Map.of("success", true, "message", "Аватар обновлен"));
            } else {
                ctx.json(Map.of("success", false, "message", "Ошибка загрузки аватарки"));
            }
        });

        // Получение файла
        app.get("/api/files/{filename}", ctx -> {
            String filename = ctx.pathParam("filename");
            DatabaseService.serveFile(ctx, filename);
        });

        // Обновление статуса онлайн
        app.post("/api/user/ping", ctx -> {
            User currentUser = ctx.sessionAttribute("user");
            if (currentUser != null) {
                DatabaseService.updateUserOnlineStatus(currentUser.getId(), true);
            }
            ctx.json(Map.of("success", true));
        });

        // Выход (обновление статуса оффлайн)
        app.post("/api/user/logout", ctx -> {
            User currentUser = ctx.sessionAttribute("user");
            if (currentUser != null) {
                DatabaseService.updateUserOnlineStatus(currentUser.getId(), false);
            }
            ctx.json(Map.of("success", true));
        });

        // Удаление сообщения
        app.delete("/api/messages/{messageId}", ctx -> {
            User currentUser = ctx.sessionAttribute("user");
            if (currentUser == null) {
                ctx.status(401).json(Map.of("success", false, "message", "Not authenticated"));
                return;
            }

            try {
                int messageId = Integer.parseInt(ctx.pathParam("messageId"));
                boolean success = DatabaseService.deleteMessage(messageId);
                ctx.json(Map.of("success", success));
            } catch (NumberFormatException e) {
                ctx.status(400).json(Map.of("success", false, "message", "Invalid message ID"));
            }
        });

        // Создание жалобы на сообщение
        app.post("/api/reports", ctx -> {
            User currentUser = ctx.sessionAttribute("user");
            if (currentUser == null) {
                ctx.status(401).json(Map.of("success", false, "message", "Not authenticated"));
                return;
            }

            String messageIdParam = ctx.formParam("messageId");
            String reason = ctx.formParam("reason");

            if (messageIdParam == null || reason == null || reason.trim().isEmpty()) {
                ctx.json(Map.of("success", false, "message", "Неверные параметры"));
                return;
            }

            try {
                int messageId = Integer.parseInt(messageIdParam);
                boolean success = DatabaseService.createReport(messageId, currentUser.getId(), reason.trim());
                ctx.json(Map.of("success", success, "message", success ? "Жалоба отправлена" : "Ошибка отправки жалобы"));
            } catch (NumberFormatException e) {
                ctx.json(Map.of("success", false, "message", "Неверный ID сообщения"));
            }
        });
    }

    private static boolean isValidImageType(String contentType) {
        return contentType != null && (
                contentType.equals("image/jpeg") ||
                        contentType.equals("image/jpg") ||
                        contentType.equals("image/png")
        );
    }
}