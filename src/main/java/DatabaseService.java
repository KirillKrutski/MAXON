import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.sql.*;
import java.util.*;
import javax.sql.DataSource;
import com.zaxxer.hikari.HikariDataSource;
import com.fasterxml.jackson.databind.ObjectMapper;
import at.favre.lib.crypto.bcrypt.BCrypt;
import io.javalin.http.Context;
import io.javalin.http.UploadedFile;
import org.jetbrains.annotations.NotNull;

public class DatabaseService {
    private static DataSource dataSource;
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final BCrypt.Hasher bcryptHasher = BCrypt.withDefaults();
    private static final BCrypt.Verifyer bcryptVerifyer = BCrypt.verifyer();

    static {
        HikariDataSource ds = new HikariDataSource();
        ds.setJdbcUrl("jdbc:postgresql://localhost:5432/chat_app");
        ds.setUsername("useradmin");
        ds.setPassword("admin");
        ds.setMaximumPoolSize(10);
        dataSource = ds;
    }

    public static Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }

    // Методы для работы с пользователями
    public static User authenticateUser(String username, String password) {
        String sql = "SELECT * FROM users WHERE username = ?";
        try (Connection conn = getConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, username);
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                // Проверяем пароль
                String storedHash = rs.getString("password");
                if (bcryptVerifyer.verify(password.toCharArray(), storedHash.toCharArray()).verified) {
                    User user = mapUser(rs);

                    // Проверяем, не заблокирован ли пользователь
                    if (user.isCurrentlyBlocked()) {
                        System.out.println("🚫 Заблокированный пользователь пытается войти: " + username);

                        // Проверяем, не истекла ли временная блокировка
                        if (user.getBlockedUntil() != null &&
                                user.getBlockedUntil().isBefore(java.time.LocalDateTime.now())) {
                            // Блокировка истекла - разблокируем
                            unblockUser(user.getId());
                            System.out.println("✅ Временная блокировка истекла, пользователь разблокирован");
                            return user;
                        }

                        return null; // Пользователь заблокирован
                    }

                    return user;
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }


    public static boolean registerUser(String username, String password, String email) {
        System.out.println("🔐 Регистрация пользователя: " + username);

        // Сначала проверяем, нет ли уже такого пользователя или email
        String checkSql = "SELECT id FROM users WHERE username = ? OR email = ?";
        String insertSql = "INSERT INTO users (username, password, email) VALUES (?, ?, ?)";

        try (Connection conn = getConnection();
             PreparedStatement checkStmt = conn.prepareStatement(checkSql)) {

            // 1. ПРОВЕРКА СУЩЕСТВОВАНИЯ ПОЛЬЗОВАТЕЛЯ
            System.out.println("🔍 Проверка существования пользователя...");
            checkStmt.setString(1, username);  // Параметр 1 для checkStmt
            checkStmt.setString(2, email);     // Параметр 2 для checkStmt

            try (ResultSet rs = checkStmt.executeQuery()) {
                if (rs.next()) {
                    System.out.println("❌ Пользователь " + username + " или email " + email + " уже существует");
                    return false;
                }
            }

            // 2. ХЭШИРОВАНИЕ ПАРОЛЯ
            System.out.println("🔒 Хэширование пароля...");
            String hashedPassword = bcryptHasher.hashToString(12, password.toCharArray());
            System.out.println("✅ Пароль успешно хэширован");

            // 3. РЕГИСТРАЦИЯ НОВОГО ПОЛЬЗОВАТЕЛЯ
            System.out.println("📝 Регистрация нового пользователя...");
            try (PreparedStatement insertStmt = conn.prepareStatement(insertSql)) {
                insertStmt.setString(1, username);        // Параметр 1 для insertStmt
                insertStmt.setString(2, hashedPassword);  // Параметр 2 для insertStmt
                insertStmt.setString(3, email);           // Параметр 3 для insertStmt

                int affectedRows = insertStmt.executeUpdate();
                System.out.println("✅ Успешно зарегистрирован пользователь: " + username + " (" + email + ")");
                return affectedRows > 0;
            }

        } catch (SQLException e) {
            System.out.println("❌ Ошибка регистрации пользователя " + username + ": " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    public static List<User> searchUsers(String query) {
        List<User> users = new ArrayList<>();
        String sql = "SELECT * FROM users WHERE username ILIKE ? AND role = 'USER'";
        try (Connection conn = getConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, "%" + query + "%");
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                users.add(mapUser(rs));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return users;
    }

    public static User getUserById(int userId) {
        String sql = "SELECT * FROM users WHERE id = ?";
        try (Connection conn = getConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, userId);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return mapUser(rs);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    // Методы для работы с контактами
    public static List<User> getUserContacts(int userId) {
        List<User> contacts = new ArrayList<>();
        String sql = "SELECT u.* FROM users u " +
                "JOIN contacts c ON u.id = c.contact_id " +
                "WHERE c.user_id = ?";
        try (Connection conn = getConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, userId);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                contacts.add(mapUser(rs));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return contacts;
    }

    public static boolean addContact(int userId, int contactId) {
        String sql = "INSERT INTO contacts (user_id, contact_id) VALUES (?, ?)";
        try (Connection conn = getConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, userId);
            stmt.setInt(2, contactId);
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    // Методы для работы с чатами
    public static List<Chat> getUserChats(int userId) {
        List<Chat> chats = new ArrayList<>();
        String sql = "SELECT c.* FROM chats c " +
                "JOIN chat_participants cp ON c.id = cp.chat_id " +
                "WHERE cp.user_id = ? ORDER BY c.created_at DESC";
        try (Connection conn = getConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, userId);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                Chat chat = mapChat(rs);
                chat.setParticipants(getChatParticipants(chat.getId()));
                chat.setLastMessage(getLastMessage(chat.getId()));
                chats.add(chat);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return chats;
    }

    public static int createPrivateChat(int user1Id, int user2Id) {
        // Сначала проверяем, нет ли уже такого чата
        String checkSql = "SELECT c.id FROM chats c " +
                "JOIN chat_participants cp1 ON c.id = cp1.chat_id " +
                "JOIN chat_participants cp2 ON c.id = cp2.chat_id " +
                "WHERE c.is_group = false AND cp1.user_id = ? AND cp2.user_id = ?";

        String insertSql = "INSERT INTO chats (is_group, created_by) VALUES (false, ?) RETURNING id";

        try (Connection conn = getConnection();
             PreparedStatement checkStmt = conn.prepareStatement(checkSql);
             PreparedStatement insertStmt = conn.prepareStatement(insertSql)) {

            // Проверяем существующий чат
            checkStmt.setInt(1, user1Id);
            checkStmt.setInt(2, user2Id);
            ResultSet rs = checkStmt.executeQuery();

            if (rs.next()) {
                return rs.getInt("id"); // Возвращаем существующий чат
            }

            // Создаем новый чат
            insertStmt.setInt(1, user1Id);
            rs = insertStmt.executeQuery();

            if (rs.next()) {
                int chatId = rs.getInt(1);
                // Добавляем участников
                addParticipantToChat(chatId, user1Id);
                addParticipantToChat(chatId, user2Id);
                System.out.println("💬 Создан приватный чат " + chatId + " между " + user1Id + " и " + user2Id);
                return chatId;
            }
        } catch (SQLException e) {
            System.out.println("❌ Ошибка создания чата: " + e.getMessage());
            e.printStackTrace();
        }
        return -1;
    }

    public static int createGroupChat(String name, int createdBy, List<Integer> participantIds) {
        String sql = "INSERT INTO chats (name, is_group, created_by) VALUES (?, true, ?) RETURNING id";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, name);
            stmt.setInt(2, createdBy);
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                int chatId = rs.getInt(1);
                // Добавляем создателя
                addParticipantToChat(chatId, createdBy);
                // Добавляем участников
                for (int participantId : participantIds) {
                    addParticipantToChat(chatId, participantId);
                }

                System.out.println("👥 Создана группа: " + name + " (ID: " + chatId +
                        "), участников: " + (participantIds.size() + 1));
                return chatId;
            }
        } catch (SQLException e) {
            System.out.println("❌ Ошибка создания группового чата: " + e.getMessage());
            e.printStackTrace();
        }
        return -1;
    }

    private static void addParticipantToChat(int chatId, int userId) {
        String sql = "INSERT INTO chat_participants (chat_id, user_id) VALUES (?, ?)";
        try (Connection conn = getConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, chatId);
            stmt.setInt(2, userId);
            stmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private static List<User> getChatParticipants(int chatId) {
        List<User> participants = new ArrayList<>();
        String sql = "SELECT u.* FROM users u " +
                "JOIN chat_participants cp ON u.id = cp.user_id " +
                "WHERE cp.chat_id = ?";
        try (Connection conn = getConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, chatId);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                participants.add(mapUser(rs));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return participants;
    }

    // Методы для работы с сообщениями
    public static boolean sendMessage(int chatId, int senderId, String content) {
        String sql = "INSERT INTO messages (chat_id, sender_id, content) VALUES (?, ?, ?)";
        try (Connection conn = getConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, chatId);
            stmt.setInt(2, senderId);
            stmt.setString(3, content);

            boolean success = stmt.executeUpdate() > 0;
            if (success) {
                System.out.println("💬 Сообщение отправлено в чат " + chatId + " от пользователя " + senderId + ": " + content);
            } else {
                System.out.println("❌ Не удалось отправить сообщение");
            }
            return success;
        } catch (SQLException e) {
            System.out.println("❌ Ошибка отправки сообщения: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    public static boolean sendMessageWithFile(int chatId, int senderId, String content,
                                              String fileName, String fileType,
                                              String fileUrl, long fileSize) {
        String sql = "INSERT INTO messages (chat_id, sender_id, content, has_file, file_name, file_type, file_url, file_size) " +
                "VALUES (?, ?, ?, true, ?, ?, ?, ?)";

        try (Connection conn = getConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, chatId);
            stmt.setInt(2, senderId);
            stmt.setString(3, content);
            stmt.setString(4, fileName);
            stmt.setString(5, fileType);
            stmt.setString(6, fileUrl);
            stmt.setLong(7, fileSize);

            boolean success = stmt.executeUpdate() > 0;
            if (success) {
                System.out.println("📎 Сообщение с файлом отправлено в чат " + chatId);
            }
            return success;
        } catch (SQLException e) {
            System.out.println("❌ Ошибка отправки сообщения с файлом: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    // Метод для получения сообщений чата
    public static List<Message> getChatMessages(int chatId) {
        List<Message> messages = new ArrayList<>();
        String sql = "SELECT m.*, u.username as sender_name, u.profile_image as sender_avatar " +
                "FROM messages m " +
                "JOIN users u ON m.sender_id = u.id " +
                "WHERE m.chat_id = ? AND m.is_deleted = false " +
                "ORDER BY m.created_at ASC";

        try (Connection conn = getConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, chatId);
            ResultSet rs = stmt.executeQuery();

            while (rs.next()) {
                Message message = mapMessage(rs);
                message.setSenderName(rs.getString("sender_name"));

                // Устанавливаем информацию об отправителе через senderName
                // (метод setSender не существует в классе Message)
                messages.add(message);
            }
        } catch (SQLException e) {
            System.out.println("❌ Ошибка получения сообщений: " + e.getMessage());
            e.printStackTrace();
        }
        return messages;
    }



    private static Message getLastMessage(int chatId) {
        String sql = "SELECT m.*, u.username as sender_name FROM messages m " +
                "JOIN users u ON m.sender_id = u.id " +
                "WHERE m.chat_id = ? AND m.is_deleted = false " +
                "ORDER BY m.created_at DESC LIMIT 1";
        try (Connection conn = getConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, chatId);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return mapMessage(rs);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    public static boolean deleteMessage(int messageId) {
        String sql = "UPDATE messages SET is_deleted = true WHERE id = ?";
        try (Connection conn = getConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, messageId);
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    public static Message getMessageById(int messageId) {
        String sql = "SELECT m.*, u.username as sender_name FROM messages m " +
                "JOIN users u ON m.sender_id = u.id " +
                "WHERE m.id = ?";
        try (Connection conn = getConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, messageId);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return mapMessage(rs);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    // Методы для работы с жалобами (REPORTS)
    public static boolean createReport(int messageId, int reporterId, String reason) {
        String sql = "INSERT INTO reports (message_id, reporter_id, reason) VALUES (?, ?, ?)";
        try (Connection conn = getConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, messageId);
            stmt.setInt(2, reporterId);
            stmt.setString(3, reason);
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    // МЕТОД, КОТОРЫЙ ОТСУТСТВОВАЛ - получение ожидающих жалоб
    public static List<Report> getPendingReports() {
        List<Report> reports = new ArrayList<>();
        String sql = "SELECT r.*, u1.username as reporter_name, u2.username as reported_user_name, " +
                "m.content as message_content, m.sender_id as message_sender_id " +
                "FROM reports r " +
                "JOIN users u1 ON r.reporter_id = u1.id " +
                "JOIN messages m ON r.message_id = m.id " +
                "JOIN users u2 ON m.sender_id = u2.id " +
                "WHERE r.status = 'PENDING' " +
                "ORDER BY r.created_at DESC";
        try (Connection conn = getConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                Report report = mapReport(rs);

                // Создаем объект сообщения
                Message message = new Message();
                message.setId(rs.getInt("message_id"));
                message.setContent(rs.getString("message_content"));
                message.setSenderId(rs.getInt("message_sender_id"));
                message.setSenderName(rs.getString("reported_user_name"));
                report.setMessage(message);

                // Создаем объект пользователя
                User reportedUser = new User();
                reportedUser.setId(rs.getInt("message_sender_id"));
                reportedUser.setUsername(rs.getString("reported_user_name"));
                report.setReportedUser(reportedUser);

                reports.add(report);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return reports;
    }

    // МЕТОД, КОТОРЫЙ ОТСУТСТВОВАЛ - обработка жалобы
    public static boolean processReport(int reportId, String decision, int days, int adminId) {
        Connection conn = null;
        try {
            conn = getConnection();
            conn.setAutoCommit(false);

            // Обновляем статус жалобы
            String updateReportSql = "UPDATE reports SET status = ?, admin_decision = ? WHERE id = ?";
            PreparedStatement updateReportStmt = conn.prepareStatement(updateReportSql);

            String status = "APPROVED";
            String adminDecision = "Жалоба одобрена";

            if ("dismiss".equals(decision)) {
                status = "REJECTED";
                adminDecision = "Жалоба отклонена";
            }

            updateReportStmt.setString(1, status);
            updateReportStmt.setString(2, adminDecision);
            updateReportStmt.setInt(3, reportId);
            updateReportStmt.executeUpdate();

            // Если жалоба одобрена и требуется блокировка
            if ("APPROVED".equals(status) && !"dismiss".equals(decision)) {
                // Получаем ID пользователя из жалобы
                String getUserIdSql = "SELECT m.sender_id FROM reports r " +
                        "JOIN messages m ON r.message_id = m.id " +
                        "WHERE r.id = ?";
                PreparedStatement getUserIdStmt = conn.prepareStatement(getUserIdSql);
                getUserIdStmt.setInt(1, reportId);
                ResultSet rs = getUserIdStmt.executeQuery();

                if (rs.next()) {
                    int userId = rs.getInt("sender_id");

                    // Блокируем пользователя
                    String blockUserSql;
                    if ("block_permanent".equals(decision)) {
                        blockUserSql = "UPDATE users SET is_blocked = true, blocked_until = NULL WHERE id = ?";
                    } else {
                        blockUserSql = "UPDATE users SET is_blocked = true, blocked_until = NOW() + INTERVAL '? days' WHERE id = ?";
                    }

                    PreparedStatement blockUserStmt = conn.prepareStatement(blockUserSql);
                    if ("block_permanent".equals(decision)) {
                        blockUserStmt.setInt(1, userId);
                    } else {
                        blockUserStmt.setInt(1, days);
                        blockUserStmt.setInt(2, userId);
                    }
                    blockUserStmt.executeUpdate();

                    // Обновляем решение администратора
                    String finalDecision = "block_permanent".equals(decision) ?
                            "Пользователь заблокирован навсегда" :
                            "Пользователь заблокирован на " + days + " дней";

                    PreparedStatement updateDecisionStmt = conn.prepareStatement(
                            "UPDATE reports SET admin_decision = ? WHERE id = ?");
                    updateDecisionStmt.setString(1, finalDecision);
                    updateDecisionStmt.setInt(2, reportId);
                    updateDecisionStmt.executeUpdate();
                }
            }

            conn.commit();
            return true;

        } catch (SQLException e) {
            e.printStackTrace();
            if (conn != null) {
                try {
                    conn.rollback();
                } catch (SQLException ex) {
                    ex.printStackTrace();
                }
            }
            return false;
        } finally {
            if (conn != null) {
                try {
                    conn.setAutoCommit(true);
                    conn.close();
                } catch (SQLException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    // Методы для администратора
    public static List<User> getAllUsers() {
        List<User> users = new ArrayList<>();
        String sql = "SELECT * FROM users WHERE role = 'USER' ORDER BY username";

        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                User user = mapUser(rs);
                users.add(user);
            }

            System.out.println("✅ [DatabaseService] getAllUsers() вернул " + users.size() + " пользователей");

        } catch (SQLException e) {
            System.out.println("❌ Ошибка получения пользователей: " + e.getMessage());
            e.printStackTrace();
        }

        return users;
    }

    public static boolean unblockUser(int userId) {
        String sql = "UPDATE users SET is_blocked = false, blocked_until = NULL WHERE id = ?";
        try (Connection conn = getConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, userId);
            boolean success = stmt.executeUpdate() > 0;

            if (success) {
                System.out.println("✅ Пользователь ID " + userId + " разблокирован");
            }

            return success;
        } catch (SQLException e) {
            System.out.println("❌ Ошибка разблокировки пользователя: " + e.getMessage());
            return false;
        }
    }

    // Вспомогательные методы маппинга
    private static User mapUser(ResultSet rs) throws SQLException {
        User user = new User();
        user.setId(rs.getInt("id"));
        user.setUsername(rs.getString("username"));
        user.setPassword(rs.getString("password"));
        user.setEmail(rs.getString("email"));
        user.setRole(rs.getString("role"));
        user.setProfileImage(rs.getString("profile_image"));
        user.setOnline(rs.getBoolean("is_online"));
        user.setBlocked(rs.getBoolean("is_blocked"));
        user.setOnline(rs.getBoolean("is_online"));


        Timestamp lastSeen = rs.getTimestamp("last_seen");
        if (lastSeen != null) {
            user.setLastSeen(lastSeen.toLocalDateTime());
        }

        user.setOnline(rs.getBoolean("is_online"));

        Timestamp blockedUntil = rs.getTimestamp("blocked_until");
        if (blockedUntil != null) {
            user.setBlockedUntil(blockedUntil.toLocalDateTime());
        }

        Timestamp createdAt = rs.getTimestamp("created_at");
        if (createdAt != null) {
            user.setCreatedAt(createdAt.toLocalDateTime());
        }

        return user;
    }

    private static Chat mapChat(ResultSet rs) throws SQLException {
        Chat chat = new Chat();
        chat.setId(rs.getInt("id"));
        chat.setName(rs.getString("name"));
        chat.setGroup(rs.getBoolean("is_group"));
        chat.setCreatedBy(rs.getInt("created_by"));

        Timestamp createdAt = rs.getTimestamp("created_at");
        if (createdAt != null) {
            chat.setCreatedAt(createdAt.toLocalDateTime());
        }

        return chat;
    }

    private static Message mapMessage(ResultSet rs) throws SQLException {
        Message message = new Message();
        message.setId(rs.getInt("id"));
        message.setChatId(rs.getInt("chat_id"));
        message.setSenderId(rs.getInt("sender_id"));
        message.setSenderName(rs.getString("sender_name"));
        message.setContent(rs.getString("content"));
        message.setDeleted(rs.getBoolean("is_deleted"));

        // Добавить поля файла
        message.setHasFile(rs.getBoolean("has_file"));
        if (message.isHasFile()) {
            message.setFileName(rs.getString("file_name"));
            message.setFileType(rs.getString("file_type"));
            message.setFileUrl(rs.getString("file_url"));
            message.setFileSize(rs.getLong("file_size"));
        }

        Timestamp createdAt = rs.getTimestamp("created_at");
        if (createdAt != null) {
            message.setCreatedAt(createdAt.toLocalDateTime());
        }

        return message;
    }

    private static Report mapReport(ResultSet rs) throws SQLException {
        Report report = new Report();
        report.setId(rs.getInt("id"));
        report.setMessageId(rs.getInt("message_id"));
        report.setReporterId(rs.getInt("reporter_id"));
        report.setReporterName(rs.getString("reporter_name"));
        report.setReason(rs.getString("reason"));
        report.setStatus(rs.getString("status"));
        report.setAdminDecision(rs.getString("admin_decision"));

        Timestamp createdAt = rs.getTimestamp("created_at");
        if (createdAt != null) {
            report.setCreatedAt(createdAt.toLocalDateTime());
        }

        return report;
    }

    public static List<User> searchUsers(String query, int currentUserId) {
        List<User> users = new ArrayList<>();
        String sql = "SELECT * FROM users WHERE username ILIKE ? AND id != ? AND role = 'USER'";
        try (Connection conn = getConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, "%" + query + "%");
            stmt.setInt(2, currentUserId);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                users.add(mapUser(rs));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return users;
    }

    // Проверка, является ли пользователь уже контактом
    public static boolean isContact(int userId, int contactId) {
        String sql = "SELECT 1 FROM contacts WHERE user_id = ? AND contact_id = ?";
        try (Connection conn = getConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, userId);
            stmt.setInt(2, contactId);
            ResultSet rs = stmt.executeQuery();
            return rs.next();
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    // Проверка, есть ли уже запрос
    public static boolean hasFriendRequest(int fromUserId, int toUserId) {
        String sql = "SELECT 1 FROM friend_requests WHERE from_user_id = ? AND to_user_id = ? AND status = 'PENDING'";
        try (Connection conn = getConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, fromUserId);
            stmt.setInt(2, toUserId);
            ResultSet rs = stmt.executeQuery();
            return rs.next();
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    // Отправка запроса в друзья
    public static boolean sendFriendRequest(int fromUserId, int toUserId) {
        String sql = "INSERT INTO friend_requests (from_user_id, to_user_id, status) VALUES (?, ?, 'PENDING')";
        try (Connection conn = getConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, fromUserId);
            stmt.setInt(2, toUserId);
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    // Получение входящих запросов
    public static List<FriendRequest> getIncomingRequests(int userId) {
        List<FriendRequest> requests = new ArrayList<>();
        String sql = "SELECT fr.*, u.username as from_username FROM friend_requests fr " +
                "JOIN users u ON fr.from_user_id = u.id " +
                "WHERE fr.to_user_id = ? AND fr.status = 'PENDING'";
        try (Connection conn = getConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, userId);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                requests.add(mapFriendRequest(rs));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return requests;
    }

    // Принятие запроса в друзья
    public static boolean acceptFriendRequest(int requestId, int userId) {
        Connection conn = null;
        try {
            conn = getConnection();
            conn.setAutoCommit(false);

            // Получаем информацию о запросе
            String getRequestSql = "SELECT * FROM friend_requests WHERE id = ? AND to_user_id = ? AND status = 'PENDING'";
            PreparedStatement getStmt = conn.prepareStatement(getRequestSql);
            getStmt.setInt(1, requestId);
            getStmt.setInt(2, userId);
            ResultSet rs = getStmt.executeQuery();

            if (rs.next()) {
                int fromUserId = rs.getInt("from_user_id");
                System.out.println("✅ Принятие запроса: " + requestId + ", от пользователя: " + fromUserId + ", к пользователю: " + userId);

                // Обновляем статус запроса
                String updateSql = "UPDATE friend_requests SET status = 'ACCEPTED' WHERE id = ?";
                PreparedStatement updateStmt = conn.prepareStatement(updateSql);
                updateStmt.setInt(1, requestId);
                updateStmt.executeUpdate();

                // Добавляем в контакты в обе стороны
                addContact(conn, userId, fromUserId); // Текущий пользователь добавляет отправителя
                addContact(conn, fromUserId, userId); // Отправитель добавляет текущего пользователя

                conn.commit();
                System.out.println("✅ Запрос принят, контакты созданы");
                return true;
            } else {
                System.out.println("❌ Запрос не найден или уже обработан");
                conn.rollback();
                return false;
            }

        } catch (SQLException e) {
            System.out.println("❌ Ошибка принятия запроса: " + e.getMessage());
            if (conn != null) {
                try {
                    conn.rollback();
                } catch (SQLException ex) {
                    ex.printStackTrace();
                }
            }
            e.printStackTrace();
            return false;
        } finally {
            if (conn != null) {
                try {
                    conn.setAutoCommit(true);
                    conn.close();
                } catch (SQLException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    public static void updateUserOnlineStatus(int userId, boolean isOnline) {
        String sql = "UPDATE users SET is_online = ?, last_seen = CURRENT_TIMESTAMP WHERE id = ?";
        try (Connection conn = getConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setBoolean(1, isOnline);
            stmt.setInt(2, userId);
            stmt.executeUpdate();
        } catch (SQLException e) {
            System.out.println("❌ Ошибка обновления статуса: " + e.getMessage());
        }
    }

    // Метод для получения расширения файла (уже должен быть, но на всякий случай)
    public static String getFileExtension(String filename) {
        if (filename == null || filename.isEmpty()) {
            return "";
        }
        int lastDot = filename.lastIndexOf(".");
        return lastDot > 0 ? filename.substring(lastDot) : "";
    }


    public static void serveFile(@NotNull Context ctx, String filename) {
        try {
            java.nio.file.Path filePath = Paths.get("uploads/" + filename);
            if (Files.exists(filePath)) {
                byte[] fileBytes = Files.readAllBytes(filePath);

                // Определяем Content-Type по расширению файла
                String contentType = "application/octet-stream";
                if (filename.toLowerCase().endsWith(".jpg") || filename.toLowerCase().endsWith(".jpeg")) {
                    contentType = "image/jpeg";
                } else if (filename.toLowerCase().endsWith(".png")) {
                    contentType = "image/png";
                }

                ctx.contentType(contentType);
                ctx.result(fileBytes);
            } else {
                ctx.status(404).result("File not found");
            }
        } catch (Exception e) {
            System.out.println("❌ Ошибка отдачи файла: " + e.getMessage());
            ctx.status(500).result("Error serving file");
        }
    }

    private static void addContact(Connection conn, int userId, int contactId) throws SQLException {
        String sql = "INSERT INTO contacts (user_id, contact_id) VALUES (?, ?) ON CONFLICT (user_id, contact_id) DO NOTHING";
        PreparedStatement stmt = conn.prepareStatement(sql);
        stmt.setInt(1, userId);
        stmt.setInt(2, contactId);
        int affected = stmt.executeUpdate();
        if (affected > 0) {
            System.out.println("✅ Добавлен контакт: " + userId + " → " + contactId);
        }
    }

    private static FriendRequest mapFriendRequest(ResultSet rs) throws SQLException {
        FriendRequest request = new FriendRequest();
        request.setId(rs.getInt("id"));
        request.setFromUserId(rs.getInt("from_user_id"));
        request.setToUserId(rs.getInt("to_user_id"));
        request.setFromUsername(rs.getString("from_username"));
        request.setStatus(rs.getString("status"));
        request.setCreatedAt(rs.getTimestamp("created_at").toLocalDateTime());
        return request;
    }

    // Добавьте эти методы в класс DatabaseService

    public static User getUserByUsername(String username) {
        String sql = "SELECT * FROM users WHERE username = ?";
        try (Connection conn = getConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, username);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return mapUser(rs);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    public static boolean updateUserPassword(int userId, String newPassword) {
        String sql = "UPDATE users SET password = ? WHERE id = ?";
        try (Connection conn = getConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {
            // ХЕШИРУЕМ НОВЫЙ ПАРОЛЬ
            String hashedPassword = bcryptHasher.hashToString(12, newPassword.toCharArray());

            stmt.setString(1, hashedPassword);
            stmt.setInt(2, userId);
            boolean success = stmt.executeUpdate() > 0;

            if (success) {
                System.out.println("✅ Пароль обновлен для пользователя ID: " + userId);
            } else {
                System.out.println("❌ Не удалось обновить пароль для пользователя ID: " + userId);
            }

            return success;
        } catch (SQLException e) {
            System.out.println("❌ Ошибка обновления пароля: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    public static boolean updateUserAvatar(int userId, UploadedFile file) {
        String originalFilename = file.filename();
        String fileExtension = getFileExtension(originalFilename);
        String filename = "avatar_" + userId + "_" + System.currentTimeMillis() + fileExtension;
        String uploadDir = "uploads/";

        try {
            // Создаем директорию если нет
            Files.createDirectories(Paths.get(uploadDir));

            // Сохраняем файл
            java.nio.file.Path filePath = Paths.get(uploadDir + filename);
            Files.copy(file.content(), filePath, StandardCopyOption.REPLACE_EXISTING);

            System.out.println("✅ Файл сохранен: " + filename);

            // Обновляем в БД
            String sql = "UPDATE users SET profile_image = ? WHERE id = ?";
            try (Connection conn = getConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, filename);
                stmt.setInt(2, userId);
                boolean success = stmt.executeUpdate() > 0;

                if (success) {
                    System.out.println("✅ Аватар обновлен в БД для пользователя: " + userId);
                }
                return success;
            }
        } catch (Exception e) {
            System.out.println("❌ Ошибка загрузки аватарки: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }
    // Метод для получения всех пользователей для админ-панели
    public static List<User> getAllUsersForAdmin() {
        System.out.println("🟡 [DatabaseService] getAllUsersForAdmin() вызван");

        List<User> users = new ArrayList<>();
        String sql = "SELECT * FROM users WHERE role = 'USER' ORDER BY username";

        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            ResultSet rs = stmt.executeQuery();
            int count = 0;

            while (rs.next()) {
                count++;
                User user = mapUser(rs); // Используйте существующий метод mapUser

                System.out.println("   Найден пользователь " + count + ": " +
                        user.getUsername() + " (ID: " + user.getId() + ")");

                users.add(user);
            }

            System.out.println("✅ Всего найдено: " + count + " пользователей");

        } catch (SQLException e) {
            System.out.println("❌ Ошибка SQL: " + e.getMessage());
            e.printStackTrace();
        }

        return users;
    }

    // Метод для блокировки пользователя
    public static boolean blockUser(int userId, String type, String reason, String daysParam, int adminId) {
        Connection conn = null;
        try {
            conn = getConnection();
            conn.setAutoCommit(false);

            System.out.println("🛠 Блокировка пользователя ID: " + userId +
                    ", тип: " + type +
                    ", причина: " + reason);

            String sql;
            int days = 0;

            if ("permanent".equals(type)) {
                sql = "UPDATE users SET is_blocked = true, blocked_until = NULL WHERE id = ?";
            } else {
                days = (daysParam != null && !daysParam.isEmpty()) ? Integer.parseInt(daysParam) : 1;
                sql = "UPDATE users SET is_blocked = true, blocked_until = CURRENT_TIMESTAMP + INTERVAL '" + days + " days' WHERE id = ?";
                System.out.println("📅 Блокировка на " + days + " дней");
            }

            PreparedStatement stmt = conn.prepareStatement(sql);
            stmt.setInt(1, userId);
            int affectedRows = stmt.executeUpdate();

            boolean success = affectedRows > 0;

            if (success) {
                // Логируем действие
                String details = "permanent".equals(type) ?
                        "Постоянная блокировка" :
                        "Временная блокировка на " + days + " дней";

                logAdminAction(adminId, userId, "BLOCK", reason, details);

                System.out.println("✅ Пользователь ID " + userId + " успешно заблокирован");
            } else {
                System.out.println("❌ Пользователь ID " + userId + " не найден");
            }

            conn.commit();
            return success;

        } catch (SQLException e) {
            System.out.println("❌ SQL ошибка блокировки: " + e.getMessage());
            e.printStackTrace();
            if (conn != null) {
                try {
                    conn.rollback();
                } catch (SQLException ex) {
                    System.out.println("❌ Ошибка отката: " + ex.getMessage());
                }
            }
            return false;
        } catch (NumberFormatException e) {
            System.out.println("❌ Ошибка парсинга дней: " + e.getMessage());
            return false;
        } finally {
            if (conn != null) {
                try {
                    conn.setAutoCommit(true);
                    conn.close();
                } catch (SQLException e) {
                    System.out.println("❌ Ошибка закрытия соединения: " + e.getMessage());
                }
            }
        }
    }



    // Логирование действий администратора
    private static void logAdminAction(int adminId, int targetUserId, String action, String reason, String details) {
        String sql = "INSERT INTO admin_logs (admin_id, target_user_id, action, reason, details, created_at) " +
                "VALUES (?, ?, ?, ?, ?, CURRENT_TIMESTAMP)";

        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, adminId);
            stmt.setInt(2, targetUserId);
            stmt.setString(3, action);
            stmt.setString(4, reason);
            stmt.setString(5, details);

            stmt.executeUpdate();

            System.out.println("📝 Логирование: " + action + " пользователя " + targetUserId);

        } catch (SQLException e) {
            System.out.println("❌ Ошибка логирования: " + e.getMessage());
        }
    }

    // Инвалидация сессий пользователя (симуляция)
    private static void invalidateUserSessions(int userId) {
        System.out.println("🚫 Инвалидация сессий для пользователя ID: " + userId);
        // В реальном приложении здесь можно:
        // 1. Удалить сессию из хранилища
        // 2. Отправить WebSocket сообщение о блокировке
        // 3. Записать в лог для последующей очистки
    }



}