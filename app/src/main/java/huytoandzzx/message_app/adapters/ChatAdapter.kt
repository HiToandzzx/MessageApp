package huytoandzzx.message_app.adapters

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
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
    private var themeColor: Int = Color.parseColor("#20A090"),
    private val reactionListener: (ChatMessage, String) -> Unit // callback khi reaction được chọn
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
            // Kiểm tra nếu tin nhắn là ảnh và có bitmap hợp lệ
            if (chatMessage.isImage && chatMessage.imageBitmap != null) {
                binding.ivSentImage.visibility = View.VISIBLE
                binding.tvMessage.visibility = View.GONE
                binding.ivSentImage.setImageBitmap(chatMessage.imageBitmap)
            } else {
                binding.ivSentImage.visibility = View.GONE
                binding.tvMessage.visibility = View.VISIBLE
                binding.tvMessage.text = chatMessage.message
            }
            binding.textDateTime.text = chatMessage.dateTime

            // Áp dụng theme cho background của tin nhắn gửi
            val backgroundDrawable = binding.tvMessage.background
            backgroundDrawable?.let {
                DrawableCompat.setTint(it, this@ChatAdapter.themeColor)
            }

            // Hiển thị reaction nếu có
            if (chatMessage.reaction.isNotEmpty()) {
                binding.tvReaction.visibility = View.VISIBLE
                binding.tvReaction.text = chatMessage.reaction
            } else {
                binding.tvReaction.visibility = View.GONE
            }

            // Long click vào tin nhắn để chọn reaction
            binding.tvMessage.setOnLongClickListener {
                showReactionDialog(binding.root.context, chatMessage)
                true
            }

            // Nhấn vào tvReaction để xoá reaction hiện tại
            binding.tvReaction.setOnClickListener {
                reactionListener(chatMessage, "") // Gửi empty string để xoá reaction
            }
        }
    }

    inner class ReceivedMessageViewHolder(private val binding: ItemContainerReceivedMessageBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun setData(chatMessage: ChatMessage, profileImage: Bitmap?) {
            // Kiểm tra nếu tin nhắn là ảnh và có bitmap hợp lệ
            if (chatMessage.isImage && chatMessage.imageBitmap != null) {
                binding.ivSentImage.visibility = View.VISIBLE
                binding.tvMessage.visibility = View.GONE
                binding.ivSentImage.setImageBitmap(chatMessage.imageBitmap)
            } else {
                binding.ivSentImage.visibility = View.GONE
                binding.tvMessage.visibility = View.VISIBLE
                binding.tvMessage.text = chatMessage.message
            }
            binding.textDateTime.text = chatMessage.dateTime

            // Hiển thị reaction nếu có
            if (chatMessage.reaction.isNotEmpty()) {
                binding.tvReaction.visibility = View.VISIBLE
                binding.tvReaction.text = chatMessage.reaction
            } else {
                binding.tvReaction.visibility = View.GONE
            }

            if (profileImage != null) {
                binding.imageProfile.setImageBitmap(profileImage)
            } else {
                binding.imageProfile.setImageResource(R.drawable.ic_default_profile)
            }

            // Long click vào tin nhắn để chọn reaction
            binding.tvMessage.setOnLongClickListener {
                showReactionDialog(binding.root.context, chatMessage)
                true
            }

            // Nhấn vào tvReaction để xoá reaction hiện tại
            binding.tvReaction.setOnClickListener {
                reactionListener(chatMessage, "")
            }
        }
    }

    private fun showReactionDialog(context: Context, chatMessage: ChatMessage) {
        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_reactions, null)
        val layoutReactions = dialogView.findViewById<LinearLayout>(R.id.layoutReactions)

        val builder = AlertDialog.Builder(context)
            .setView(dialogView)
        val alertDialog = builder.create()

        val reactions = arrayOf("👍", "❤️", "😂", "😮", "😢", "\uD83D\uDE21")

        for (reaction in reactions) {
            val textView = TextView(context).apply {
                text = reaction
                textSize = 24f
                setPadding(16, 16, 16, 16)
                val params = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                params.setMargins(16, 0, 16, 0)
                layoutParams = params

                setOnClickListener {
                    reactionListener(chatMessage, reaction)
                    alertDialog.dismiss()
                }
            }
            layoutReactions.addView(textView)
        }

        alertDialog.show()
    }
}

