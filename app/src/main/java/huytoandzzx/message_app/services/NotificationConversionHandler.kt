package huytoandzzx.message_app.services

import android.content.Context
import android.content.Intent
import android.util.Log
import com.onesignal.notifications.INotificationClickEvent
import com.onesignal.notifications.INotificationClickListener
import huytoandzzx.message_app.activities.ChatActivity
import huytoandzzx.message_app.activities.MainActivity
import huytoandzzx.message_app.models.ChatMessage
import huytoandzzx.message_app.models.User
import huytoandzzx.message_app.utilities.Constants

class NotificationConversionHandler(private val context: Context) : INotificationClickListener {
    override fun onClick(event: INotificationClickEvent) {
        val data = event.notification.additionalData
        if (data != null) {
            val chatMessage = ChatMessage().apply {
                conversationId = data.optString("conversationId", "")
                conversationName = data.optString("conversationName", "")
                conversationImage = data.optString("conversationImage", "")
            }
            handleNotificationClick(chatMessage)
        } else {
            Log.e("NotificationHandler", "Notification data is missing")
        }
    }

    private fun handleNotificationClick(chatMessage: ChatMessage) {
        Log.d("NotificationHandler", "Handling notification for: ${chatMessage.conversationName}, ID: ${chatMessage.conversationId}, Image: ${chatMessage.conversationImage}")

        val user = User().apply {
            id = chatMessage.conversationId
            name = chatMessage.conversationName
            image = chatMessage.conversationImage
        }
        onConversionClicked(user)
    }

    private fun onConversionClicked(user: User?) {
        user?.let {
            Log.d("NotificationHandler", "Clicked on notification for user: ${user.name}, ID: ${user.id}, Image: ${user.image}")

            val mainIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            context.startActivity(mainIntent)

            val chatIntent = Intent(context, ChatActivity::class.java).apply {
                putExtra(Constants.KEY_USER, user)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(chatIntent)
        } ?: Log.e("NotificationHandler", "User data is null")
    }
}

