
let currentUser = null;
let currentChat = null;
let selectedFile = null;
let messagePollingInterval = null;
let friendRequestPollingInterval = null;
let contactPollingInterval = null;

// ==================== ИНИЦИАЛИЗАЦИЯ ====================
document.addEventListener('DOMContentLoaded', function() {
    console.log("🚀 Инициализация чата...");
    initialize();
});

async function initialize() {
    await loadUser();
    setupEventListeners();
    startPolling();
}

// Загрузка информации о текущем пользователе
async function loadUser() {
    try {
        const response = await fetch('/api/user/current');
        if (response.ok) {
            currentUser = await response.json();
            console.log("✅ Пользователь загружен:", currentUser.username);

            updateUserStatusDisplay();
            await fetch('/api/user/ping', { method: 'POST' });

            loadFriendRequests();
            loadContacts();

            // Запускаем периодическое обновление статуса
            setInterval(updateUserStatusDisplay, 30000);
        } else {
            console.error("❌ Пользователь не авторизован");
            window.location.href = '/';
        }
    } catch (error) {
        console.error('❌ Ошибка загрузки пользователя:', error);
        window.location.href = '/';
    }
}

// Настройка обработчиков событий
function setupEventListeners() {
    // Кнопка отправки сообщения
    document.getElementById('sendMessageBtn')?.addEventListener('click', sendMessage);

    // Поле ввода сообщения - отправка по Enter
    const messageInput = document.getElementById('messageInput');
    if (messageInput) {
        messageInput.addEventListener('keypress', function(e) {
            if (e.key === 'Enter' && !e.shiftKey) {
                e.preventDefault();
                sendMessage();
            }
        });
    }

    // Загрузка файлов
    setupFileUpload();

    // Кнопка выхода
    const logoutBtn = document.getElementById('logoutBtn') || document.querySelector('[onclick="logout()"]');
    if (logoutBtn) {
        logoutBtn.addEventListener('click', logout);
    }

    // Поиск пользователей
    const userSearch = document.getElementById('userSearch');
    if (userSearch) {
        userSearch.addEventListener('input', function(e) {
            searchUsers(e.target.value);
        });
    }

    // Смена пароля
    setupPasswordChangeModal();
}

// Настройка загрузки файлов
function setupFileUpload() {
    const fileInput = document.getElementById('fileUpload');
    if (!fileInput) return;

    // Ограничиваем типы файлов
    fileInput.accept = ".jpg,.jpeg,.png,.gif,.bmp,.webp,.pdf,.doc,.docx,.xls,.xlsx,.txt,.mp3,.wav,.ogg,.mp4,.avi,.mov,.zip,.rar,.7z";

    fileInput.addEventListener('change', function(e) {
        if (e.target.files.length > 0) {
            const file = e.target.files[0];

            // Проверяем размер файла (макс 10MB)
            if (file.size > 10 * 1024 * 1024) {
                alert('❌ Файл слишком большой! Максимальный размер: 10MB');
                fileInput.value = '';
                return;
            }

            // Проверяем тип файла
            const allowedTypes = [
                'image/jpeg', 'image/jpg', 'image/png', 'image/gif', 'image/bmp', 'image/webp',
                'application/pdf',
                'application/msword', 'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
                'application/vnd.ms-excel', 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
                'text/plain',
                'audio/mpeg', 'audio/mp3', 'audio/wav', 'audio/ogg',
                'video/mp4', 'video/avi', 'video/quicktime', 'video/x-msvideo',
                'application/zip', 'application/x-rar-compressed', 'application/x-7z-compressed'
            ];

            // Разрешаем файлы без MIME типа, но с правильным расширением
            const fileExtension = file.name.split('.').pop().toLowerCase();
            const allowedExtensions = ['jpg', 'jpeg', 'png', 'gif', 'bmp', 'webp', 'pdf', 'doc', 'docx', 'xls', 'xlsx', 'txt', 'mp3', 'wav', 'ogg', 'mp4', 'avi', 'mov', 'zip', 'rar', '7z'];

            if (!allowedTypes.includes(file.type) && !allowedExtensions.includes(fileExtension)) {
                alert('❌ Недопустимый тип файла! Разрешены: изображения, документы, аудио, видео, архивы');
                fileInput.value = '';
                return;
            }

            selectedFile = file;
            showSelectedFile(file);
        }
    });

    // Кнопка прикрепления файла
    const fileUploadBtn = document.querySelector('.file-upload-btn');
    if (fileUploadBtn) {
        fileUploadBtn.addEventListener('click', function() {
            fileInput.click();
        });
    }
}

// Показать информацию о выбранном файле
function showSelectedFile(file) {
    const infoDiv = document.getElementById('selectedFileInfo');
    const fileNameSpan = document.getElementById('selectedFileName');

    if (infoDiv && fileNameSpan) {
        fileNameSpan.textContent = `${file.name} (${formatFileSize(file.size)})`;
        infoDiv.style.display = 'flex';
    }
}

// Удалить выбранный файл
function removeSelectedFile() {
    selectedFile = null;
    const fileInput = document.getElementById('fileUpload');
    if (fileInput) fileInput.value = '';

    const infoDiv = document.getElementById('selectedFileInfo');
    if (infoDiv) infoDiv.style.display = 'none';
}

// Форматирование размера файла
function formatFileSize(bytes) {
    if (bytes === 0) return '0 Bytes';
    const k = 1024;
    const sizes = ['Bytes', 'KB', 'MB', 'GB'];
    const i = Math.floor(Math.log(bytes) / Math.log(k));
    return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i];
}

// ==================== ОБНОВЛЕНИЕ СТАТУСА ПОЛЬЗОВАТЕЛЯ ====================
async function updateUserStatusDisplay() {
    try {
        const response = await fetch('/api/user/current');
        if (response.ok) {
            const updatedUser = await response.json();
            currentUser = updatedUser;

            const statusElement = document.getElementById('userStatus');
            if (statusElement) {
                if (currentUser.online) {
                    statusElement.textContent = '🟢 В сети';
                    statusElement.className = 'status-online';
                } else {
                    const lastSeenText = formatLastSeen(currentUser.lastSeen);
                    statusElement.textContent = `⚫ Был(а) ${lastSeenText}`;
                    statusElement.className = 'status-offline';
                }
            }
        }
    } catch (error) {
        console.error('❌ Ошибка обновления статуса:', error);
    }
}

// Форматирование времени последней активности
function formatLastSeen(lastSeen) {
    if (!lastSeen) return 'давно';

    try {
        const lastSeenDate = new Date(lastSeen);
        const now = new Date();
        const diffMs = now - lastSeenDate;
        const diffMinutes = Math.floor(diffMs / (1000 * 60));
        const diffSeconds = Math.floor(diffMs / 1000);

        if (diffSeconds < 30) return 'только что';
        if (diffMinutes < 1) return 'менее минуты назад';
        if (diffMinutes < 60) return `${diffMinutes} мин назад`;
        if (diffMinutes < 1440) return `${Math.floor(diffMinutes / 60)} ч назад`;

        return lastSeenDate.toLocaleDateString('ru-RU', {
            day: 'numeric',
            month: 'short',
            hour: '2-digit',
            minute: '2-digit'
        });
    } catch (e) {
        return 'недавно';
    }
}

// ==================== ПОИСК ПОЛЬЗОВАТЕЛЕЙ ====================
async function searchUsers(query) {
    const resultsContainer = document.getElementById('searchResults');

    if (!query || query.trim().length < 2) {
        if (resultsContainer) resultsContainer.innerHTML = '';
        return;
    }

    console.log("🔍 Поиск пользователей:", query);

    try {
        const response = await fetch(`/api/users/search?q=${encodeURIComponent(query)}`);
        if (!response.ok) {
            throw new Error('Ошибка поиска');
        }

        const users = await response.json();
        console.log("📋 Найдено пользователей:", users.length);
        displaySearchResults(users);
    } catch (error) {
        console.error('❌ Ошибка поиска:', error);
        if (resultsContainer) {
            resultsContainer.innerHTML = '<div class="no-results">❌ Ошибка поиска</div>';
        }
    }
}

// Отображение результатов поиска
function displaySearchResults(users) {
    const container = document.getElementById('searchResults');
    if (!container) return;

    if (users.length === 0) {
        container.innerHTML = '<div class="no-results">👤 Пользователи не найдены</div>';
        return;
    }

    container.innerHTML = users.map(user => `
        <div class="search-result-item">
            <div class="user-info">
                <strong>👤 ${user.username}</strong>
                <span class="user-id">ID: ${user.id}</span>
            </div>
            <button class="btn-small send-request-btn"
                    onclick="sendFriendRequest(${user.id})"
                    title="Отправить запрос в друзья">
                📨 Добавить
            </button>
        </div>
    `).join('');
}

// ==================== ЗАПРОСЫ В ДРУЗЬЯ ====================
// Отправка запроса в друзья
async function sendFriendRequest(toUserId) {
    if (!currentUser) {
        alert('❌ Вы не авторизованы');
        return;
    }

    console.log("🔄 Отправка запроса пользователю ID:", toUserId);

    try {
        const response = await fetch('/api/friend-request', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/x-www-form-urlencoded',
            },
            body: `toUserId=${toUserId}`
        });

        const data = await response.json();
        console.log("📨 Ответ сервера:", data);

        if (data.success) {
            alert('✅ Запрос отправлен!');
            // Очищаем результаты поиска
            const searchResults = document.getElementById('searchResults');
            const userSearch = document.getElementById('userSearch');
            if (searchResults) searchResults.innerHTML = '';
            if (userSearch) userSearch.value = '';
        } else {
            alert('❌ ' + data.message);
        }
    } catch (error) {
        console.error('💥 Ошибка отправки запроса:', error);
        alert('💥 Ошибка соединения с сервером');
    }
}

// Загрузка входящих запросов
async function loadFriendRequests() {
    try {
        const response = await fetch('/api/friend-requests/incoming');
        if (!response.ok) {
            throw new Error('Ошибка загрузки запросов');
        }

        const requests = await response.json();
        console.log("📨 Загружено запросов:", requests.length);
        displayFriendRequests(requests);
    } catch (error) {
        console.error('❌ Ошибка загрузки запросов:', error);
    }
}

// Отображение входящих запросов
function displayFriendRequests(requests) {
    const container = document.getElementById('friendRequests');
    if (!container) return;

    if (requests.length === 0) {
        container.innerHTML = '<div class="no-requests">📭 Запросов нет</div>';
        return;
    }

    container.innerHTML = requests.map(request => `
        <div class="friend-request-item">
            <div class="request-info">
                <strong>👤 ${request.fromUsername}</strong>
                <span>хочет добавить вас в контакты</span>
            </div>
            <div class="request-actions">
                <button class="btn-small accept-btn"
                        onclick="acceptFriendRequest(${request.id})"
                        title="Принять запрос">
                    ✅ Принять
                </button>
                <button class="btn-small reject-btn"
                        onclick="rejectFriendRequest(${request.id})"
                        title="Отклонить запрос">
                    ❌ Отклонить
                </button>
            </div>
        </div>
    `).join('');
}

// Принятие запроса
async function acceptFriendRequest(requestId) {
    console.log("✅ Принятие запроса ID:", requestId);

    try {
        const response = await fetch(`/api/friend-requests/${requestId}/accept`, {
            method: 'POST'
        });

        const data = await response.json();

        if (data.success) {
            alert('✅ Запрос принят! Пользователь добавлен в контакты');
            loadFriendRequests();
            loadContacts();
        } else {
            alert('❌ Ошибка принятия запроса: ' + data.message);
        }
    } catch (error) {
        console.error('💥 Ошибка принятия запроса:', error);
        alert('💥 Ошибка соединения с сервером');
    }
}

// Отклонение запроса
async function rejectFriendRequest(requestId) {
    console.log("❌ Отклонение запроса ID:", requestId);

    if (!confirm('Вы уверены, что хотите отклонить запрос?')) {
        return;
    }

    try {
        const response = await fetch(`/api/friend-requests/${requestId}/reject`, {
            method: 'POST'
        });

        const data = await response.json();

        if (data.success) {
            alert('✅ Запрос отклонен');
            loadFriendRequests();
        } else {
            alert('❌ Ошибка отклонения запроса');
        }
    } catch (error) {
        console.error('💥 Ошибка отклонения запроса:', error);
        alert('💥 Ошибка соединения с сервером');
    }
}

// ==================== КОНТАКТЫ ====================
// Загрузка контактов
async function loadContacts() {
    try {
        const response = await fetch('/api/contacts');
        if (!response.ok) {
            throw new Error('Ошибка загрузки контактов');
        }

        const contacts = await response.json();
        console.log("📞 Загружено контактов:", contacts.length);
        displayContacts(contacts);
    } catch (error) {
        console.error('❌ Ошибка загрузки контактов:', error);
    }
}

// Отображение контактов
function displayContacts(contacts) {
    const container = document.getElementById('contactsList');
    if (!container) return;

    if (contacts.length === 0) {
        container.innerHTML = '<div class="no-contacts">👥 Контактов пока нет</div>';
        return;
    }

    container.innerHTML = contacts.map(contact => {
        const isOnline = contact.online || contact.isOnline;
        const status = isOnline ? '🟢 В сети' : `⚫ Был(а) ${formatLastSeen(contact.lastSeen)}`;
        const statusClass = isOnline ? 'status-online' : 'status-offline';

        return `
            <div class="contact-item" data-user-id="${contact.id}">
                <div class="contact-avatar">
                    <img src="${contact.profileImageUrl || '/images/default-avatar.png'}"
                         class="avatar avatar-small" alt="${contact.username}">
                </div>
                <div class="contact-info">
                    <div class="contact-name">${contact.username}</div>
                    <div class="contact-status ${statusClass}">${status}</div>
                </div>
                <button class="btn-small start-chat-btn"
                        onclick="startChat(${contact.id}, '${contact.username}')"
                        title="Начать чат">
                    💬 Чат
                </button>
            </div>
        `;
    }).join('');
}

// Начало чата
async function startChat(userId, username) {
    console.log("💬 Начало чата с:", username, "ID:", userId);

    try {
        const response = await fetch('/api/chats/private', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/x-www-form-urlencoded',
            },
            body: `otherUserId=${userId}`
        });

        const data = await response.json();

        if (data.success && data.chatId) {
            currentChat = {
                id: data.chatId,
                userId: userId,
                name: username,
                isPrivate: true
            };

            // Показываем область чата
            document.querySelector('.no-chat-selected')?.classList.add('hidden');
            const activeChat = document.getElementById('activeChat');
            if (activeChat) activeChat.classList.remove('hidden');

            // Устанавливаем информацию о чате
            const chatTitle = document.getElementById('chatTitle');
            const chatParticipants = document.getElementById('chatParticipants');
            if (chatTitle) chatTitle.textContent = username;
            if (chatParticipants) chatParticipants.textContent = `Участники: ${username}`;

            // Активируем поле ввода
            const messageInput = document.getElementById('messageInput');
            const sendMessageBtn = document.getElementById('sendMessageBtn');
            if (messageInput) messageInput.disabled = false;
            if (sendMessageBtn) sendMessageBtn.disabled = false;

            // Фокусируемся на поле ввода
            if (messageInput) messageInput.focus();

            // Загружаем историю сообщений
            await loadChatMessages(data.chatId);

            // Запускаем автообновление сообщений
            startMessagePolling(data.chatId);
        } else {
            alert('❌ Ошибка создания чата: ' + (data.message || 'Неизвестная ошибка'));
        }
    } catch (error) {
        console.error('💥 Ошибка начала чата:', error);
        alert('💥 Ошибка соединения с сервером');
    }
}

// ==================== РАБОТА С СООБЩЕНИЯМИ ====================
// Загрузка сообщений чата
async function loadChatMessages(chatId) {
    console.log("📨 Загрузка сообщений для чата ID:", chatId);

    try {
        const response = await fetch(`/api/chats/${chatId}/messages`);
        if (!response.ok) {
            throw new Error('Ошибка загрузки сообщений');
        }

        const messages = await response.json();
        displayMessages(messages);
    } catch (error) {
        console.error('❌ Ошибка загрузки сообщений:', error);
        const container = document.getElementById('messagesContainer');
        if (container) {
            container.innerHTML = `
                <div class="no-messages">
                    <p>❌ Ошибка загрузки сообщений</p>
                </div>
            `;
        }
    }
}

// Отображение сообщений
function displayMessages(messages) {
    const container = document.getElementById('messagesContainer');
    if (!container) return;

    if (messages.length === 0) {
        container.innerHTML = `
            <div class="no-messages">
                <p>💬 Начните общение с этим пользователем!</p>
                <p><em>Отправьте первое сообщение</em></p>
            </div>
        `;
        return;
    }

    container.innerHTML = messages.map(message => {
        const isOwnMessage = currentUser && message.senderId === currentUser.id;
        const messageTime = new Date(message.createdAt).toLocaleTimeString('ru-RU', {
            hour: '2-digit',
            minute: '2-digit'
        });

        // Контент файла, если есть
        let fileContent = '';
        if (message.hasFile) {
            const fileIcon = getFileIcon(message.fileType);
            const fileSize = message.fileSize ? formatFileSize(message.fileSize) : '';

            if (message.fileType === 'image') {
                fileContent = `
                    <div class="message-file image-file">
                        <img src="${message.fileUrl}" 
                             alt="${message.fileName}"
                             onclick="openImageModal('${message.fileUrl}', '${message.fileName}')"
                             loading="lazy">
                    </div>
                `;
            } else if (message.fileType === 'video') {
                fileContent = `
                    <div class="message-file">
                        <div class="file-icon">${fileIcon}</div>
                        <div class="file-info">
                            <div class="file-name">${message.fileName}</div>
                            ${fileSize ? `<div class="file-size">${fileSize}</div>` : ''}
                        </div>
                        <video controls class="video-player">
                            <source src="${message.fileUrl}" type="video/mp4">
                            Ваш браузер не поддерживает видео.
                        </video>
                        <a href="${message.fileUrl}" 
                           class="download-btn"
                           download="${message.fileName}">
                            📥
                        </a>
                    </div>
                `;
            } else if (message.fileType === 'audio') {
                fileContent = `
                    <div class="message-file">
                        <div class="file-icon">${fileIcon}</div>
                        <div class="file-info">
                            <div class="file-name">${message.fileName}</div>
                            ${fileSize ? `<div class="file-size">${fileSize}</div>` : ''}
                        </div>
                        <audio controls class="audio-player">
                            <source src="${message.fileUrl}" type="audio/mpeg">
                            Ваш браузер не поддерживает аудио.
                        </audio>
                        <a href="${message.fileUrl}" 
                           class="download-btn"
                           download="${message.fileName}">
                            📥
                        </a>
                    </div>
                `;
            } else {
                fileContent = `
                    <div class="message-file">
                        <div class="file-icon">${fileIcon}</div>
                        <div class="file-info">
                            <div class="file-name">${message.fileName}</div>
                            ${fileSize ? `<div class="file-size">${fileSize}</div>` : ''}
                        </div>
                        <a href="${message.fileUrl}" 
                           class="download-btn"
                           download="${message.fileName}">
                            📥
                        </a>
                    </div>
                `;
            }
        }

        return `
            <div class="message-item ${isOwnMessage ? 'own' : 'other'}">
                <div class="message-header">
                    <span class="message-sender">${message.senderName}</span>
                    <span class="message-time">${messageTime}</span>
                </div>
                ${message.content ? `<div class="message-content">${message.content}</div>` : ''}
                ${fileContent}
                ${!message.isDeleted ? `
                    <div class="message-actions">
                        ${isOwnMessage ? `
                            <button class="message-action delete-message"
                                    onclick="deleteMessage(${message.id})"
                                    title="Удалить сообщение">🗑️</button>
                        ` : `
                            <button class="message-action report-message"
                                    onclick="reportMessage(${message.id})"
                                    title="Пожаловаться">⚠️</button>
                        `}
                    </div>
                ` : ''}
            </div>
        `;
    }).join('');

    // Прокручиваем вниз
    container.scrollTop = container.scrollHeight;
}

// Получение иконки для типа файла
function getFileIcon(fileType) {
    const icons = {
        'image': '🖼️',
        'video': '🎬',
        'audio': '🎵',
        'pdf': '📕',
        'word': '📄',
        'excel': '📊',
        'archive': '🗜️',
        'document': '📋'
    };
    return icons[fileType] || '📎';
}

// Открытие модального окна для изображения
function openImageModal(src, alt) {
    const modal = document.createElement('div');
    modal.className = 'image-modal modal';
    modal.innerHTML = `
        <div class="modal-content" style="background: transparent; border: none; max-width: 90vw; max-height: 90vh;">
            <img src="${src}" alt="${alt}" style="max-width: 100%; max-height: 100%; border-radius: 10px;">
            <button onclick="this.parentElement.parentElement.remove()" 
                    style="position: absolute; top: 10px; right: 10px; background: rgba(255,255,255,0.9); border: none; border-radius: 50%; width: 40px; height: 40px; font-size: 1.5rem; cursor: pointer;">×</button>
        </div>
    `;
    document.body.appendChild(modal);

    // Закрытие по клику на фон
    modal.addEventListener('click', function(e) {
        if (e.target === modal) {
            modal.remove();
        }
    });
}

// Запуск автообновления сообщений
function startMessagePolling(chatId) {
    // Останавливаем предыдущий интервал
    if (messagePollingInterval) {
        clearInterval(messagePollingInterval);
    }

    // Запускаем новый интервал
    messagePollingInterval = setInterval(async () => {
        if (currentChat && currentChat.id === chatId) {
            await loadChatMessages(chatId);
        }
    }, 2000); // Обновляем каждые 2 секунды
}

// Остановка автообновления
function stopMessagePolling() {
    if (messagePollingInterval) {
        clearInterval(messagePollingInterval);
        messagePollingInterval = null;
    }
}

// ==================== ОТПРАВКА СООБЩЕНИЙ ====================
// Отправка сообщения (текст и/или файл)
async function sendMessage() {
    if (!currentChat || !currentUser) {
        alert('❌ Чат не выбран');
        return;
    }

    const messageInput = document.getElementById('messageInput');
    const content = messageInput ? messageInput.value.trim() : '';

    // Проверяем, есть ли что отправлять
    if (!content && !selectedFile) {
        return;
    }

    console.log("📤 Отправка сообщения:", {
        chatId: currentChat.id,
        content: content,
        hasFile: !!selectedFile
    });

    try {
        const formData = new FormData();
        formData.append('chatId', currentChat.id);
        if (content) {
            formData.append('content', content);
        }
        if (selectedFile) {
            formData.append('file', selectedFile);
        }

        const response = await fetch('/api/messages/upload-file', {
            method: 'POST',
            body: formData
        });

        const data = await response.json();

        if (data.success) {
            // Очищаем поле ввода
            if (messageInput) messageInput.value = '';
            removeSelectedFile();

            // Немедленно обновляем сообщения
            await loadChatMessages(currentChat.id);
        } else {
            alert('❌ Ошибка отправки сообщения: ' + data.message);
        }
    } catch (error) {
        console.error('💥 Ошибка отправки сообщения:', error);
        alert('💥 Ошибка соединения с сервером');
    }
}

// Удаление сообщения
async function deleteMessage(messageId) {
    if (!confirm('Удалить это сообщение?')) return;

    try {
        const response = await fetch(`/api/messages/${messageId}`, {
            method: 'DELETE'
        });

        const data = await response.json();
        if (data.success && currentChat) {
            await loadChatMessages(currentChat.id);
        }
    } catch (error) {
        console.error('Ошибка удаления сообщения:', error);
        alert('❌ Ошибка удаления сообщения');
    }
}

// Жалоба на сообщение
async function reportMessage(messageId) {
    const reason = prompt('Введите причину жалобы:');
    if (!reason) return;

    try {
        const response = await fetch('/api/reports', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/x-www-form-urlencoded',
            },
            body: `messageId=${messageId}&reason=${encodeURIComponent(reason)}`
        });

        const data = await response.json();
        if (data.success) {
            alert('✅ Жалоба отправлена администратору');
        } else {
            alert('❌ Ошибка отправки жалобы');
        }
    } catch (error) {
        console.error('Ошибка отправки жалобы:', error);
        alert('❌ Ошибка отправки жалобы');
    }
}

// ==================== СМЕНА ПАРОЛЯ ====================
function setupPasswordChangeModal() {
    // Кнопка смены пароля
    const changePasswordBtn = document.querySelector('[onclick="showChangePasswordModal()"]');
    if (changePasswordBtn) {
        changePasswordBtn.addEventListener('click', showChangePasswordModal);
    }

    // Кнопка отмены в модальном окне
    const cancelBtn = document.querySelector('[onclick="hideChangePasswordModal()"]');
    if (cancelBtn) {
        cancelBtn.addEventListener('click', hideChangePasswordModal);
    }

    // Кнопка подтверждения смены пароля
    const confirmBtn = document.querySelector('[onclick="changePassword()"]');
    if (confirmBtn) {
        confirmBtn.addEventListener('click', changePassword);
    }

    // Обработка Enter в полях пароля
    const passwordInputs = document.querySelectorAll('#changePasswordModal input[type="password"]');
    passwordInputs.forEach(input => {
        input.addEventListener('keypress', function(e) {
            if (e.key === 'Enter') {
                changePassword();
            }
        });
    });
}

// Показать модальное окно смены пароля
function showChangePasswordModal() {
    const modal = document.getElementById('changePasswordModal');
    if (modal) {
        modal.classList.remove('hidden');
        // Очищаем поля и сообщения
        document.getElementById('currentPassword').value = '';
        document.getElementById('newPassword').value = '';
        document.getElementById('confirmNewPassword').value = '';
        document.getElementById('changePasswordMessage').textContent = '';
    }
}

// Скрыть модальное окно смены пароля
function hideChangePasswordModal() {
    const modal = document.getElementById('changePasswordModal');
    if (modal) modal.classList.add('hidden');
}

// Смена пароля
async function changePassword() {
    const currentPassword = document.getElementById('currentPassword')?.value;
    const newPassword = document.getElementById('newPassword')?.value;
    const confirmNewPassword = document.getElementById('confirmNewPassword')?.value;
    const messageElement = document.getElementById('changePasswordMessage');
    const changeBtn = document.querySelector('#changePasswordModal .btn-primary');

    if (!currentPassword || !newPassword || !confirmNewPassword) {
        showChangePasswordMessage('Заполните все поля', 'error');
        return;
    }

    if (newPassword.length < 3) {
        showChangePasswordMessage('Новый пароль должен содержать минимум 3 символа', 'error');
        return;
    }

    if (newPassword !== confirmNewPassword) {
        showChangePasswordMessage('Новые пароли не совпадают', 'error');
        return;
    }

    // Показываем загрузку
    if (changeBtn) {
        changeBtn.textContent = 'Смена...';
        changeBtn.disabled = true;
    }

    try {
        console.log("🔄 Смена пароля...");

        const response = await fetch('/api/change-password', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/x-www-form-urlencoded',
            },
            body: `currentPassword=${encodeURIComponent(currentPassword)}&newPassword=${encodeURIComponent(newPassword)}`
        });

        const data = await response.json();
        console.log("📨 Ответ смены пароля:", data);

        if (data.success) {
            showChangePasswordMessage('Пароль успешно изменен!', 'success');
            setTimeout(() => {
                hideChangePasswordModal();
            }, 2000);
        } else {
            showChangePasswordMessage(data.message || 'Ошибка смены пароля', 'error');
        }
    } catch (error) {
        console.error('💥 Ошибка смены пароля:', error);
        showChangePasswordMessage('Ошибка соединения с сервером', 'error');
    } finally {
        // Восстанавливаем кнопку
        if (changeBtn) {
            changeBtn.textContent = 'Сменить пароль';
            changeBtn.disabled = false;
        }
    }
}

// Показать сообщение в модальном окне смены пароля
function showChangePasswordMessage(message, type) {
    const element = document.getElementById('changePasswordMessage');
    if (element) {
        element.textContent = message;
        element.className = `message ${type}`;
    }
}

// ==================== ПОЛИНГ ====================
function startPolling() {
    // Обновление запросов каждые 10 секунд
    friendRequestPollingInterval = setInterval(() => {
        if (currentUser) {
            loadFriendRequests();
        }
    }, 10000);

    // Обновление контактов каждые 15 секунд
    contactPollingInterval = setInterval(() => {
        if (currentUser) {
            loadContacts();
            updateUserStatusDisplay();
        }
    }, 15000);

    // Обновление статуса онлайн каждые 30 секунд
    setInterval(() => {
        if (currentUser) {
            fetch('/api/user/ping', { method: 'POST' });
        }
    }, 30000);
}

function stopPolling() {
    if (friendRequestPollingInterval) {
        clearInterval(friendRequestPollingInterval);
    }
    if (contactPollingInterval) {
        clearInterval(contactPollingInterval);
    }
    stopMessagePolling();
}

// ==================== СИСТЕМНЫЕ ФУНКЦИИ ====================
// Выход из системы
async function logout() {
    console.log("🚪 Выход из системы...");

    if (!confirm('Вы уверены, что хотите выйти?')) {
        return;
    }

    // Останавливаем все интервалы
    stopPolling();

    try {
        const response = await fetch('/api/logout', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/x-www-form-urlencoded',
            }
        });

        console.log("✅ Выход выполнен, статус:", response.status);
        window.location.href = '/';
    } catch (error) {
        console.error('❌ Ошибка выхода:', error);
        window.location.href = '/';
    }
}

// ==================== ОБРАБОТЧИКИ СОБЫТИЙ БРАУЗЕРА ====================
// При загрузке страницы отмечаем онлайн
window.addEventListener('load', () => {
    fetch('/api/user/ping', { method: 'POST' });
});

// При закрытии страницы отмечаем оффлайн
window.addEventListener('beforeunload', () => {
    // Используем sendBeacon для надежной отправки при закрытии
    if (navigator.sendBeacon) {
        navigator.sendBeacon('/api/user/logout');
    } else {
        // Fallback для старых браузеров
        const xhr = new XMLHttpRequest();
        xhr.open('POST', '/api/user/logout', false); // Синхронный запрос
        xhr.send();
    }
});

// При фокусе на окне отмечаем онлайн
window.addEventListener('focus', () => {
    fetch('/api/user/ping', { method: 'POST' });
});

// При уходе со страницы отмечаем оффлайн через 30 секунд
let blurTimeout;
window.addEventListener('blur', () => {
    blurTimeout = setTimeout(() => {
        if (!document.hasFocus()) {
            fetch('/api/user/logout', { method: 'POST' });
        }
    }, 30000);
});

window.addEventListener('focus', () => {
    if (blurTimeout) {
        clearTimeout(blurTimeout);
    }
});

// ==================== ЗАГРУЗКА АВАТАРКИ ====================
// Загрузка аватарки
document.getElementById('avatarUpload')?.addEventListener('change', function(e) {
    const file = e.target.files[0];
    if (file) {
        uploadAvatar(file);
    }
});

async function uploadAvatar(file) {
    const formData = new FormData();
    formData.append('avatar', file);

    try {
        const response = await fetch('/api/profile/upload-avatar', {
            method: 'POST',
            body: formData
        });

        const data = await response.json();
        if (data.success) {
            alert('✅ Аватар обновлен!');
            location.reload();
        } else {
            alert('❌ Ошибка: ' + data.message);
        }
    } catch (error) {
        console.error('Ошибка загрузки аватарки:', error);
        alert('❌ Ошибка загрузки файла');
    }
}

// ==================== СОЗДАНИЕ ГРУПП ====================

// Показать модальное окно создания группы
function showCreateGroupModal() {
    const modal = document.getElementById('createGroupModal');
    if (!modal) {
        createGroupModal();
        return;
    }

    document.getElementById('groupName').value = '';
    loadContactsForGroup();
    modal.classList.remove('hidden');
}

// Создать модальное окно группы
function createGroupModal() {
    const modalHTML = `
    <div id="createGroupModal" class="modal hidden">
        <div class="modal-content">
            <h3>👥 Создать новую группу</h3>
            
            <div class="form-group">
                <label for="groupName">Название группы:</label>
                <input type="text" id="groupName" placeholder="Введите название группы" required>
            </div>
            
            <div class="form-group">
                <label>Выберите участников из контактов:</label>
                <div id="groupParticipantsList" class="participants-list">
                    <!-- Список контактов с чекбоксами -->
                </div>
            </div>
            
            <div class="selected-participants" id="selectedParticipants">
                <h4>Выбранные участники (<span id="selectedCount">0</span>):</h4>
                <div id="selectedParticipantsList"></div>
            </div>
            
            <div class="modal-actions">
                <button type="button" class="btn-secondary" onclick="hideCreateGroupModal()">Отмена</button>
                <button type="button" class="btn-primary" onclick="createGroup()">Создать группу</button>
            </div>
            
            <div id="createGroupMessage" class="message"></div>
        </div>
    </div>
    `;

    document.body.insertAdjacentHTML('beforeend', modalHTML);
}

// Скрыть модальное окно создания группы
function hideCreateGroupModal() {
    const modal = document.getElementById('createGroupModal');
    if (modal) modal.classList.add('hidden');
}

// Загрузка контактов для выбора в группу
function loadContactsForGroup() {
    const contactsList = document.getElementById('groupParticipantsList');
    const selectedList = document.getElementById('selectedParticipantsList');
    const selectedCount = document.getElementById('selectedCount');

    if (!contactsList) return;

    // Загружаем контакты
    fetch('/api/contacts')
        .then(response => response.json())
        .then(contacts => {
            if (contacts.length === 0) {
                contactsList.innerHTML = '<div class="no-contacts">У вас нет контактов</div>';
                return;
            }

            contactsList.innerHTML = contacts.map(contact => `
                <div class="participant-checkbox-item" data-user-id="${contact.id}">
                    <input type="checkbox" id="contact_${contact.id}" 
                           onchange="toggleParticipant(${contact.id}, '${contact.username}')">
                    <div class="participant-info">
                        <img src="${contact.profileImageUrl || '/images/default-avatar.png'}" 
                             class="avatar avatar-small" alt="${contact.username}">
                        <div style="margin-left: 10px;">
                            <div>${contact.username}</div>
                            <div class="contact-status">${contact.online ? '🟢 В сети' : '⚫ Не в сети'}</div>
                        </div>
                    </div>
                </div>
            `).join('');

            // Очищаем выбранных участников
            selectedList.innerHTML = '';
            selectedCount.textContent = '0';
        })
        .catch(error => {
            console.error('❌ Ошибка загрузки контактов:', error);
            contactsList.innerHTML = '<div class="no-contacts">❌ Ошибка загрузки контактов</div>';
        });
}

// Выбор/отмена выбора участника
let selectedParticipants = new Map();

function toggleParticipant(userId, username) {
    const checkbox = document.getElementById(`contact_${userId}`);
    const selectedList = document.getElementById('selectedParticipantsList');
    const selectedCount = document.getElementById('selectedCount');

    if (checkbox.checked) {
        // Добавляем участника
        selectedParticipants.set(userId, username);

        const participantHTML = `
            <div class="selected-participant-item" id="selected_${userId}">
                ${username}
                <span class="selected-participant-remove" onclick="removeParticipant(${userId})">×</span>
            </div>
        `;

        selectedList.insertAdjacentHTML('beforeend', participantHTML);
    } else {
        // Удаляем участника
        removeParticipant(userId);
    }

    // Обновляем счетчик
    selectedCount.textContent = selectedParticipants.size;
}

// Удаление участника
function removeParticipant(userId) {
    selectedParticipants.delete(userId);

    const selectedItem = document.getElementById(`selected_${userId}`);
    if (selectedItem) {
        selectedItem.remove();
    }

    const checkbox = document.getElementById(`contact_${userId}`);
    if (checkbox) {
        checkbox.checked = false;
    }

    const selectedCount = document.getElementById('selectedCount');
    if (selectedCount) {
        selectedCount.textContent = selectedParticipants.size;
    }
}

// Создание группы
async function createGroup() {
    const groupName = document.getElementById('groupName').value.trim();
    const messageElement = document.getElementById('createGroupMessage');

    // Валидация
    if (!groupName) {
        showGroupMessage('Введите название группы', 'error');
        return;
    }

    if (selectedParticipants.size < 2) {
        showGroupMessage('Выберите минимум 2 участника', 'error');
        return;
    }

    // Подготавливаем данные
    const participantIds = Array.from(selectedParticipants.keys());

    console.log("👥 Создание группы:", {
        name: groupName,
        participants: participantIds
    });

    try {
        const response = await fetch('/api/chats/group', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
            },
            body: JSON.stringify({
                name: groupName,
                participantIds: participantIds
            })
        });

        const data = await response.json();

        if (data.success) {
            showGroupMessage('✅ Группа успешно создана!', 'success');

            // Закрываем модальное окно через 2 секунды
            setTimeout(() => {
                hideCreateGroupModal();
                // Перезагружаем чаты (если у вас есть такой функционал)
                // loadChats();
            }, 2000);
        } else {
            showGroupMessage('❌ ' + (data.message || 'Ошибка создания группы'), 'error');
        }
    } catch (error) {
        console.error('💥 Ошибка создания группы:', error);
        showGroupMessage('💥 Ошибка соединения с сервером', 'error');
    }
}

// Показать сообщение в модальном окне
function showGroupMessage(message, type) {
    const element = document.getElementById('createGroupMessage');
    element.textContent = message;
    element.className = `message ${type}`;
}

// ==================== ЗАГРУЗКА ГРУППОВЫХ ЧАТОВ ====================

// Функция для загрузки чатов (добавьте в вашу существующую логику)
async function loadChats() {
    try {
        const response = await fetch('/api/chats');
        if (!response.ok) throw new Error('Ошибка загрузки чатов');

        const chats = await response.json();
        displayChats(chats);
    } catch (error) {
        console.error('❌ Ошибка загрузки чатов:', error);
    }
}

// Отображение чатов (обновите вашу существующую функцию)
function displayChats(chats) {
    // Разделяем чаты на приватные и групповые
    const privateChats = chats.filter(chat => !chat.group);
    const groupChats = chats.filter(chat => chat.group);

    // Ваша логика отображения...
}

// Делаем функции глобально доступными
window.showCreateGroupModal = showCreateGroupModal;
window.hideCreateGroupModal = hideCreateGroupModal;
window.toggleParticipant = toggleParticipant;
window.removeParticipant = removeParticipant;
window.createGroup = createGroup;

// ==================== ГЛОБАЛЬНЫЕ ФУНКЦИИ ====================
// Делаем функции доступными глобально
window.searchUsers = searchUsers;
window.sendFriendRequest = sendFriendRequest;
window.acceptFriendRequest = acceptFriendRequest;
window.rejectFriendRequest = rejectFriendRequest;
window.startChat = startChat;
window.deleteMessage = deleteMessage;
window.reportMessage = reportMessage;
window.sendMessage = sendMessage;
window.logout = logout;
window.showChangePasswordModal = showChangePasswordModal;
window.hideChangePasswordModal = hideChangePasswordModal;
window.changePassword = changePassword;
window.removeSelectedFile = removeSelectedFile;
window.openImageModal = openImageModal;
window.formatFileSize = formatFileSize;
window.formatLastSeen = formatLastSeen;
