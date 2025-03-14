package huytoandzzx.message_app.listeners

import android.content.Context
import android.content.Intent
import android.util.Log
import com.onesignal.notifications.INotificationClickEvent
import com.onesignal.notifications.INotificationClickListener
import huytoandzzx.message_app.activities.ChatActivity

class MyNotificationClickListener(private var context: Context) : INotificationClickListener {
    override fun onClick(event: INotificationClickEvent) {
        try {
            // Lấy dữ liệu bổ sung từ thông báo
            val additionalData = event.notification.additionalData
            val conversationId = additionalData?.optString("conversationId", "")
            val conversationName = additionalData?.optString("conversationName", "Unknown")
            val conversationImage = additionalData?.optString("conversationImage", "")

            if (!conversationId.isNullOrEmpty()) {
                val intent = Intent(context, ChatActivity::class.java).apply {
                    putExtra("conversationId", conversationId)
                    putExtra("conversationName", conversationName)
                    putExtra("conversationImage", conversationImage)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }

                context.startActivity(intent)
            } else {
                Log.e("MyNotificationClick", "Không có conversationId")
            }
        } catch (e: Exception) {
            Log.e("MyNotificationClick", "Lỗi khi mở ChatActivity: ${e.message}")
        }
    }
}

