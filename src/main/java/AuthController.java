import io.javalin.Javalin;
import java.util.Map;

public class AuthController {
    public static void setupRoutes(Javalin app) {
        // API для входа
        app.post("/api/login", ctx -> {
            String username = ctx.formParam("username");
            String password = ctx.formParam("password");

            System.out.println("🔐 Попытка входа: " + username);

            if (username == null || password == null || username.trim().isEmpty()) {
                ctx.json(Map.of("success", false, "message", "Заполните все поля"));
                return;
            }

            User user = DatabaseService.authenticateUser(username, password);
            if (user != null) {
                System.out.println("✅ Успешный вход: " + username + " (роль: " + user.getRole() + ")");
                DatabaseService.updateUserOnlineStatus(user.getId(), true);
                ctx.sessionAttribute("user", user);
                ctx.json(Map.of("success", true, "role", user.getRole()));
            } else {
                System.out.println("❌ Неудачный вход: " + username);
                ctx.json(Map.of("success", false, "message", "Неверное имя пользователя или пароль"));
            }
        });

        // API для регистрации
        app.post("/api/register", ctx -> {
            String username = ctx.formParam("username");
            String password = ctx.formParam("password");
            String email = ctx.formParam("email"); // Добавляем email

            System.out.println("📝 Попытка регистрации: " + username + ", email: " + email);

            if (username == null || password == null || email == null ||
                    username.trim().isEmpty() || email.trim().isEmpty()) {
                ctx.json(Map.of("success", false, "message", "Заполните все поля"));
                return;
            }

            if (username.length() < 3) {
                ctx.json(Map.of("success", false, "message", "Имя пользователя должно содержать минимум 3 символа"));
                return;
            }

            if (password.length() < 3) {
                ctx.json(Map.of("success", false, "message", "Пароль должен содержать минимум 3 символа"));
                return;
            }

            // Простая валидация email
            if (!email.contains("@") || !email.contains(".")) {
                ctx.json(Map.of("success", false, "message", "Введите корректный email адрес"));
                return;
            }

            boolean success = DatabaseService.registerUser(username, password, email);
            if (success) {
                System.out.println("✅ Успешная регистрация: " + username + " (" + email + ")");
                ctx.json(Map.of("success", true, "message", "Регистрация успешна! Теперь войдите в систему."));
            } else {
                System.out.println("❌ Неудачная регистрация: " + username);
                ctx.json(Map.of("success", false, "message", "Имя пользователя или email уже заняты"));
            }
        });

        // API для восстановления пароля
        app.post("/api/forgot-password", ctx -> {
            String username = ctx.formParam("username");

            System.out.println("🔐 Запрос восстановления пароля для: " + username);

            if (username == null || username.trim().isEmpty()) {
                ctx.json(Map.of("success", false, "message", "Введите имя пользователя"));
                return;
            }

            // Ищем пользователя
            User user = DatabaseService.getUserByUsername(username);
            if (user == null || user.getEmail() == null) {
                // Для безопасности не сообщаем, что пользователь не существует
                ctx.json(Map.of("success", true, "message", "Если пользователь существует и у него указан email, на него отправлен временный пароль"));
                return;
            }

            // Генерируем временный пароль
            String tempPassword = EmailService.generateTempPassword();

            // Обновляем пароль в базе данных (он автоматически захешируется в DatabaseService)
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

        // API для смены пароля
        app.post("/api/change-password", ctx -> {
            User currentUser = ctx.sessionAttribute("user");
            if (currentUser == null) {
                ctx.status(401).json(Map.of("success", false, "message", "Не авторизован"));
                return;
            }

            String currentPassword = ctx.formParam("currentPassword");
            String newPassword = ctx.formParam("newPassword");

            System.out.println("🔐 Запрос смены пароля для пользователя: " + currentUser.getUsername());

            if (currentPassword == null || newPassword == null ||
                    currentPassword.trim().isEmpty() || newPassword.trim().isEmpty()) {
                ctx.json(Map.of("success", false, "message", "Заполните все поля"));
                return;
            }

            if (newPassword.length() < 3) {
                ctx.json(Map.of("success", false, "message", "Новый пароль должен содержать минимум 3 символа"));
                return;
            }

            // Проверяем текущий пароль
            User verifiedUser = DatabaseService.authenticateUser(currentUser.getUsername(), currentPassword);
            if (verifiedUser == null) {
                ctx.json(Map.of("success", false, "message", "Неверный текущий пароль"));
                return;
            }

            // Меняем пароль
            boolean success = DatabaseService.updateUserPassword(currentUser.getId(), newPassword);

            if (success) {
                System.out.println("✅ Пароль успешно изменен для пользователя: " + currentUser.getUsername());

                // Обновляем пользователя в сессии (если нужно)
                User updatedUser = DatabaseService.getUserById(currentUser.getId());
                ctx.sessionAttribute("user", updatedUser);

                ctx.json(Map.of("success", true, "message", "Пароль успешно изменен"));
            } else {
                System.out.println("❌ Ошибка изменения пароля для пользователя: " + currentUser.getUsername());
                ctx.json(Map.of("success", false, "message", "Ошибка изменения пароля"));
            }
        });

        // Получение текущего пользователя
        app.get("/api/user/current", ctx -> {
            User user = ctx.sessionAttribute("user");
            if (user != null) {
                ctx.json(user);
            } else {
                ctx.status(401).json(Map.of("error", "Not authenticated"));
            }
        });

        // Выход
        app.post("/api/logout", ctx -> {
            User currentUser = ctx.sessionAttribute("user");
            if (currentUser != null) {
                System.out.println("🚪 Выход пользователя: " + currentUser.getUsername());

                // ОБНОВЛЯЕМ СТАТУС ОФФЛАЙН
                DatabaseService.updateUserOnlineStatus(currentUser.getId(), false);
            }
            ctx.req().getSession().invalidate();
            ctx.redirect("/");
        });
    }
}