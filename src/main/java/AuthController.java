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

            System.out.println("📝 Попытка регистрации: " + username);

            if (username == null || password == null || username.trim().isEmpty()) {
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

            boolean success = DatabaseService.registerUser(username, password);
            if (success) {
                System.out.println("✅ Успешная регистрация: " + username);
                ctx.json(Map.of("success", true, "message", "Регистрация успешна! Теперь войдите в систему."));
            } else {
                System.out.println("❌ Неудачная регистрация: " + username);
                ctx.json(Map.of("success", false, "message", "Имя пользователя уже занято"));
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

        // Выход - ТОЛЬКО ОДИН РАЗ!
        app.post("/api/logout", ctx -> {
            System.out.println("🚪 Выход пользователя");
            ctx.req().getSession().invalidate();
            ctx.redirect("/");
        });
    }
}