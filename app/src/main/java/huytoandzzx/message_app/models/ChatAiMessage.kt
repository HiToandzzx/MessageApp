package huytoandzzx.message_app.models

data class ChatAiMessage(
    val text: String,
    val isUser: Boolean // true nếu là tin nhắn của user, false nếu là bot
)
