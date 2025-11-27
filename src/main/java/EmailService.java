import jakarta.mail.*;
import jakarta.mail.internet.*;
import java.util.Properties;
import java.util.Random;

public class EmailService {
    // ЗАМЕНИТЕ НА ВАШИ РЕАЛЬНЫЕ ДАННЫЕ
    private static final String SMTP_HOST = "smtp.gmail.com";
    private static final String SMTP_PORT = "587";
    private static final String EMAIL_USERNAME = "kirillevgrafov2003@gmail.com"; // Ваш реальный Gmail
    private static final String EMAIL_PASSWORD = "pqaf hmse xjzv nmlp"; // Ваш реальный пароль приложения

    private static final Random random = new Random();

    public static String generateTempPassword() {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
        StringBuilder password = new StringBuilder();
        for (int i = 0; i < 10; i++) {
            password.append(chars.charAt(random.nextInt(chars.length())));
        }
        return password.toString();
    }

    public static boolean sendPasswordResetEmail(String toEmail, String tempPassword) {
        try {
            Properties props = new Properties();
            props.put("mail.smtp.auth", "true");
            props.put("mail.smtp.starttls.enable", "true");
            props.put("mail.smtp.host", SMTP_HOST);
            props.put("mail.smtp.port", SMTP_PORT);
            props.put("mail.smtp.ssl.trust", SMTP_HOST);
            // Добавляем дополнительные настройки
            props.put("mail.smtp.ssl.protocols", "TLSv1.2");
            props.put("mail.smtp.starttls.required", "true");

            Session session = Session.getInstance(props, new Authenticator() {
                protected PasswordAuthentication getPasswordAuthentication() {
                    return new PasswordAuthentication(EMAIL_USERNAME, EMAIL_PASSWORD);
                }
            });

            // Включаем отладку для диагностики
            session.setDebug(true);

            MimeMessage message = new MimeMessage(session);
            message.setFrom(new InternetAddress(EMAIL_USERNAME));
            message.setRecipients(jakarta.mail.Message.RecipientType.TO, InternetAddress.parse(toEmail));
            message.setSubject("Восстановление пароля - FLUPPY");
            message.setSentDate(new java.util.Date());

            String emailContent = "<h3>Восстановление пароля</h3>" +
                    "<p>Вы запросили восстановление пароля для вашего аккаунта в Chat Application.</p>" +
                    "<p><strong>Временный пароль для входа:</strong> " + tempPassword + "</p>" +
                    "<p>После входа в систему рекомендуется сменить пароль в настройках профиля.</p>" +
                    "<br><p><em>Если вы не запрашивали восстановление пароля, проигнорируйте это письмо.</em></p>";

            message.setContent(emailContent, "text/html; charset=utf-8");

            System.out.println("🔄 Попытка отправки email на: " + toEmail);
            System.out.println("📧 Отправитель: " + EMAIL_USERNAME);

            Transport.send(message);
            System.out.println("✅ Письмо с временным паролем отправлено на: " + toEmail);
            return true;

        } catch (Exception e) {
            System.out.println("❌ Ошибка отправки email: " + e.getMessage());
            System.out.println("⚠️ Для тестирования временный пароль: " + tempPassword);
            e.printStackTrace();
            return false;
        }
    }
}