import io.javalin.Javalin;
import io.javalin.http.Context;
import io.javalin.http.UploadedFile;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.time.LocalDateTime;

public class MessageController {
    private static final ObjectMapper objectMapper = new ObjectMapper();

    public static void setupRoutes(Javalin app) {
        // Отправка сообщения (ОБЫЧНОЕ)
        app.post("/api/messages/send", ctx -> {
            User currentUser = ctx.sessionAttribute("user");
            if (currentUser == null) {
                ctx.status(401).json(Map.of("success", false, "message", "Not authenticated"));
                return;
            }

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

        // ОТПРАВКА СООБЩЕНИЯ С ФАЙЛОМ (НОВЫЙ ЭНДПОИНТ)
        app.post("/api/messages/send-with-file", ctx -> {
            User currentUser = ctx.sessionAttribute("user");
            if (currentUser == null) {
                ctx.status(401).json(Map.of("success", false, "message", "Not authenticated"));
                return;
            }

            if (!currentUser.canSendMessages()) {
                ctx.json(Map.of("success", false, "message", "Вы заблокированы и не можете отправлять сообщения"));
                return;
            }

            String chatIdParam = ctx.formParam("chatId");
            String content = ctx.formParam("content");
            UploadedFile file = ctx.uploadedFile("file");

            if (chatIdParam == null || (content == null && file == null)) {
                ctx.json(Map.of("success", false, "message", "Неверные параметры"));
                return;
            }

            try {
                int chatId = Integer.parseInt(chatIdParam);

                // Если есть файл
                if (file != null) {
                    // Проверяем размер файла
                    if (file.size() > 10 * 1024 * 1024) {
                        ctx.json(Map.of("success", false, "message", "Файл слишком большой (макс. 10MB)"));
                        return;
                    }

                    // Получаем оригинальное имя и расширение
                    String originalFilename = file.filename();
                    if (originalFilename == null || originalFilename.isEmpty()) {
                        ctx.json(Map.of("success", false, "message", "Имя файла не указано"));
                        return;
                    }

                    String fileExtension = getFileExtension(originalFilename);

                    // Создаем уникальное имя для хранения
                    String storedFilename = "chat_" + chatId + "_" + currentUser.getId() +
                            "_" + System.currentTimeMillis() + fileExtension;

                    // Создаем директорию, если не существует
                    String uploadDir = "uploads/chat_files/";
                    Files.createDirectories(Paths.get(uploadDir));

                    // Сохраняем файл на диск
                    java.nio.file.Path filePath = Paths.get(uploadDir + storedFilename);
                    Files.copy(file.content(), filePath, StandardCopyOption.REPLACE_EXISTING);

                    // Определяем тип файла
                    String fileType = determineFileType(fileExtension);
                    String fileUrl = "/api/files/chat_files/" + storedFilename;

                    // ВАЖНО: Сохраняем в БД через новый метод
                    boolean success = DatabaseService.sendMessageWithFile(
                            chatId,
                            currentUser.getId(),
                            content != null ? content.trim() : "",
                            originalFilename,
                            fileType,
                            fileUrl,
                            file.size()
                    );

                    if (success) {
                        ctx.json(Map.of(
                                "success", true,
                                "message", "Файл отправлен",
                                "fileUrl", fileUrl
                        ));
                    } else {
                        // Откатываем: удаляем файл с диска
                        Files.deleteIfExists(filePath);
                        ctx.json(Map.of("success", false, "message", "Ошибка сохранения в БД"));
                    }
                } else {
                    // Если файла нет, отправляем обычное сообщение
                    boolean success = DatabaseService.sendMessage(
                            chatId,
                            currentUser.getId(),
                            content != null ? content.trim() : ""
                    );
                    ctx.json(Map.of("success", success, "message",
                            success ? "Сообщение отправлено" : "Ошибка отправки"));
                }
            } catch (Exception e) {
                System.err.println("❌ Ошибка отправки файла: " + e.getMessage());
                e.printStackTrace();
                ctx.json(Map.of("success", false, "message", "Ошибка сервера: " + e.getMessage()));
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

            // Проверяем размер файла
            if (file.size() > 5 * 1024 * 1024) {
                ctx.json(Map.of("success", false, "message", "Файл слишком большой (макс. 5MB)"));
                return;
            }

            boolean success = DatabaseService.updateUserAvatar(currentUser.getId(), file);
            if (success) {
                User updatedUser = DatabaseService.getUserById(currentUser.getId());
                ctx.sessionAttribute("user", updatedUser);
                ctx.json(Map.of("success", true, "message", "Аватар обновлен"));
            } else {
                ctx.json(Map.of("success", false, "message", "Ошибка загрузки аватарки"));
            }
        });

        // Получение файла (ОБЩИЙ)
        app.get("/api/files/{filename}", ctx -> {
            String filename = ctx.pathParam("filename");
            DatabaseService.serveFile(ctx, filename);
        });

        // Получение файлов чата (ОТДЕЛЬНЫЙ ЭНДПОИНТ)
        app.get("/api/files/chat_files/{filename}", ctx -> {
            String filename = ctx.pathParam("filename");
            try {
                java.nio.file.Path filePath = Paths.get("uploads/chat_files/" + filename);
                if (Files.exists(filePath)) {
                    byte[] fileBytes = Files.readAllBytes(filePath);

                    // Определяем Content-Type
                    String contentType = Files.probeContentType(filePath);
                    if (contentType == null) {
                        // Определяем по расширению
                        String extension = getFileExtension(filename).toLowerCase();
                        contentType = getContentTypeByExtension(extension);
                    }

                    ctx.contentType(contentType);
                    ctx.result(fileBytes);
                } else {
                    ctx.status(404).result("File not found");
                }
            } catch (Exception e) {
                System.err.println("❌ Ошибка отдачи файла: " + e.getMessage());
                ctx.status(500).result("Error serving file");
            }
        });

        // Обновление статуса онлайн
        app.post("/api/user/ping", ctx -> {
            User currentUser = ctx.sessionAttribute("user");
            if (currentUser != null) {
                DatabaseService.updateUserOnlineStatus(currentUser.getId(), true);
            }
            ctx.json(Map.of("success", true));
        });

        // Выход
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
                ctx.json(Map.of("success", success, "message",
                        success ? "Жалоба отправлена" : "Ошибка отправки жалобы"));
            } catch (NumberFormatException e) {
                ctx.json(Map.of("success", false, "message", "Неверный ID сообщения"));
            }
        });

        app.post("/api/messages/upload-file", ctx -> {
            User currentUser = ctx.sessionAttribute("user");
            if (currentUser == null) {
                ctx.status(401).json(Map.of("success", false, "message", "Not authenticated"));
                return;
            }

            if (!currentUser.canSendMessages()) {
                ctx.json(Map.of("success", false, "message", "Вы заблокированы и не можете отправлять сообщения"));
                return;
            }

            String chatIdParam = ctx.formParam("chatId");
            String content = ctx.formParam("content");
            UploadedFile file = ctx.uploadedFile("file");

            System.out.println("📤 Запрос отправки сообщения. ChatId: " + chatIdParam +
                    ", content: " + content + ", hasFile: " + (file != null));

            if (chatIdParam == null || (content == null && file == null)) {
                ctx.json(Map.of("success", false, "message", "Неверные параметры"));
                return;
            }

            try {
                int chatId = Integer.parseInt(chatIdParam);

                // ЕСЛИ ЕСТЬ ФАЙЛ
                if (file != null) {
                    System.out.println("📎 Отправка файла: " + file.filename() + ", size: " + file.size());

                    // Проверяем размер файла (максимум 10MB)
                    if (file.size() > 10 * 1024 * 1024) {
                        ctx.json(Map.of("success", false, "message", "Файл слишком большой (макс. 10MB)"));
                        return;
                    }

                    // Получаем оригинальное имя и расширение
                    String originalFilename = file.filename();
                    if (originalFilename == null || originalFilename.isEmpty()) {
                        ctx.json(Map.of("success", false, "message", "Имя файла не указано"));
                        return;
                    }

                    String fileExtension = getFileExtension(originalFilename);

                    // Создаем уникальное имя для хранения
                    String storedFilename = "chat_" + chatId + "_" + currentUser.getId() +
                            "_" + System.currentTimeMillis() + fileExtension;

                    // Создаем директорию, если не существует
                    String uploadDir = "uploads/chat_files/";
                    Files.createDirectories(Paths.get(uploadDir));

                    // Сохраняем файл на диск
                    java.nio.file.Path filePath = Paths.get(uploadDir + storedFilename);
                    Files.copy(file.content(), filePath, StandardCopyOption.REPLACE_EXISTING);

                    // Определяем тип файла
                    String fileType = determineFileType(fileExtension);
                    String fileUrl = "/api/files/chat_files/" + storedFilename;

                    System.out.println("💾 Файл сохранен: " + filePath + ", URL: " + fileUrl);

                    // ВАЖНО: Сохраняем в БД через метод ДЛЯ ФАЙЛОВ
                    boolean success = DatabaseService.sendMessageWithFile(
                            chatId,
                            currentUser.getId(),
                            content != null ? content.trim() : "",
                            originalFilename,
                            fileType,
                            fileUrl,
                            file.size()
                    );

                    if (success) {
                        System.out.println("✅ Файл успешно сохранен в БД");
                        ctx.json(Map.of(
                                "success", true,
                                "message", "Файл отправлен",
                                "fileUrl", fileUrl
                        ));
                    } else {
                        // Откатываем: удаляем файл с диска
                        Files.deleteIfExists(filePath);
                        System.out.println("❌ Ошибка сохранения файла в БД");
                        ctx.json(Map.of("success", false, "message", "Ошибка сохранения в БД"));
                    }
                } else {
                    // ЕСЛИ ФАЙЛА НЕТ - отправляем обычное сообщение
                    System.out.println("💬 Отправка текстового сообщения");
                    boolean success = DatabaseService.sendMessage(
                            chatId,
                            currentUser.getId(),
                            content != null ? content.trim() : ""
                    );
                    ctx.json(Map.of("success", success, "message",
                            success ? "Сообщение отправлено" : "Ошибка отправки"));
                }
            } catch (NumberFormatException e) {
                System.err.println("❌ Неверный ID чата: " + chatIdParam);
                ctx.json(Map.of("success", false, "message", "Неверный ID чата"));
            } catch (Exception e) {
                System.err.println("❌ Ошибка отправки файла: " + e.getMessage());
                e.printStackTrace();
                ctx.json(Map.of("success", false, "message", "Ошибка сервера: " + e.getMessage()));
            }
        });
    }

    // Вспомогательные методы
    private static String getFileExtension(String filename) {
        if (filename == null || filename.isEmpty()) {
            return "";
        }
        int lastDot = filename.lastIndexOf(".");
        return lastDot > 0 ? filename.substring(lastDot) : "";
    }

    private static String determineFileType(String extension) {
        extension = extension.toLowerCase();
        if (extension.matches("(\\.jpg|\\.jpeg|\\.png|\\.gif|\\.bmp|\\.webp)$")) {
            return "image";
        } else if (extension.matches("(\\.mp4|\\.avi|\\.mov|\\.wmv|\\.flv|\\.mkv)$")) {
            return "video";
        } else if (extension.matches("(\\.mp3|\\.wav|\\.ogg|\\.flac|\\.aac)$")) {
            return "audio";
        } else if (extension.matches("\\.pdf$")) {
            return "pdf";
        } else if (extension.matches("(\\.doc|\\.docx)$")) {
            return "word";
        } else if (extension.matches("(\\.xls|\\.xlsx)$")) {
            return "excel";
        } else if (extension.matches("(\\.zip|\\.rar|\\.7z|\\.tar|\\.gz)$")) {
            return "archive";
        } else if (extension.matches("(\\.txt|\\.rtf|\\.md)$")) {
            return "text";
        } else {
            return "document";
        }
    }

    private static String getContentTypeByExtension(String extension) {
        switch (extension) {
            case ".jpg":
            case ".jpeg":
                return "image/jpeg";
            case ".png":
                return "image/png";
            case ".gif":
                return "image/gif";
            case ".pdf":
                return "application/pdf";
            case ".zip":
                return "application/zip";
            case ".mp3":
                return "audio/mpeg";
            case ".mp4":
                return "video/mp4";
            case ".txt":
                return "text/plain";
            case ".doc":
                return "application/msword";
            case ".docx":
                return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            default:
                return "application/octet-stream";
        }
    }

    private static boolean isValidImageType(String contentType) {
        return contentType != null && (
                contentType.equals("image/jpeg") ||
                        contentType.equals("image/jpg") ||
                        contentType.equals("image/png") ||
                        contentType.equals("image/gif")
        );
    }
}