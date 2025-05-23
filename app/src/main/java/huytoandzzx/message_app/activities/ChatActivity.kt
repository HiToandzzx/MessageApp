package huytoandzzx.message_app.activities

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.app.Dialog
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.os.Bundle
import android.provider.MediaStore
import android.util.Base64
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.gms.tasks.OnCompleteListener
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.EventListener
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.QuerySnapshot
import huytoandzzx.message_app.R
import huytoandzzx.message_app.adapters.ChatAdapter
import huytoandzzx.message_app.databinding.ActivityChatBinding
import huytoandzzx.message_app.models.ChatMessage
import huytoandzzx.message_app.models.User
import huytoandzzx.message_app.services.OneSignalNotificationService
import huytoandzzx.message_app.utilities.Constants
import huytoandzzx.message_app.utilities.PreferenceManager
import java.io.ByteArrayOutputStream
import java.util.Date

@Suppress("DEPRECATION")
class ChatActivity : AppCompatActivity() {
    private lateinit var binding: ActivityChatBinding
    private lateinit var receiverUser: User
    private var chatMessages: MutableList<ChatMessage> = mutableListOf()
    private lateinit var chatAdapter: ChatAdapter
    private lateinit var database: FirebaseFirestore
    private lateinit var preferenceManager: PreferenceManager
    private var conversionId: String? = null
    private var isReceiverAvailable :Boolean = false
    private var currentThemeColor: Int = Color.parseColor("#20A090")

    companion object {
        private const val IMAGE_PICK_REQUEST_CODE = 1001
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityChatBinding.inflate(layoutInflater)
        setContentView(binding.root)

        preferenceManager = PreferenceManager(applicationContext)
        setListeners()
        loadReceiverDetails()
        init()
        listenMessages()
        listenThemeChange()

        receiverUser.id?.let { preferenceManager.putString(Constants.KEY_OPEN_CONVERSATION_ID, it) }
    }

    private fun init() {
        chatMessages = mutableListOf()
        chatAdapter = ChatAdapter(
            chatMessages,
            receiverUser.image?.let { getBitmapFromEncodedString(it) },
            preferenceManager.getString(Constants.KEY_USER_ID) ?: "",
            reactionListener = { chatMessage, reaction ->
                // cập nhật trường reaction của tin nhắn lên Firestore
                database.collection(Constants.KEY_COLLECTION_CHAT)
                    .whereEqualTo(Constants.KEY_SENDER_ID, chatMessage.senderId)
                    .whereEqualTo(Constants.KEY_TIMESTAMP, chatMessage.dateObject)
                    .get()
                    .addOnSuccessListener { querySnapshot ->
                        for (document in querySnapshot.documents) {
                            document.reference.update(Constants.KEY_REACTION, reaction)
                        }
                    }
            }
        )

        binding.chatRecyclerView.adapter = chatAdapter
        database = FirebaseFirestore.getInstance()
    }

    private fun setListeners(){
        binding.imageBack.setOnClickListener{
            onBackPressedDispatcher.onBackPressed()
        }

        binding.layoutSend.setOnClickListener{
            sendMessage()
        }

        binding.imgMore.setOnClickListener {
            showOptionsDialog()
        }

        binding.imgAttach.setOnClickListener {
            val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
            startActivityForResult(intent, IMAGE_PICK_REQUEST_CODE)
        }
    }

    private fun listenMessages() {
        database.collection(Constants.KEY_COLLECTION_CHAT)
            .whereEqualTo(Constants.KEY_SENDER_ID, preferenceManager.getString(Constants.KEY_USER_ID))
            .whereEqualTo(Constants.KEY_RECEIVER_ID, receiverUser.id)
            .addSnapshotListener(eventListener)

        database.collection(Constants.KEY_COLLECTION_CHAT)
            .whereEqualTo(Constants.KEY_SENDER_ID, receiverUser.id)
            .whereEqualTo(Constants.KEY_RECEIVER_ID, preferenceManager.getString(Constants.KEY_USER_ID))
            .addSnapshotListener(eventListener)
    }

    @SuppressLint("NotifyDataSetChanged")
    private val eventListener = EventListener<QuerySnapshot> { value, error ->
        if (error != null) {
            return@EventListener
        }

        if (value != null) {
            val count = chatMessages.size
            for (documentChange in value.documentChanges) {
                when (documentChange.type) {
                    DocumentChange.Type.ADDED -> {
                        val rawMessage = documentChange.document.getString(Constants.KEY_MESSAGE) ?: ""
                        val chatMessage = ChatMessage().apply {
                            senderId = documentChange.document.getString(Constants.KEY_SENDER_ID) ?: ""
                            receiverId = documentChange.document.getString(Constants.KEY_RECEIVER_ID) ?: ""
                            dateTime = getReadableDateTime(documentChange.document.getDate(Constants.KEY_TIMESTAMP) ?: Date())
                            dateObject = documentChange.document.getDate(Constants.KEY_TIMESTAMP) ?: Date()
                            reaction = documentChange.document.getString(Constants.KEY_REACTION) ?: ""
                            isRead = documentChange.document.getBoolean(Constants.KEY_IS_READ) ?: false
                        }
                        // Kiểm tra nếu tin nhắn là ảnh (có tiền tố "IMG:")
                        if (rawMessage.startsWith("IMG:")) {
                            chatMessage.isImage = true
                            // Lấy phần chuỗi Base64 sau "IMG:" và giải mã thành Bitmap
                            val base64Image = rawMessage.substring(4)
                            chatMessage.imageBitmap = getBitmapFromEncodedString(base64Image)
                            chatMessage.message = ""
                        } else {
                            chatMessage.isImage = false
                            chatMessage.message = rawMessage
                        }
                        chatMessages.add(chatMessage)
                    }
                    DocumentChange.Type.MODIFIED -> {
                        val modifiedSender = documentChange.document.getString(Constants.KEY_SENDER_ID) ?: ""
                        val modifiedReceiver = documentChange.document.getString(Constants.KEY_RECEIVER_ID) ?: ""
                        val modifiedTimestamp = documentChange.document.getDate(Constants.KEY_TIMESTAMP) ?: Date()
                        val newRawMessage = documentChange.document.getString(Constants.KEY_MESSAGE) ?: ""
                        val newReaction = documentChange.document.getString(Constants.KEY_REACTION) ?: ""
                        val newIsRead = documentChange.document.getBoolean(Constants.KEY_IS_READ) ?: false
                        for (i in chatMessages.indices) {
                            val message = chatMessages[i]
                            if (message.senderId == modifiedSender &&
                                message.receiverId == modifiedReceiver &&
                                message.dateObject == modifiedTimestamp) {
                                // Cập nhật reaction
                                message.reaction = newReaction
                                message.isRead = newIsRead
                                // Cập nhật nội dung tin nhắn
                                if (newRawMessage.startsWith("IMG:")) {
                                    message.isImage = true
                                    val base64Image = newRawMessage.substring(4)
                                    message.imageBitmap = getBitmapFromEncodedString(base64Image)
                                    message.message = ""
                                } else {
                                    message.isImage = false
                                    message.message = newRawMessage
                                    message.imageBitmap = null
                                }
                                chatAdapter.notifyItemChanged(i)
                                break
                            }
                        }
                    }
                    else -> {
                    }
                }
            }

            // Sắp xếp lại danh sách tin nhắn theo thời gian
            chatMessages.sortBy { it.dateObject }

            if (count == 0) {
                chatAdapter.notifyDataSetChanged()
            } else {
                chatAdapter.notifyItemRangeInserted(count, chatMessages.size - count)
                binding.chatRecyclerView.smoothScrollToPosition(chatMessages.size - 1)
            }
            binding.chatRecyclerView.visibility = View.VISIBLE
        }
        binding.progressBar.visibility = View.GONE

        if (conversionId == null){
            checkForConversion()
        }
    }

    private fun sendMessage() {
        val messageText = binding.inputMessage.text.toString()
        val message = hashMapOf<String, Any>(
            Constants.KEY_SENDER_ID to (preferenceManager.getString(Constants.KEY_USER_ID) ?: ""),
            Constants.KEY_RECEIVER_ID to (receiverUser.id ?: ""),
            Constants.KEY_MESSAGE to messageText,
            Constants.KEY_TIMESTAMP to Date(),
            Constants.KEY_REACTION to "",
            Constants.KEY_IS_READ to false
        )
        database.collection(Constants.KEY_COLLECTION_CHAT).add(message)

        if (conversionId != null) {
            updateConversion(messageText)
        } else {
            val conversion = hashMapOf<String, Any>(
                Constants.KEY_SENDER_ID to (preferenceManager.getString(Constants.KEY_USER_ID) ?: ""),
                Constants.KEY_SENDER_NAME to (preferenceManager.getString(Constants.KEY_NAME) ?: ""),
                Constants.KEY_SENDER_IMAGE to (preferenceManager.getString(Constants.KEY_IMAGE) ?: ""),
                Constants.KEY_RECEIVER_ID to (receiverUser.id ?: ""),
                Constants.KEY_RECEIVER_NAME to (receiverUser.name ?: ""),
                Constants.KEY_RECEIVER_IMAGE to (receiverUser.image ?: ""),
                Constants.KEY_LAST_MESSAGE to messageText,
                Constants.KEY_TIMESTAMP to Date(),
            )
            addConversion(conversion)
        }

        // Push background notification
        if (!isReceiverAvailable && conversionId != null) {
            database.collection(Constants.KEY_COLLECTION_CONVERSATIONS)
                .document(conversionId!!)
                .get()
                .addOnSuccessListener { document ->
                    val isMuted = if (preferenceManager.getString(Constants.KEY_USER_ID) == document.getString(Constants.KEY_SENDER_ID)) {
                        document.getBoolean(Constants.KEY_MUTE_RECEIVER) ?: false
                    } else {
                        document.getBoolean(Constants.KEY_MUTE_SENDER) ?: false
                    }

                    // Chỉ gửi thông báo nếu người nhận chưa bật mute
                    if (!isMuted) {
                        sendPushNotification(if (messageText.startsWith("IMG:")) "[Image]" else messageText)
                    }
                }
        }

        binding.inputMessage.setText("")
    }

    // Hàm gửi push notification qua OneSignal REST API
    private fun sendPushNotification(messageText: String) {
        val playerId = receiverUser.token ?: ""

        // Gọi phương thức của service
        OneSignalNotificationService.sendPushNotification(
            restApiKey = Constants.ONESIGNAL_REST_API_KEY,
            appId = Constants.ONESIGNAL_APP_ID,
            playerId = playerId,
            messageText = messageText,
            conversationId = preferenceManager.getString(Constants.KEY_USER_ID),
            conversationImage = preferenceManager.getString(Constants.KEY_IMAGE),
            conversationName = preferenceManager.getString(Constants.KEY_NAME),
        )
    }

    private fun markMessageAsRead() {
        database.collection(Constants.KEY_COLLECTION_CHAT)
            .whereEqualTo(Constants.KEY_RECEIVER_ID, preferenceManager.getString(Constants.KEY_USER_ID))
            .whereEqualTo(Constants.KEY_SENDER_ID, receiverUser.id)
            .get()
            .addOnSuccessListener { querySnapshot ->
                for (document in querySnapshot.documents) {
                    if (document.getBoolean(Constants.KEY_IS_READ) == false) {
                        document.reference.update(Constants.KEY_IS_READ, true)
                    }
                }
            }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == IMAGE_PICK_REQUEST_CODE && resultCode == RESULT_OK && data != null) {
            val imageUri = data.data
            try {
                val bitmap = MediaStore.Images.Media.getBitmap(contentResolver, imageUri)
                val encodedImage = "IMG:" + encodeImage(bitmap)

                val message = hashMapOf<String, Any>(
                    Constants.KEY_SENDER_ID to (preferenceManager.getString(Constants.KEY_USER_ID) ?: ""),
                    Constants.KEY_RECEIVER_ID to (receiverUser.id ?: ""),
                    Constants.KEY_MESSAGE to encodedImage,
                    Constants.KEY_TIMESTAMP to Date(),
                    Constants.KEY_REACTION to ""
                )

                database.collection(Constants.KEY_COLLECTION_CHAT).add(message)
                if (conversionId != null) {
                    updateConversion("[Image]")
                } else {
                    val conversion = hashMapOf<String, Any>(
                        Constants.KEY_SENDER_ID to (preferenceManager.getString(Constants.KEY_USER_ID) ?: ""),
                        Constants.KEY_SENDER_NAME to (preferenceManager.getString(Constants.KEY_NAME) ?: ""),
                        Constants.KEY_SENDER_IMAGE to (preferenceManager.getString(Constants.KEY_IMAGE) ?: ""),
                        Constants.KEY_RECEIVER_ID to (receiverUser.id ?: ""),
                        Constants.KEY_RECEIVER_NAME to (receiverUser.name ?: ""),
                        Constants.KEY_RECEIVER_IMAGE to (receiverUser.image ?: ""),
                        Constants.KEY_LAST_MESSAGE to "[Image]",
                        Constants.KEY_TIMESTAMP to Date()
                    )
                    addConversion(conversion)
                }

                // Push background notification
                if (!isReceiverAvailable) {
                    sendPushNotification("[Image]")
                }

            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(this, "Failed to process image", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // OPEN DIALOG CHAT OPTIONS
    @SuppressLint("MissingInflatedId", "SetTextI18n")
    private fun showOptionsDialog() {
        // Inflate layout cho dialog
        val dialogView = layoutInflater.inflate(R.layout.dialog_options, null)

        // Áp dụng màu theme cho container của dialog_options
        val dialogContainer = dialogView.findViewById<LinearLayout>(R.id.dialogContainer)
        dialogContainer.setBackgroundColor(currentThemeColor)

        val builder = AlertDialog.Builder(this)
        builder.setView(dialogView)
        val dialog = builder.create()
        dialog.show()

        dialog.window?.setLayout(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )

        // Lấy tham chiếu tới các view trong dialog
        val dialogTheme = dialogView.findViewById<androidx.appcompat.widget.AppCompatImageView>(R.id.dialogTheme)
        val dialogDelete = dialogView.findViewById<androidx.appcompat.widget.AppCompatImageView>(R.id.dialogDelete)
        val dialogNotification = dialogView.findViewById<androidx.appcompat.widget.AppCompatImageView>(R.id.dialogNotification)
        val dialogNotificationText = dialogView.findViewById<TextView>(R.id.dialogNotificationText)

        conversionId?.let { convId ->
            val currentUserId = preferenceManager.getString(Constants.KEY_USER_ID) ?: ""

            // Lấy chính xác giá trị senderId từ Firestore
            val docRef = database.collection(Constants.KEY_COLLECTION_CONVERSATIONS).document(convId)

            docRef.get().addOnSuccessListener { document ->
                if (document != null && document.exists()) {
                    val senderId = document.getString(Constants.KEY_SENDER_ID) ?: ""
                    val isCurrentUserSender = currentUserId == senderId

                    val updateField = if (isCurrentUserSender) {
                        Constants.KEY_MUTE_SENDER
                    } else {
                        Constants.KEY_MUTE_RECEIVER
                    }

                    // Lắng nghe thay đổi realtime cho document
                    docRef.addSnapshotListener { documentSnapshot, error ->
                        if (error != null) {
                            Toast.makeText(this, "Failed to load notification status", Toast.LENGTH_SHORT).show()
                            return@addSnapshotListener
                        }

                        if (documentSnapshot != null && documentSnapshot.exists()) {
                            val isMuted = documentSnapshot.getBoolean(updateField) ?: false

                            if (!isMuted) {
                                dialogNotification.setImageResource(R.drawable.ic_notification_off)
                                dialogNotificationText.text = "Mute"
                                binding.ivNotification.visibility = View.GONE
                            } else {
                                dialogNotification.setImageResource(R.drawable.ic_notifications)
                                dialogNotificationText.text = "Unmute"
                                binding.ivNotification.visibility = View.VISIBLE
                            }

                            dialogNotification.setOnClickListener {
                                val newMutedStatus = !isMuted
                                docRef.update(updateField, newMutedStatus)
                                    .addOnSuccessListener {
                                        if (newMutedStatus) {
                                            dialogNotification.setImageResource(R.drawable.ic_notification_off)
                                            dialogNotificationText.text = "Mute"
                                        } else {
                                            dialogNotification.setImageResource(R.drawable.ic_notifications)
                                            dialogNotificationText.text = "Unmute"
                                        }

                                        val status = if (newMutedStatus) "muted" else "unmuted"
                                        Toast.makeText(this, "Notifications $status", Toast.LENGTH_SHORT).show()
                                    }
                                    .addOnFailureListener {
                                        Toast.makeText(this, "Failed to update notification status", Toast.LENGTH_SHORT).show()
                                    }
                                dialog.dismiss()
                            }
                        }
                    }
                }
            }
        }

        // Xử lý sự kiện click cho nút Theme: hiện dialog chọn theme
        dialogTheme.setOnClickListener {
            showThemeSelectionDialog()
            dialog.dismiss()
        }

        // Xử lý sự kiện click cho nút Delete: hiện hộp thoại xác nhận xoá
        dialogDelete.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Delete Conversation")
                .setMessage("Are you sure delete this conversation?")
                .setPositiveButton("Yes") { confirmDialog, _ ->
                    conversionId?.let { convId ->
                        database.collection(Constants.KEY_COLLECTION_CONVERSATIONS)
                            .document(convId)
                            .delete()
                            .addOnSuccessListener {
                                // Sau khi xoá conversation, tiến hành xoá các tin nhắn liên quan
                                deleteChatMessages()
                            }
                            .addOnFailureListener {
                                Toast.makeText(this, "Delete conversation failed", Toast.LENGTH_SHORT).show()
                            }
                    } ?: run {
                        // Nếu không có conversionId, chỉ xoá các tin nhắn
                        deleteChatMessages()
                    }
                    confirmDialog.dismiss()
                }
                .setNegativeButton("Cancel") { confirmDialog, _ ->
                    confirmDialog.dismiss()
                }
                .create()
                .show()
            dialog.dismiss()
        }
    }

    // HIỂN THỊ DANH SÁCH THEME
    @SuppressLint("SetTextI18n")
    private fun showThemeSelectionDialog() {
        // Create a custom dialog
        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.dialog_theme)

        // Set dialog width to match parent
        dialog.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)

        // Find views in the dialog
        val titleTextView = dialog.findViewById<TextView>(R.id.textViewTitle)
        val doneButton = dialog.findViewById<Button>(R.id.buttonDone)

        // Set title text
        titleTextView.text = "Theme"
        doneButton.text = "Done"

        // Set up the color options with resource IDs
        val colorOptions = listOf(
            Triple("Bóng rầm", R.color.theme_shade, R.id.colorBongRam),
            Triple("Hoa hồng", R.color.theme_rose, R.id.colorHoaHong),
            Triple("Tím oải hương", R.color.theme_lavender, R.id.colorTimOaiHuong),
            Triple("Hoa tulip", R.color.theme_tulip, R.id.colorHoaTulip),
            Triple("Cỏ điện", R.color.theme_classic, R.id.colorCoDien),
            Triple("Táo", R.color.theme_apple, R.id.colorTao),
            Triple("Mật ong", R.color.theme_honey, R.id.colorMatOng),
            Triple("Kiwi", R.color.theme_kiwi, R.id.colorKiwi),
            Triple("Đại dương", R.color.theme_ocean, R.id.colorDaiDuong),
        )

        // Set up each color view
        for ((_, colorResId, viewId) in colorOptions) {
            val colorView = dialog.findViewById<View>(viewId)
            // Lấy màu từ resources
            val colorValue = ContextCompat.getColor(this, colorResId)

            // Add click listener to each color
            colorView.setOnClickListener {
                applyThemeColor(colorValue)
                saveThemeColorToFirebase(colorValue)
                dialog.dismiss()
            }
        }

        doneButton.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun listenThemeChange() {
        receiverUser.id?.let { userId ->
            database.collection(Constants.KEY_COLLECTION_CONVERSATIONS)
                .whereEqualTo(Constants.KEY_SENDER_ID, preferenceManager.getString(Constants.KEY_USER_ID))
                .whereEqualTo(Constants.KEY_RECEIVER_ID, userId)
                .addSnapshotListener { value, error ->
                    if (error != null) {
                        Log.e("ChatActivity", "Failed to listen for theme changes", error)
                        return@addSnapshotListener
                    }
                    value?.documents?.forEach { document ->
                        val themeColor = document.getLong(Constants.KEY_THEME_COLOR)
                        themeColor?.let { applyThemeColor(it.toInt()) }
                    }
                }
        }
    }

    // APPLY THEME
    private fun applyThemeColor(color: Int) {
        currentThemeColor = color
        binding.root.setBackgroundColor(color)
        binding.headerBackground.setBackgroundColor(color)
        val drawable = ContextCompat.getDrawable(this, R.drawable.ic_send)
        drawable?.setTint(color)
        binding.btnLayoutSend.setImageDrawable(drawable)
        chatAdapter.updateThemeColor(color)
    }

    // LƯU THEME
    private fun saveThemeColorToFirebase(newColor: Int) {
        val conversationId = conversionId ?: return
        val update = hashMapOf(Constants.KEY_THEME_COLOR to newColor)
        database.collection(Constants.KEY_COLLECTION_CONVERSATIONS)
            .document(conversationId)
            .update(update as Map<String, Any>)
            .addOnSuccessListener {
                Log.d("ChatActivity", "Theme color updated successfully")
            }
            .addOnFailureListener { e ->
                Log.e("ChatActivity", "Failed to update theme color", e)
            }
    }

    private fun loadThemeColor() {
        conversionId?.let { convId ->
            database.collection(Constants.KEY_COLLECTION_CONVERSATIONS)
                .document(convId)
                .addSnapshotListener { documentSnapshot, error ->
                    if (error != null) {
                        Log.e("ChatActivity", "Error loading theme color", error)
                        return@addSnapshotListener
                    }
                    if (documentSnapshot != null && documentSnapshot.exists()) {
                        val themeColor = documentSnapshot.getLong(Constants.KEY_THEME_COLOR)
                        themeColor?.let {
                            applyThemeColor(it.toInt())
                        }
                    }
                }
        }
    }

    private fun addConversion(conversion: HashMap<String, Any>) {
        conversion[Constants.KEY_MUTE_SENDER] = false
        conversion[Constants.KEY_MUTE_RECEIVER] = false

        database.collection(Constants.KEY_COLLECTION_CONVERSATIONS)
            .add(conversion)
            .addOnSuccessListener { documentReference ->
                conversionId = documentReference.id
            }
    }

    private fun updateConversion(message: String) {
        val documentReference = database.collection(Constants.KEY_COLLECTION_CONVERSATIONS).document(conversionId!!)
        documentReference.update(
            Constants.KEY_LAST_MESSAGE, message,
            Constants.KEY_TIMESTAMP, Date()
        )
    }

    // XOÁ ĐOẠN CHAT
    private fun deleteChatMessages() {
        val senderId = preferenceManager.getString(Constants.KEY_USER_ID) ?: ""
        val receiverId = receiverUser.id ?: ""

        // Lấy tin nhắn với sender là người dùng và receiver là receiverUser
        database.collection(Constants.KEY_COLLECTION_CHAT)
            .whereEqualTo(Constants.KEY_SENDER_ID, senderId)
            .whereEqualTo(Constants.KEY_RECEIVER_ID, receiverId)
            .get()
            .addOnSuccessListener { querySnapshot1 ->
                val batch = database.batch()
                for (document in querySnapshot1.documents) {
                    batch.delete(document.reference)
                }
                // Lấy tin nhắn với sender là receiverUser và receiver là người dùng
                database.collection(Constants.KEY_COLLECTION_CHAT)
                    .whereEqualTo(Constants.KEY_SENDER_ID, receiverId)
                    .whereEqualTo(Constants.KEY_RECEIVER_ID, senderId)
                    .get()
                    .addOnSuccessListener { querySnapshot2 ->
                        for (document in querySnapshot2.documents) {
                            batch.delete(document.reference)
                        }
                        // Commit batch xoá tất cả tin nhắn
                        batch.commit().addOnSuccessListener {
                            Toast.makeText(this, "Delete chat successfully", Toast.LENGTH_SHORT).show()
                            finish()
                        }.addOnFailureListener {
                            Toast.makeText(this, "Delete chat failed", Toast.LENGTH_SHORT).show()
                        }
                    }
                    .addOnFailureListener {
                        Toast.makeText(this, "ERROR", Toast.LENGTH_SHORT).show()
                    }
            }
            .addOnFailureListener {
                Toast.makeText(this, "ERROR", Toast.LENGTH_SHORT).show()
            }
    }

    private val conversionOnCompleteListener = OnCompleteListener<QuerySnapshot> { task ->
        if (task.isSuccessful && task.result != null && (task.result?.documents?.size ?: 0) > 0) {
            val documentSnapshot = task.result?.documents?.get(0)
            conversionId = documentSnapshot?.id

            val themeColorLong = documentSnapshot?.getLong(Constants.KEY_THEME_COLOR)
            themeColorLong?.let {
                applyThemeColor(it.toInt())
            }

            // Kiểm tra trạng thái mute
            val muteSender = documentSnapshot?.getBoolean(Constants.KEY_MUTE_SENDER) ?: false
            val muteReceiver = documentSnapshot?.getBoolean(Constants.KEY_MUTE_RECEIVER) ?: false
            Log.d("MUTE_STATUS", "Sender Muted: $muteSender, Receiver Muted: $muteReceiver")
        }
    }

    // CHECK USER CÓ ONLINE
    private fun listenAvailabilityOfReceiver() {
        database.collection(Constants.KEY_COLLECTION_USERS)
            .document(receiverUser.id!!)
            .addSnapshotListener { value, error ->
                if (error != null) {
                    return@addSnapshotListener
                }

                value?.let {
                    val availability = it.getLong(Constants.KEY_AVAILABILITY)?.toInt()
                    isReceiverAvailable = (availability == 1)
                    receiverUser.token = it.getString(Constants.KEY_ONESIGNAL_PLAYER_ID)
                }

                if (isReceiverAvailable) {
                    binding.imageStatus.visibility = View.VISIBLE
                    binding.tvStatus.visibility = View.VISIBLE
                } else {
                    binding.imageStatus.visibility = View.GONE
                    binding.tvStatus.visibility = View.GONE
                }
            }
    }

    private fun loadReceiverDetails() {
        // Khởi tạo receiverUser từ Intent
        receiverUser = intent.getSerializableExtra(Constants.KEY_USER) as User

        // Nếu có conversionId, lấy thông tin cập nhật từ Firestore
        if (conversionId != null) {
            database.collection(Constants.KEY_COLLECTION_CONVERSATIONS)
                .document(conversionId!!)
                .get()
                .addOnSuccessListener { document ->
                    if (document.exists()) {
                        val updatedName = document.getString(Constants.KEY_RECEIVER_NAME)
                        if (!updatedName.isNullOrEmpty()) {
                            receiverUser.name = updatedName
                        }
                    }
                    // Cập nhật giao diện với tên mới
                    binding.tvName.text = receiverUser.name
                }
                .addOnFailureListener {
                    binding.tvName.text = receiverUser.name
                }
        } else {
            binding.tvName.text = receiverUser.name
        }

        // Hiển thị hình ảnh
        receiverUser.image?.let {
            val bitmap = getBitmapFromEncodedString(it)
            binding.imageAvatar.setImageBitmap(bitmap)
        }
    }

    private fun checkForConversion() {
        if (chatMessages.isNotEmpty()) {
            checkForConversionRemotely(
                preferenceManager.getString(Constants.KEY_USER_ID) ?: "",
                receiverUser.id ?: ""
            )
        }
        checkForConversionRemotely(
            receiverUser.id ?: "",
            preferenceManager.getString(Constants.KEY_USER_ID) ?: ""
        )

        // Kiểm tra cả hai chiều sender và receiver
        database.collection(Constants.KEY_COLLECTION_CONVERSATIONS)
            .whereEqualTo(Constants.KEY_SENDER_ID, preferenceManager.getString(Constants.KEY_USER_ID))
            .whereEqualTo(Constants.KEY_RECEIVER_ID, receiverUser.id)
            .get()
            .addOnCompleteListener { task ->
                if (task.isSuccessful && task.result != null && task.result.documents.isNotEmpty()) {
                    val documentSnapshot = task.result.documents[0]
                    conversionId = documentSnapshot.id
                    checkNotificationStatus()
                } else {
                    database.collection(Constants.KEY_COLLECTION_CONVERSATIONS)
                        .whereEqualTo(Constants.KEY_SENDER_ID, receiverUser.id)
                        .whereEqualTo(Constants.KEY_RECEIVER_ID, preferenceManager.getString(Constants.KEY_USER_ID))
                        .get()
                        .addOnCompleteListener { reverseTask ->
                            if (reverseTask.isSuccessful && reverseTask.result != null && reverseTask.result.documents.isNotEmpty()) {
                                val reverseSnapshot = reverseTask.result.documents[0]
                                conversionId = reverseSnapshot.id
                                checkNotificationStatus()
                            }
                        }
                }
            }
    }

    private fun checkNotificationStatus() {
        conversionId?.let { convId ->
            val currentUserId = preferenceManager.getString(Constants.KEY_USER_ID) ?: ""
            val docRef = database.collection(Constants.KEY_COLLECTION_CONVERSATIONS).document(convId)

            docRef.addSnapshotListener { document, error ->
                if (error != null || document == null || !document.exists()) return@addSnapshotListener

                val senderId = document.getString(Constants.KEY_SENDER_ID) ?: ""
                val receiverId = document.getString(Constants.KEY_RECEIVER_ID) ?: ""

                // Kiểm tra đúng trạng thái mute cho cả hai phía
                val muteSender = document.getBoolean(Constants.KEY_MUTE_SENDER) ?: false
                val muteReceiver = document.getBoolean(Constants.KEY_MUTE_RECEIVER) ?: false

                val isMuted = when (currentUserId) {
                    senderId -> muteSender
                    receiverId -> muteReceiver
                    else -> false
                }

                if (isMuted) {
                    binding.ivNotification.visibility = View.VISIBLE
                } else {
                    binding.ivNotification.visibility = View.GONE
                }
            }
        }
    }

    private fun checkForConversionRemotely(senderId: String, receiverId: String) {
        database.collection(Constants.KEY_COLLECTION_CONVERSATIONS)
            .whereEqualTo(Constants.KEY_SENDER_ID, senderId)
            .whereEqualTo(Constants.KEY_RECEIVER_ID, receiverId)
            .get()
            .addOnCompleteListener(conversionOnCompleteListener)
    }

    private fun getReadableDateTime(date: Date): String {
        val dateFormat = java.text.SimpleDateFormat("hh:mm a", java.util.Locale.getDefault())
        return dateFormat.format(date)
    }

    private fun encodeImage(bitmap: Bitmap): String {
        val baos = ByteArrayOutputStream()
        // Nén ảnh sang JPEG với chất lượng 100
        bitmap.compress(Bitmap.CompressFormat.JPEG, 100, baos)
        val bytes = baos.toByteArray()
        return Base64.encodeToString(bytes, Base64.DEFAULT)
    }

    private fun getBitmapFromEncodedString(encodedImage: String): Bitmap {
        val bytes = Base64.decode(encodedImage, Base64.DEFAULT)
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }

    override fun onResume() {
        super.onResume()
        listenAvailabilityOfReceiver()
        loadThemeColor()
        markMessageAsRead()
    }

    override fun onDestroy() {
        super.onDestroy()
        // Xóa trạng thái cuộc trò chuyện đang mở khi thoát activity
        preferenceManager.putString(Constants.KEY_OPEN_CONVERSATION_ID, "")
    }
}
