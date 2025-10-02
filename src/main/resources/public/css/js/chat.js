class ChatManager {
    constructor() {
        this.currentUser = null;
        this.currentChat = null;
        this.chats = [];
        this.messages = new Map(); // chatId -> messages array
        this.pollingInterval = null;
        this.init();
    }

    async init() {
        await this.checkAuth();
        this.setupEventListeners();
        this.loadChats();
        this.startPolling();
    }

    async checkAuth() {
        try {
            const response = await fetch('/api/user/current');
            if (response.ok) {
                this.currentUser = await response.json();
                document.getElementById('currentUsername').textContent = this.currentUser.username;
            } else {
                window.location.href = '/';
            }
        } catch (error) {
            console.error('Auth check failed:', error);
            window.location.href = '/';
        }
    }

    setupEventListeners() {
        // Logout
        document.getElementById('logoutBtn').addEventListener('click', () => this.logout());

        // User search
        document.getElementById('userSearch').addEventListener('input', (e) => this.searchUsers(e.target.value));

        // Message sending
        document.getElementById('sendMessageBtn').addEventListener('click', () => this.sendMessage());
        document.getElementById('messageInput').addEventListener('keypress', (e) => {
            if (e.key === 'Enter') this.sendMessage();
        });

        // Group chat creation
        document.getElementById('createGroupBtn').addEventListener('click', () => this.showGroupModal());
        document.getElementById('createGroupConfirm').addEventListener('click', () => this.createGroupChat());
        document.getElementById('cancelGroup').addEventListener('click', () => this.hideGroupModal());

        // Report modal
        document.getElementById('submitReport').addEventListener('click', () => this.submitReport());
        document.getElementById('cancelReport').addEventListener('click', () => this.hideReportModal());

        // Close modals on outside click
        document.addEventListener('click', (e) => {
            if (e.target.classList.contains('modal')) {
                e.target.classList.add('hidden');
            }
        });
    }

    async searchUsers(query) {
        if (!query.trim()) {
            document.getElementById('searchResults').innerHTML = '';
            return;
        }

        try {
            const response = await fetch(`/api/users/search?q=${encodeURIComponent(query)}`);
            const users = await response.json();
            this.displaySearchResults(users);
        } catch (error) {
            console.error('Search error:', error);
        }
    }

    displaySearchResults(users) {
        const container = document.getElementById('searchResults');
        container.innerHTML = '';

        users.forEach(user => {
            if (user.id === this.currentUser.id) return;

            const div = document.createElement('div');
            div.className = 'search-result-item';
            div.innerHTML = `
                <span>${user.username}</span>
                <button class="btn-small start-chat-btn" data-user-id="${user.id}">Начать чат</button>
            `;
            container.appendChild(div);
        });

        // Add event listeners to start chat buttons
        document.querySelectorAll('.start-chat-btn').forEach(btn => {
            btn.addEventListener('click', (e) => {
                const userId = e.target.dataset.userId;
                this.startPrivateChat(userId);
            });
        });
    }

    async startPrivateChat(otherUserId) {
        try {
            const response = await fetch('/api/chat/private', {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/x-www-form-urlencoded',
                },
                body: `otherUserId=${otherUserId}`
            });

            const data = await response.json();
            if (data.chatId) {
                document.getElementById('userSearch').value = '';
                document.getElementById('searchResults').innerHTML = '';
                this.loadChats(); // Reload chats list
            }
        } catch (error) {
            console.error('Start chat error:', error);
        }
    }

    async loadChats() {
        try {
            const response = await fetch('/api/chats');
            this.chats = await response.json();
            this.displayChats();
        } catch (error) {
            console.error('Load chats error:', error);
        }
    }

    displayChats() {
        const container = document.getElementById('contactsList');

        if (this.chats.length === 0) {
            container.innerHTML = '<div class="no-contacts">Нет чатов</div>';
            return;
        }

        container.innerHTML = this.chats.map(chat => `
            <div class="contact-item ${this.currentChat?.id === chat.id ? 'active' : ''}" 
                 data-chat-id="${chat.id}">
                <div class="contact-name">${chat.getDisplayName(this.currentUser.id)}</div>
                <div class="last-message">
                    ${chat.lastMessage ? chat.lastMessage.content : 'Нет сообщений'}
                </div>
            </div>
        `).join('');

        // Add click listeners
        document.querySelectorAll('.contact-item').forEach(item => {
            item.addEventListener('click', () => {
                const chatId = item.dataset.chatId;
                this.selectChat(chatId);
            });
        });
    }

    async selectChat(chatId) {
        this.currentChat = this.chats.find(c => c.id == chatId);
        this.displayChats(); // Update active state

        document.getElementById('noChatSelected').classList.add('hidden');
        document.getElementById('chatWindow').classList.remove('hidden');

        document.getElementById('chatTitle').textContent =
            this.currentChat.getDisplayName(this.currentUser.id);
        document.getElementById('chatParticipants').textContent =
            `Участники: ${this.currentChat.getParticipantsNames(this.currentUser.id)}`;

        // Enable message input
        document.getElementById('messageInput').disabled = false;
        document.getElementById('sendMessageBtn').disabled = false;

        await this.loadMessages(chatId);
    }

    async loadMessages(chatId) {
        try {
            const response = await fetch(`/api/chat/${chatId}/messages`);
            const messages = await response.json();
            this.messages.set(chatId, messages);
            this.displayMessages(messages);
        } catch (error) {
            console.error('Load messages error:', error);
        }
    }

    displayMessages(messages) {
        const container = document.getElementById('messagesContainer');
        container.innerHTML = '';

        messages.forEach(message => {
            const isOwn = message.senderId === this.currentUser.id;
            const div = document.createElement('div');
            div.className = `message-item ${isOwn ? 'own' : 'other'}`;
            div.innerHTML = `
                <div class="message-header">
                    <span class="message-sender">${message.senderName}</span>
                    <span class="message-time">${new Date(message.createdAt).toLocaleTimeString()}</span>
                </div>
                <div class="message-content">${message.getDisplayContent()}</div>
                ${!message.isDeleted ? `
                    <div class="message-actions">
                        ${isOwn ? `
                            <button class="message-action delete-message" data-message-id="${message.id}">🗑️</button>
                        ` : `
                            <button class="message-action report-message" data-message-id="${message.id}">⚠️</button>
                        `}
                    </div>
                ` : ''}
            `;
            container.appendChild(div);
        });

        // Add action listeners
        document.querySelectorAll('.delete-message').forEach(btn => {
            btn.addEventListener('click', (e) => {
                const messageId = e.target.dataset.messageId;
                this.deleteMessage(messageId);
            });
        });

        document.querySelectorAll('.report-message').forEach(btn => {
            btn.addEventListener('click', (e) => {
                const messageId = e.target.dataset.messageId;
                this.showReportModal(messageId);
            });
        });

        // Scroll to bottom
        container.scrollTop = container.scrollHeight;
    }

    async sendMessage() {
        const input = document.getElementById('messageInput');
        const content = input.value.trim();

        if (!content || !this.currentChat) return;

        try {
            const response = await fetch('/api/message', {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/x-www-form-urlencoded',
                },
                body: `chatId=${this.currentChat.id}&content=${encodeURIComponent(content)}`
            });

            const data = await response.json();
            if (data.success) {
                input.value = '';
                this.loadMessages(this.currentChat.id);
                this.loadChats(); // Update last message in chats list
            }
        } catch (error) {
            console.error('Send message error:', error);
        }
    }

    async deleteMessage(messageId) {
        if (!confirm('Удалить это сообщение?')) return;

        try {
            const response = await fetch(`/api/message/${messageId}`, {
                method: 'DELETE'
            });

            const data = await response.json();
            if (data.success && this.currentChat) {
                this.loadMessages(this.currentChat.id);
            }
        } catch (error) {
            console.error('Delete message error:', error);
        }
    }

    showReportModal(messageId) {
        this.reportingMessageId = messageId;
        document.getElementById('reportModal').classList.remove('hidden');
        document.getElementById('reportReason').value = '';
    }

    hideReportModal() {
        document.getElementById('reportModal').classList.add('hidden');
        this.reportingMessageId = null;
    }

    async submitReport() {
        const reason = document.getElementById('reportReason').value.trim();
        if (!reason) {
            alert('Введите причину жалобы');
            return;
        }

        try {
            const response = await fetch('/api/report', {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/x-www-form-urlencoded',
                },
                body: `messageId=${this.reportingMessageId}&reason=${encodeURIComponent(reason)}`
            });

            const data = await response.json();
            if (data.success) {
                alert('Жалоба отправлена администратору');
                this.hideReportModal();
            } else {
                alert('Ошибка отправки жалобы');
            }
        } catch (error) {
            console.error('Report error:', error);
            alert('Ошибка отправки жалобы');
        }
    }

    showGroupModal() {
        this.loadAvailableContacts();
        document.getElementById('groupModal').classList.remove('hidden');
        document.getElementById('groupName').value = '';
    }

    hideGroupModal() {
        document.getElementById('groupModal').classList.add('hidden');
    }

    async loadAvailableContacts() {
        try {
            const response = await fetch('/api/contacts');
            const contacts = await response.json();
            this.displayAvailableContacts(contacts);
        } catch (error) {
            console.error('Load contacts error:', error);
        }
    }

    displayAvailableContacts(contacts) {
        const container = document.getElementById('availableContacts');
        container.innerHTML = contacts.map(contact => `
            <label class="contact-checkbox">
                <input type="checkbox" value="${contact.id}">
                ${contact.username}
            </label>
        `).join('');
    }

    async createGroupChat() {
        const name = document.getElementById('groupName').value.trim();
        if (!name) {
            alert('Введите название группы');
            return;
        }

        const selectedContacts = Array.from(
            document.querySelectorAll('#availableContacts input:checked')
        ).map(input => input.value);

        if (selectedContacts.length === 0) {
            alert('Выберите хотя бы одного участника');
            return;
        }

        try {
            const response = await fetch('/api/chat/group', {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json',
                },
                body: JSON.stringify({
                    name: name,
                    participantIds: selectedContacts
                })
            });

            const data = await response.json();
            if (data.chatId) {
                this.hideGroupModal();
                this.loadChats();
            }
        } catch (error) {
            console.error('Create group error:', error);
            alert('Ошибка создания группы');
        }
    }

    startPolling() {
        this.pollingInterval = setInterval(() => {
            if (this.currentChat) {
                this.loadMessages(this.currentChat.id);
            }
            this.loadChats();
        }, 3000); // Poll every 3 seconds
    }

    stopPolling() {
        if (this.pollingInterval) {
            clearInterval(this.pollingInterval);
        }
    }

    async logout() {
        try {
            await fetch('/api/logout', { method: 'POST' });
            window.location.href = '/';
        } catch (error) {
            console.error('Logout error:', error);
            window.location.href = '/';
        }
    }
}

// Initialize chat manager when page loads
document.addEventListener('DOMContentLoaded', () => {
    window.chatManager = new ChatManager();
});