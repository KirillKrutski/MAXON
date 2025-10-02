import java.time.LocalDateTime;

public class Message {
    private int id;
    private int chatId;
    private int senderId;
    private String senderName;
    private String content;
    private boolean isDeleted;
    private LocalDateTime createdAt;

    // Конструкторы
    public Message() {
        this.createdAt = LocalDateTime.now();
        this.isDeleted = false;
    }

    public Message(int chatId, int senderId, String content) {
        this();
        this.chatId = chatId;
        this.senderId = senderId;
        this.content = content;
    }

    public Message(int id, int chatId, int senderId, String senderName, String content,
                   boolean isDeleted, LocalDateTime createdAt) {
        this.id = id;
        this.chatId = chatId;
        this.senderId = senderId;
        this.senderName = senderName;
        this.content = content;
        this.isDeleted = isDeleted;
        this.createdAt = createdAt;
    }

    // Геттеры и сеттеры
    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public int getChatId() { return chatId; }
    public void setChatId(int chatId) { this.chatId = chatId; }

    public int getSenderId() { return senderId; }
    public void setSenderId(int senderId) { this.senderId = senderId; }

    public String getSenderName() { return senderName; }
    public void setSenderName(String senderName) { this.senderName = senderName; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public boolean isDeleted() { return isDeleted; }
    public void setDeleted(boolean deleted) { isDeleted = deleted; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    // Бизнес-логика
    public String getDisplayContent() {
        return isDeleted ? "Сообщение удалено" : content;
    }

    public boolean canDelete(int userId) {
        return senderId == userId && !isDeleted;
    }

    public boolean canReport(int userId) {
        return senderId != userId && !isDeleted;
    }

    // Builder
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private int id;
        private int chatId;
        private int senderId;
        private String senderName;
        private String content;
        private boolean isDeleted;
        private LocalDateTime createdAt;

        public Builder id(int id) { this.id = id; return this; }
        public Builder chatId(int chatId) { this.chatId = chatId; return this; }
        public Builder senderId(int senderId) { this.senderId = senderId; return this; }
        public Builder senderName(String senderName) { this.senderName = senderName; return this; }
        public Builder content(String content) { this.content = content; return this; }
        public Builder isDeleted(boolean isDeleted) { this.isDeleted = isDeleted; return this; }
        public Builder createdAt(LocalDateTime createdAt) { this.createdAt = createdAt; return this; }

        public Message build() {
            return new Message(id, chatId, senderId, senderName, content, isDeleted, createdAt);
        }
    }
}