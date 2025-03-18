package huytoandzzx.message_app.adapters

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.firestore.FirebaseFirestore
import huytoandzzx.message_app.databinding.ItemContainerRecentConversionBinding
import huytoandzzx.message_app.listeners.ConversionListener
import huytoandzzx.message_app.models.ChatMessage
import huytoandzzx.message_app.models.User
import huytoandzzx.message_app.utilities.Constants
import huytoandzzx.message_app.utilities.PreferenceManager

class RecentConversationsAdapter(
    private var chatMessages: List<ChatMessage>,
    private val conversionListener: ConversionListener,
) : RecyclerView.Adapter<RecentConversationsAdapter.ConversionViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ConversionViewHolder {
        val binding = ItemContainerRecentConversionBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ConversionViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ConversionViewHolder, position: Int) {
        holder.setData(chatMessages[position])
    }

    override fun getItemCount(): Int = chatMessages.size

    inner class ConversionViewHolder(private val binding: ItemContainerRecentConversionBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun setData(chatMessage: ChatMessage) {
            Log.d("RecentConversationsAdapter", "Conversation ID: ${chatMessage.conversationId}")

            binding.imageProfile.setImageBitmap(getConversionImage(chatMessage.conversationImage))
            binding.tvUsername.text = chatMessage.conversationName
            binding.tvRecentMessage.text = chatMessage.message

            // Lấy userId hiện tại từ PreferenceManager
            val currentUserId =
                PreferenceManager(binding.root.context).getString(Constants.KEY_USER_ID)

            // Kiểm tra tin nhắn chưa đọc
            checkUnreadMessages(chatMessage, currentUserId)

            // Ẩn/hiện trạng thái online
            binding.textAvailability.visibility = View.GONE
            val userRef = FirebaseFirestore.getInstance().collection(Constants.KEY_COLLECTION_USERS)
                .document(chatMessage.conversationId)
            userRef.addSnapshotListener { document, _ ->
                if (document != null && document.exists()) {
                    val availability = document.getLong(Constants.KEY_AVAILABILITY)
                    binding.textAvailability.visibility =
                        if (availability?.toInt() == 1) View.VISIBLE else View.GONE
                }
            }

            // Sự kiện click vào cuộc trò chuyện
            binding.root.setOnClickListener {
                Log.d(
                    "RecentConversationsAdapter",
                    "Clicked on conversation with: ${chatMessage.conversationName}, ID: ${chatMessage.conversationId}, Image: ${chatMessage.conversationImage}"
                )

                val user = User().apply {
                    id = chatMessage.conversationId
                    name = chatMessage.conversationName
                    image = chatMessage.conversationImage
                }
                conversionListener.onConversionClicked(user)

                markMessagesAsRead(chatMessage.conversationId, currentUserId)
                binding.unRead.visibility = View.GONE
                binding.tvRecentMessage.setTypeface(null, android.graphics.Typeface.NORMAL)
                binding.tvRecentMessage.setTextColor(android.graphics.Color.GRAY)
            }
        }

        private fun getConversionImage(encodedImage: String): Bitmap {
            val bytes = Base64.decode(encodedImage, Base64.DEFAULT)
            return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        }

        private fun checkUnreadMessages(chatMessage: ChatMessage, currentUserId: String?) {
            val chatRef = FirebaseFirestore.getInstance().collection(Constants.KEY_COLLECTION_CHAT)

            chatRef
                .whereEqualTo(Constants.KEY_SENDER_ID, chatMessage.conversationId)
                .whereEqualTo(Constants.KEY_RECEIVER_ID, currentUserId)
                .whereEqualTo(Constants.KEY_IS_READ, false)
                .addSnapshotListener { querySnapshot, error ->
                    if (error != null) {
                        Log.e("RecentConversationsAdapter", "Error checking unread messages", error)
                        return@addSnapshotListener
                    }

                    // Lấy trạng thái cuộc trò chuyện đang mở
                    val openConversationId = PreferenceManager(binding.root.context)
                        .getString(Constants.KEY_OPEN_CONVERSATION_ID)

                    if (querySnapshot != null && !querySnapshot.isEmpty) {
                        // Nếu cuộc trò chuyện đang mở -> tự động đánh dấu đã đọc
                        if (openConversationId == chatMessage.conversationId) {
                            markMessagesAsRead(chatMessage.conversationId, currentUserId)
                            binding.unRead.visibility = View.GONE
                            binding.tvRecentMessage.setTypeface(null, android.graphics.Typeface.NORMAL)
                            binding.tvRecentMessage.setTextColor(android.graphics.Color.GRAY)
                        } else {
                            // Hiển thị chấm đỏ và làm đậm tin nhắn nếu chưa đọc
                            binding.unRead.visibility = View.VISIBLE
                            binding.tvRecentMessage.setTypeface(null, android.graphics.Typeface.BOLD)
                            binding.tvRecentMessage.setTextColor(android.graphics.Color.BLACK)
                        }
                    } else {
                        // Không có tin nhắn chưa đọc
                        binding.unRead.visibility = View.GONE
                        binding.tvRecentMessage.setTypeface(null, android.graphics.Typeface.NORMAL)
                        binding.tvRecentMessage.setTextColor(android.graphics.Color.GRAY)
                    }
                }
        }

        // Đánh dấu tin nhắn là đã đọc
        private fun markMessagesAsRead(conversationId: String, currentUserId: String?) {
            val chatRef = FirebaseFirestore.getInstance().collection(Constants.KEY_COLLECTION_CHAT)

            // Lấy các tin nhắn chưa đọc từ người gửi hiện tại
            chatRef
                .whereEqualTo(Constants.KEY_SENDER_ID, conversationId)
                .whereEqualTo(Constants.KEY_RECEIVER_ID, currentUserId)
                .whereEqualTo(Constants.KEY_IS_READ, false)
                .get()
                .addOnSuccessListener { querySnapshot ->
                    for (document in querySnapshot.documents) {
                        document.reference.update(Constants.KEY_IS_READ, true)
                    }
                }
                .addOnFailureListener { e ->
                    Log.e("RecentConversationsAdapter", "Error marking messages as read", e)
                }
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    fun updateList(newList: List<ChatMessage>) {
        chatMessages = newList
        notifyDataSetChanged()
    }
}
