package huytoandzzx.message_app.services

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

object OneSignalNotificationService {

    // Hàm gửi push notification qua OneSignal
    fun sendPushNotification(
        restApiKey: String,
        appId: String,
        playerId: String,
        messageText: String,
        senderId: String?,
        senderName: String?
    ) {
        // Nếu không có Player ID, không gửi được
        if (playerId.isEmpty()) {
            Log.d("OneSignalService", "Không có playerId của người nhận")
            return
        }

        // Xây dựng payload JSON theo yêu cầu của OneSignal
        val jsonBody = JSONObject().apply {
            put("app_id", appId)
            put("include_player_ids", JSONArray().put(playerId))
            put("headings", JSONObject().put("en", "New Message from $senderName"))
            put("contents", JSONObject().put("en", messageText))
            // Optional: thêm dữ liệu bổ sung nếu cần
            put("data", JSONObject().apply {
                put("senderId", senderId ?: "")
                put("senderName", senderName ?: "")
            })
        }

        // Sử dụng Coroutine để gửi request
        CoroutineScope(Dispatchers.IO).launch {
            val client = OkHttpClient()
            val mediaType = "application/json; charset=utf-8".toMediaType()
            val body = jsonBody.toString().toRequestBody(mediaType)
            val request = Request.Builder()
                .url("https://onesignal.com/api/v1/notifications")
                .post(body)
                .addHeader("Content-Type", "application/json")
                .addHeader("Authorization", "Basic $restApiKey")
                .build()
            try {
                val response = client.newCall(request).execute()
                Log.d("OneSignalService", "Response: ${response.body?.string()}")
            } catch (e: Exception) {
                Log.e("OneSignalService", "Lỗi khi gửi push notification: ${e.message}")
            }
        }
    }
}
