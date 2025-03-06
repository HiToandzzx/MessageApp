package huytoandzzx.message_app.adapters

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.graphics.drawable.DrawableCompat
import androidx.recyclerview.widget.RecyclerView
import huytoandzzx.message_app.R
import huytoandzzx.message_app.databinding.ItemContainerReceivedMessageBinding
import huytoandzzx.message_app.databinding.ItemContainerSentMessageBinding
import huytoandzzx.message_app.models.ChatMessage

class ChatAdapter(
    private val chatMessages: List<ChatMessage>,
    private val receiverProfileImage: Bitmap?,
    private val senderId: String,
    private var themeColor: Int = Color.parseColor("#20A090")
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val VIEW_TYPE_SENT = 1
        private const val VIEW_TYPE_RECEIVED = 2
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return if (viewType == VIEW_TYPE_SENT) {
            val binding = ItemContainerSentMessageBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
            SentMessageViewHolder(binding)
        } else {
            val binding = ItemContainerReceivedMessageBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
            ReceivedMessageViewHolder(binding)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val chatMessage = chatMessages[position]
        if (holder is SentMessageViewHolder) {
            holder.setData(chatMessage)
        } else if (holder is ReceivedMessageViewHolder) {
            holder.setData(chatMessage, receiverProfileImage)
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    fun updateThemeColor(newColor: Int) {
        themeColor = newColor
        notifyDataSetChanged()
    }

    override fun getItemCount(): Int = chatMessages.size

    override fun getItemViewType(position: Int): Int {
        return if (chatMessages[position].senderId == senderId) VIEW_TYPE_SENT else VIEW_TYPE_RECEIVED
    }

    inner class SentMessageViewHolder(private val binding: ItemContainerSentMessageBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun setData(chatMessage: ChatMessage) {
            binding.tvMessage.text = chatMessage.message
            binding.textDateTime.text = chatMessage.dateTime

            // Lấy drawable nền của tvMessage (được định nghĩa từ bg_sent_message)
            val backgroundDrawable = binding.tvMessage.background
            // Thay đổi tint của drawable theo màu theme đã chọn từ adapter
            backgroundDrawable?.let {
                DrawableCompat.setTint(it, this@ChatAdapter.themeColor)
            }
        }
    }

    inner class ReceivedMessageViewHolder(private val binding: ItemContainerReceivedMessageBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun setData(chatMessage: ChatMessage, profileImage: Bitmap?) {
            binding.tvMessage.text = chatMessage.message
            binding.textDateTime.text = chatMessage.dateTime
            if (profileImage != null) {
                binding.imageProfile.setImageBitmap(profileImage)
            } else {
                binding.imageProfile.setImageResource(R.drawable.ic_default_profile)
            }
        }
    }
}

