package huytoandzzx.message_app.models

import java.util.Date

data class ChatMessage(
    var senderId: String = "",
    var receiverId: String = "",
    var message: String = "",
    var reaction: String = "",
    var dateTime: String = "",
    var dateObject: Date = Date(),

    var conversationId: String= "",
    var conversationName: String= "",
    var conversationImage: String= "",
)
