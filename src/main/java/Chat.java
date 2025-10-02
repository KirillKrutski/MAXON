import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class Chat {
    private int id;
    private String name;
    private boolean isGroup;
    private int createdBy;
    private String createdByName;
    private List<User> participants;
    private LocalDateTime createdAt;
    private Message lastMessage;

    // Конструкторы
    public Chat() {
        this.participants = new ArrayList<>();
        this.createdAt = LocalDateTime.now();
        this.isGroup = false;
    }

    public Chat(String name, boolean isGroup, int createdBy) {
        this();
        this.name = name;
        this.isGroup = isGroup;
        this.createdBy = createdBy;
    }

    // Геттеры и сеттеры
    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public boolean isGroup() { return isGroup; }
    public void setGroup(boolean group) { isGroup = group; }

    public int getCreatedBy() { return createdBy; }
    public void setCreatedBy(int createdBy) { this.createdBy = createdBy; }

    public String getCreatedByName() { return createdByName; }
    public void setCreatedByName(String createdByName) { this.createdByName = createdByName; }

    public List<User> getParticipants() { return participants; }
    public void setParticipants(List<User> participants) { this.participants = participants; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public Message getLastMessage() { return lastMessage; }
    public void setLastMessage(Message lastMessage) { this.lastMessage = lastMessage; }

    // Бизнес-логика
    public void addParticipant(User user) {
        if (participants.stream().noneMatch(p -> p.getId() == user.getId())) {
            participants.add(user);
        }
    }

    public void removeParticipant(int userId) {
        participants.removeIf(p -> p.getId() == userId);
    }

    public boolean hasParticipant(int userId) {
        return participants.stream().anyMatch(p -> p.getId() == userId);
    }

    public String getDisplayName(int currentUserId) {
        if (!isGroup && participants.size() == 2) {
            return participants.stream()
                    .filter(p -> p.getId() != currentUserId)
                    .findFirst()
                    .map(User::getUsername)
                    .orElse(name);
        }
        return name != null ? name : "Групповой чат";
    }

    public String getParticipantsNames(int currentUserId) {
        if (isGroup) {
            return participants.stream()
                    .map(User::getUsername)
                    .reduce((a, b) -> a + ", " + b)
                    .orElse("Нет участников");
        } else {
            return participants.stream()
                    .filter(p -> p.getId() != currentUserId)
                    .findFirst()
                    .map(User::getUsername)
                    .orElse("Неизвестный пользователь");
        }
    }

    // Builder
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private int id;
        private String name;
        private boolean isGroup;
        private int createdBy;
        private String createdByName;
        private List<User> participants = new ArrayList<>();
        private LocalDateTime createdAt;
        private Message lastMessage;

        public Builder id(int id) { this.id = id; return this; }
        public Builder name(String name) { this.name = name; return this; }
        public Builder isGroup(boolean isGroup) { this.isGroup = isGroup; return this; }
        public Builder createdBy(int createdBy) { this.createdBy = createdBy; return this; }
        public Builder createdByName(String createdByName) { this.createdByName = createdByName; return this; }
        public Builder participants(List<User> participants) { this.participants = participants; return this; }
        public Builder createdAt(LocalDateTime createdAt) { this.createdAt = createdAt; return this; }
        public Builder lastMessage(Message lastMessage) { this.lastMessage = lastMessage; return this; }

        public Chat build() {
            Chat chat = new Chat();
            chat.setId(id);
            chat.setName(name);
            chat.setGroup(isGroup);
            chat.setCreatedBy(createdBy);
            chat.setCreatedByName(createdByName);
            chat.setParticipants(participants);
            chat.setCreatedAt(createdAt != null ? createdAt : LocalDateTime.now());
            chat.setLastMessage(lastMessage);
            return chat;
        }
    }
}