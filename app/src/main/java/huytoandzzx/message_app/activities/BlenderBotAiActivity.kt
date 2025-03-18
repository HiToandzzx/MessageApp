package huytoandzzx.message_app.activities

import android.annotation.SuppressLint
import android.os.Bundle
import android.widget.EditText
import android.widget.ImageView
import android.widget.Toast
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.navigation.NavigationView
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import huytoandzzx.message_app.R
import huytoandzzx.message_app.adapters.ChatAiAdapter
import huytoandzzx.message_app.models.BlenderBotRequest
import huytoandzzx.message_app.models.BlenderBotResponse
import huytoandzzx.message_app.models.ChatAiMessage
import huytoandzzx.message_app.services.BlenderBotApiService
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.text.SimpleDateFormat
import java.util.Locale
import huytoandzzx.message_app.utilities.Constants
import huytoandzzx.message_app.utilities.PreferenceManager

class BlenderBotAiActivity : BaseActivity() {

    private lateinit var rvChat: RecyclerView
    private lateinit var etMessage: EditText
    private lateinit var btnSend: ImageView
    private lateinit var btnBack: ImageView
    private lateinit var btnNewChat: ImageView
    private lateinit var btnHistory: ImageView
    private lateinit var chatAiAdapter: ChatAiAdapter
    private val messages = mutableListOf<ChatAiMessage>()
    private val db = FirebaseFirestore.getInstance()

    // Sử dụng PreferenceManager để lấy user id
    private lateinit var preferenceManager: PreferenceManager
    private var userId: String? = null

    private var currentChatId: String? = null // Lưu ID của cuộc trò chuyện hiện tại

    private lateinit var apiService: BlenderBotApiService
    private val authToken = "Bearer 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"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_blender_bot_ai)

        // Khởi tạo PreferenceManager và lấy userId từ Shared Preferences
        preferenceManager = PreferenceManager(applicationContext)
        userId = preferenceManager.getString(Constants.KEY_USER_ID)

        rvChat = findViewById(R.id.rvChat)
        etMessage = findViewById(R.id.etMessage)
        btnSend = findViewById(R.id.btnSend)
        btnBack = findViewById(R.id.btnBack)
        btnNewChat = findViewById(R.id.btnNewChat)
        btnHistory = findViewById(R.id.btnHistory)

        chatAiAdapter = ChatAiAdapter(messages)
        rvChat.layoutManager = LinearLayoutManager(this)
        rvChat.adapter = chatAiAdapter

        val retrofit = Retrofit.Builder()
            .baseUrl("https://api-inference.huggingface.co/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        apiService = retrofit.create(BlenderBotApiService::class.java)

        btnSend.setOnClickListener {
            val userMessage = etMessage.text.toString().trim()
            if (userMessage.isNotEmpty()) {
                addMessage(ChatAiMessage(userMessage, true))
                sendMessage(userMessage)
                etMessage.text.clear()
            }
        }

        btnBack.setOnClickListener {
            finish()
        }

        btnNewChat.setOnClickListener {
            startNewChat()
        }

        btnHistory.setOnClickListener {
            val drawerLayout = findViewById<DrawerLayout>(R.id.drawerLayoutChaiAi)
            drawerLayout.openDrawer(GravityCompat.END) // Mở menu từ bên phải
            loadChatHistory() // Tải danh sách cuộc trò chuyện
        }
    }

    // LOAD LỊCH SỬ CHAT, chỉ tải chat của người dùng hiện tại
    private fun loadChatHistory() {
        val navigationView = findViewById<NavigationView>(R.id.navigationViewChatAi)
        val menu = navigationView.menu
        menu.clear() // Xóa danh sách cũ

        // Đảm bảo userId không null
        userId?.let { id ->
            db.collection("chat_with_ai")
                .whereEqualTo("userId", id)  // Lọc theo userId
                //.orderBy("timestamp", Query.Direction.DESCENDING)
                .get()
                .addOnSuccessListener { result ->
                    for (document in result) {
                        val chatId = document.id
                        val messagesList = document.get("messages") as? List<Map<String, Any>>

                        // Lấy tin nhắn đầu tiên của user
                        val firstUserMessage = messagesList?.firstOrNull { it["isUser"] as Boolean }
                        val title = firstUserMessage?.get("text") as? String ?: "Không có tin nhắn"

                        // Lấy timestamp và chuyển thành chuỗi ngày giờ
                        val timestamp = document.getTimestamp("timestamp")?.toDate()
                        val formattedDate = if (timestamp != null) {
                            SimpleDateFormat("dd/MM HH:mm", Locale.getDefault()).format(timestamp)
                        } else {
                            "Không rõ thời gian"
                        }

                        val menuItem = menu.add("$formattedDate - $title")
                        menuItem.setOnMenuItemClickListener {
                            loadChatMessages(chatId) // Tải tin nhắn cũ
                            true
                        }
                    }
                }
        }
    }

    // TOUCH MỞ LẠI ĐOẠN CHAT TRONG HISTORY
    @SuppressLint("NotifyDataSetChanged")
    private fun loadChatMessages(chatId: String) {
        val drawerLayout = findViewById<DrawerLayout>(R.id.drawerLayoutChaiAi)

        // Trước khi mở cuộc trò chuyện khác, lưu lại cuộc trò chuyện hiện tại
        saveChatToFirestore()

        db.collection("chat_with_ai").document(chatId).get()
            .addOnSuccessListener { document ->
                val messagesList = document.get("messages") as? List<Map<String, Any>>
                messages.clear()

                messagesList?.forEach {
                    val text = it["text"] as String
                    val isUser = it["isUser"] as Boolean
                    messages.add(ChatAiMessage(text, isUser))
                }

                chatAiAdapter.notifyDataSetChanged()
                rvChat.scrollToPosition(messages.size - 1)

                // Đóng Drawer sau khi tải tin nhắn
                drawerLayout.closeDrawer(GravityCompat.END)

                // Cập nhật ID của cuộc trò chuyện hiện tại
                currentChatId = chatId
            }
    }

    private fun addMessage(message: ChatAiMessage) {
        messages.add(message)
        chatAiAdapter.notifyItemInserted(messages.size - 1)
        rvChat.scrollToPosition(messages.size - 1)
    }

    // CHAT VỚI AI
    private fun sendMessage(message: String) {
        val request = BlenderBotRequest(
            inputs = message,
            parameters = mapOf(
                "max_new_tokens" to 50,
                "temperature" to 0.7
            )
        )

        val loadingMessage = ChatAiMessage("Responsing...", false)
        messages.add(loadingMessage)
        val loadingIndex = messages.size - 1
        chatAiAdapter.notifyItemInserted(loadingIndex)
        rvChat.scrollToPosition(loadingIndex)

        apiService.chat(authToken, request).enqueue(object : Callback<List<BlenderBotResponse>> {
            override fun onResponse(
                call: Call<List<BlenderBotResponse>>,
                response: Response<List<BlenderBotResponse>>
            ) {
                if (response.isSuccessful) {
                    val botResponses = response.body()
                    if (!botResponses.isNullOrEmpty()) {
                        val botReply = botResponses[0].generated_text ?: "Không có phản hồi."
                        messages[loadingIndex] = ChatAiMessage(botReply, false)
                    } else {
                        Toast.makeText(this@BlenderBotAiActivity, "Bot không có phản hồi", Toast.LENGTH_LONG).show()
                    }
                } else {
                    Toast.makeText(this@BlenderBotAiActivity, "Lỗi API: ${response.errorBody()?.string()}", Toast.LENGTH_LONG).show()
                }
                chatAiAdapter.notifyItemChanged(loadingIndex)
            }

            override fun onFailure(call: Call<List<BlenderBotResponse>>, t: Throwable) {
                Toast.makeText(this@BlenderBotAiActivity, "Lỗi kết nối: ${t.message}", Toast.LENGTH_LONG).show()
                chatAiAdapter.notifyItemChanged(loadingIndex)
            }
        })
    }

    // LƯU ĐOẠN CHAT VÀO FIREBASE, thêm userId để phân biệt người dùng
    private fun saveChatToFirestore() {
        if (messages.isEmpty()) return

        val messageList = messages.map {
            mapOf("text" to it.text, "isUser" to it.isUser)
        }

        if (currentChatId != null) {
            // Chỉ cập nhật danh sách tin nhắn, giữ nguyên timestamp
            db.collection("chat_with_ai").document(currentChatId!!)
                .update("messages", messageList)
                .addOnFailureListener {
                    Toast.makeText(this, "Lưu thất bại: ${it.message}", Toast.LENGTH_SHORT).show()
                }
        } else {
            // Nếu là cuộc trò chuyện mới, tạo mới với timestamp và userId
            val chatData = hashMapOf(
                "userId" to (userId ?: "anonymous"),
                "timestamp" to FieldValue.serverTimestamp(),
                "messages" to messageList
            )

            db.collection("chat_with_ai")
                .add(chatData)
                .addOnSuccessListener { documentRef ->
                    currentChatId = documentRef.id
                }
                .addOnFailureListener {
                    Toast.makeText(this, "Lưu thất bại: ${it.message}", Toast.LENGTH_SHORT).show()
                }
        }
    }

    // TẠO MỚI CHAT
    @SuppressLint("NotifyDataSetChanged")
    private fun startNewChat() {
        saveChatToFirestore()

        // Xóa tin nhắn cũ trên UI
        messages.clear()
        chatAiAdapter.notifyDataSetChanged()

        // Reset ID của cuộc trò chuyện hiện tại (bắt đầu cuộc trò chuyện mới)
        currentChatId = null

        Toast.makeText(this, "Bắt đầu cuộc trò chuyện mới", Toast.LENGTH_SHORT).show()
    }

    override fun onDestroy() {
        super.onDestroy()
        saveChatToFirestore() // Lưu khi app đóng
    }
}
