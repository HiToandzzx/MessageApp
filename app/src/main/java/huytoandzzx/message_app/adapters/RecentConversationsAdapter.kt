package huytoandzzx.message_app.adapters

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
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
            binding.imageProfile.setImageBitmap(getConversionImage(chatMessage.conversationImage))
            binding.tvUsername.text = chatMessage.conversationName
            binding.tvRecentMessage.text = chatMessage.message

            // Ẩn/hiện trạng thái online
            binding.textAvailability.visibility = View.GONE
            val userRef = FirebaseFirestore.getInstance().collection(Constants.KEY_COLLECTION_USERS)
                .document(chatMessage.conversationId)
            userRef.addSnapshotListener { document, _ ->
                if (document != null && document.exists()) {
                    val availability = document.getLong(Constants.KEY_AVAILABILITY)
                    binding.textAvailability.visibility = if (availability?.toInt() == 1) View.VISIBLE else View.GONE
                }
            }

            binding.root.setOnClickListener {
                val user = User().apply {
                    id = chatMessage.conversationId
                    name = chatMessage.conversationName
                    image = chatMessage.conversationImage
                }
                conversionListener.onConversionClicked(user)
            }
        }

        private fun getConversionImage(encodedImage: String): Bitmap {
            val bytes = Base64.decode(encodedImage, Base64.DEFAULT)
            return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    fun updateList(newList: List<ChatMessage>) {
        chatMessages = newList
        notifyDataSetChanged()
    }
}

