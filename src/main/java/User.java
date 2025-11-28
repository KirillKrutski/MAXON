import java.time.LocalDateTime;

public class User {
    private int id;
    private String username;
    private String password;
    private String role;
    private boolean isBlocked;
    private LocalDateTime blockedUntil;
    private LocalDateTime createdAt;
    private String email;
    private String profileImage;
    private boolean isOnline;
    private LocalDateTime lastSeen;

    // Конструкторы
    public User() {
        this.role = "USER";
        this.isBlocked = false;
        this.createdAt = LocalDateTime.now();
    }

    public User(int id, String username, String password, String email, String role, boolean isBlocked,
                LocalDateTime blockedUntil, LocalDateTime createdAt) {
        this.id = id;
        this.username = username;
        this.password = password;
        this.email = email;
        this.role = role != null ? role : "USER";
        this.isBlocked = isBlocked;
        this.blockedUntil = blockedUntil;
        this.createdAt = createdAt != null ? createdAt : LocalDateTime.now();
    }

    // Геттеры и сеттеры
    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getEmail() {
        return email;
    }
    public void setEmail(String email) {
        this.email = email;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public boolean isBlocked() {
        return isBlocked;
    }

    public void setBlocked(boolean blocked) {
        isBlocked = blocked;
    }

    public LocalDateTime getBlockedUntil() {
        return blockedUntil;
    }

    public void setBlockedUntil(LocalDateTime blockedUntil) {
        this.blockedUntil = blockedUntil;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public void setProfileImage(String profileImage) { this.profileImage = profileImage; }

    public String getProfileImage() { return profileImage; }

    public void setOnline(boolean online) { isOnline = online; }

    public boolean getIsOnline() { return isOnline; }

    public void setLastSeen(LocalDateTime lastSeen) { this.lastSeen = lastSeen; }

    public LocalDateTime getLastSeen() { return lastSeen; }

    // Вспомогательные методы
    public boolean isAdmin() {
        return "ADMIN".equals(role);
    }

    public boolean isCurrentlyBlocked() {
        if (!isBlocked) {
            return false;
        }
        if (blockedUntil == null) {
            return true; // перманентная блокировка
        }
        return LocalDateTime.now().isBefore(blockedUntil);
    }

    public boolean canSendMessages() {
        return !isCurrentlyBlocked();
    }

    // Методы для блокировки
    public void blockPermanently() {
        this.isBlocked = true;
        this.blockedUntil = null;
    }

    public void blockTemporarily(int days) {
        this.isBlocked = true;
        this.blockedUntil = LocalDateTime.now().plusDays(days);
    }

    public void unblock() {
        this.isBlocked = false;
        this.blockedUntil = null;
    }

    // equals и hashCode
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        User user = (User) o;

        return id == user.id;
    }

    @Override
    public int hashCode() {
        return id;
    }

    // toString
    @Override
    public String toString() {
        return "User{" +
                "id=" + id +
                ", username='" + username + '\'' +
                ", role='" + role + '\'' +
                ", isBlocked=" + isBlocked +
                ", blockedUntil=" + blockedUntil +
                ", createdAt=" + createdAt +
                '}';
    }

    // Builder pattern для удобного создания объектов
    public static class Builder {
        private int id;
        private String username;
        private String password;
        private String role = "USER";
        private String email;
        private boolean isBlocked = false;
        private LocalDateTime blockedUntil;
        private LocalDateTime createdAt = LocalDateTime.now();

        public Builder id(int id) {
            this.id = id;
            return this;
        }

        public Builder username(String username) {
            this.username = username;
            return this;
        }

        public Builder password(String password) {
            this.password = password;
            return this;
        }

        public Builder email(String email) {
            this.email = email;
            return this;
        }

        public Builder role(String role) {
            this.role = role;
            return this;
        }

        public Builder isBlocked(boolean isBlocked) {
            this.isBlocked = isBlocked;
            return this;
        }

        public Builder blockedUntil(LocalDateTime blockedUntil) {
            this.blockedUntil = blockedUntil;
            return this;
        }

        public Builder createdAt(LocalDateTime createdAt) {
            this.createdAt = createdAt;
            return this;
        }

        public User build() {
            return new User(id, username, password, email, role, isBlocked, blockedUntil, createdAt);
        }
    }

    // Статический метод для создания builder
    public static Builder builder() {
        return new Builder();
    }

    // Метод для создания копии пользователя (без пароля)
    public User copyWithoutPassword() {
        return new User(
                this.id,
                this.username,
                null, // не копируем пароль
                this.email,
                this.role,
                this.isBlocked,
                this.blockedUntil,
                this.createdAt
        );
    }

    // Валидация пользователя
    public boolean isValid() {
        return username != null && !username.trim().isEmpty() &&
                password != null && !password.trim().isEmpty() &&
                email != null && !email.trim().isEmpty() &&
                role != null && (role.equals("USER") || role.equals("ADMIN"));
    }

    // Проверка минимальных требований к паролю
    public boolean hasValidPassword() {
        return password != null && password.length() >= 3;
    }

    // Метод для получения статуса онлайн
    public String getStatus() {
        if (isOnline) {
            return "🟢 В сети";
        } else if (lastSeen != null) {
            long minutesAgo = java.time.Duration.between(lastSeen, LocalDateTime.now()).toMinutes();
            if (minutesAgo < 5) return "🟡 Был(а) только что";
            else if (minutesAgo < 60) return "⚫ Был(а) " + minutesAgo + " мин назад";
            else return "⚫ Был(а) " + (minutesAgo / 60) + " ч назад";
        }
        return "⚫ Не в сети";
    }

    public String getProfileImageUrl() {
        if (profileImage != null && !profileImage.isEmpty()) {
            return "/api/files/" + profileImage;
        }
        return "/images/default-avatar.png"; // Дефолтная аватарка
    }
}