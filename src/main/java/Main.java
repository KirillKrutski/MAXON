import io.javalin.Javalin;
import io.javalin.http.staticfiles.Location;
import io.javalin.rendering.template.JavalinThymeleaf;
import io.javalin.json.JavalinJackson;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;
import java.util.HashMap;
import java.util.Map;

public class Main {
    public static void main(String[] args) {
        ObjectMapper objectMapper = createObjectMapper();

        Javalin app = Javalin.create(config -> {
            // Статические файлы (CSS, JS) из public
            config.staticFiles.add(staticFiles -> {
                staticFiles.hostedPath = "/";
                staticFiles.directory = "/public";
                staticFiles.location = Location.CLASSPATH;
            });

            // Thymeleaf для HTML из templates
            config.fileRenderer(new JavalinThymeleaf(createTemplateEngine()));

            //настройка JSON mapper
            config.jsonMapper(new JavalinJackson(objectMapper, true));
        });

        // API маршруты
        AuthController.setupRoutes(app);
        ChatController.setupRoutes(app);

        // HTML маршруты
        app.get("/", ctx -> {
            ctx.render("login.html");
        });

        app.get("/chat", ctx -> {
            User user = ctx.sessionAttribute("user");
            if (user == null) {
                ctx.redirect("/");
                return;
            }
            Map<String, Object> model = new HashMap<>();
            model.put("user", user);
            model.put("username", user.getUsername());
            ctx.render("chat.html", model);
        });

        app.get("/admin", ctx -> {
            User user = ctx.sessionAttribute("user");
            if (user == null || !"ADMIN".equals(user.getRole())) {
                ctx.redirect("/");
                return;
            }
            Map<String, Object> model = new HashMap<>();
            model.put("user", user);
            model.put("username", user.getUsername());
            model.put("role", user.getRole());
            ctx.render("admin.html", model);
        });

        // CORS
        app.before(ctx -> {
            ctx.header("Access-Control-Allow-Origin", "*");
            ctx.header("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
            ctx.header("Access-Control-Allow-Headers", "Content-Type, Authorization");
            System.out.println(ctx.method() + " " + ctx.path());
        });

        // Запуск сервера
        String host = "0.0.0.0";
        int port = 8080;

        app.start(host, port);
        showNetworkInfo(port);
    }

    private static ObjectMapper createObjectMapper() {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return objectMapper;
    }

    private static TemplateEngine createTemplateEngine() {
        TemplateEngine templateEngine = new TemplateEngine();
        ClassLoaderTemplateResolver templateResolver = new ClassLoaderTemplateResolver();
        templateResolver.setPrefix("/templates/");
        templateResolver.setSuffix(".html");
        templateResolver.setCharacterEncoding("UTF-8");
        templateEngine.setTemplateResolver(templateResolver);
        return templateEngine;
    }

    private static void showNetworkInfo(int port) {
        try {
            System.out.println("🎉 Сервер запущен!");
            System.out.println("📍 Локальный: http://localhost:" + port);

            java.util.Enumeration<java.net.NetworkInterface> interfaces = java.net.NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                java.net.NetworkInterface iface = interfaces.nextElement();
                if (iface.isUp() && !iface.isLoopback()) {
                    java.util.Enumeration<java.net.InetAddress> addresses = iface.getInetAddresses();
                    while (addresses.hasMoreElements()) {
                        java.net.InetAddress addr = addresses.nextElement();
                        if (addr instanceof java.net.Inet4Address) {
                            System.out.println("🌐 Сетевой: http://" + addr.getHostAddress() + ":" + port);
                        }
                    }
                }
            }
        } catch (Exception e) {
            System.out.println("❌ Ошибка сети: " + e.getMessage());
        }
    }
}