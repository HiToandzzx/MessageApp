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
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
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
import huytoandzzx.message_app.utilities.Constants
import huytoandzzx.message_app.utilities.PreferenceManager
import java.io.ByteArrayOutputStream
import java.util.Date

@Suppress("DEPRECATION", "NAME_SHADOWING")
class ChatActivity : BaseActivity() {
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
    }

    private fun init() {
        chatMessages = mutableListOf()
        chatAdapter = ChatAdapter(
            chatMessages,
            receiverUser.image?.let { getBitmapFromEncodedString(it) },
            preferenceManager.getString(Constants.KEY_USER_ID) ?: "",
            reactionListener = { chatMessage, reaction ->
                // Ví dụ: cập nhật trường reaction của tin nhắn lên Firestore
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

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == IMAGE_PICK_REQUEST_CODE && resultCode == RESULT_OK && data != null) {
            val imageUri = data.data
            try {
                val bitmap = MediaStore.Images.Media.getBitmap(contentResolver, imageUri)
                val encodedImage = "IMG:" + encodeImage(bitmap)

                // Tạo tin nhắn gửi hình ảnh. Ở đây, chúng ta lưu encodedImage vào trường message.
                // Nếu bạn muốn phân biệt giữa tin nhắn văn bản và tin nhắn hình ảnh, có thể bổ sung thêm cờ hoặc kiểu tin nhắn.
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
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(this, "Failed to process image", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun encodeImage(bitmap: Bitmap): String {
        val baos = ByteArrayOutputStream()
        // Nén ảnh sang JPEG với chất lượng 100 (bạn có thể điều chỉnh chất lượng nếu cần)
        bitmap.compress(Bitmap.CompressFormat.JPEG, 100, baos)
        val bytes = baos.toByteArray()
        return Base64.encodeToString(bytes, Base64.DEFAULT)
    }

    // OPEN DIALOG CHAT OPTIONS
    @SuppressLint("MissingInflatedId")
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
        val dialogNickName = dialogView.findViewById<androidx.appcompat.widget.AppCompatImageView>(R.id.dialogNickName)
        val dialogTheme = dialogView.findViewById<androidx.appcompat.widget.AppCompatImageView>(R.id.dialogTheme)
        val dialogDelete = dialogView.findViewById<androidx.appcompat.widget.AppCompatImageView>(R.id.dialogDelete)

        // Xử lý sự kiện click cho nút NickName: hiển thị dialog thay đổi Nickname
        dialogNickName.setOnClickListener {
            val builder = AlertDialog.Builder(this)
            builder.setTitle("Change Nicknames")

            // Inflate layout tùy chỉnh cho dialog
            val dialogView = layoutInflater.inflate(R.layout.dialog_change_nicknames, null)
            // Nếu cần thiết lập padding lại bằng code (nếu không có sẵn trong XML)
            val paddingInDp = 16
            val scale = resources.displayMetrics.density
            val paddingInPx = (paddingInDp * scale + 0.5f).toInt()
            dialogView.setPadding(paddingInPx, paddingInPx, paddingInPx, paddingInPx)

            // Lấy tham chiếu tới 2 EditText
            val senderNicknameInput = dialogView.findViewById<EditText>(R.id.editTextSenderNickname)
            val receiverNicknameInput = dialogView.findViewById<EditText>(R.id.editTextReceiverNickname)

            builder.setView(dialogView)
            builder.setPositiveButton("Save") { dialogInterface, _ ->
                val newSenderNickname = senderNicknameInput.text.toString().trim()
                val newReceiverNickname = receiverNicknameInput.text.toString().trim()

                if (newSenderNickname.isNotEmpty() || newReceiverNickname.isNotEmpty()) {
                    // Tạo map cập nhật với timestamp
                    val updateMap = hashMapOf<String, Any>(
                        Constants.KEY_TIMESTAMP to Date()
                    )
                    if (newSenderNickname.isNotEmpty()) {
                        updateMap[Constants.KEY_SENDER_NAME] = newSenderNickname
                    }
                    if (newReceiverNickname.isNotEmpty()) {
                        updateMap[Constants.KEY_RECEIVER_NAME] = newReceiverNickname
                    }

                    conversionId?.let { convId ->
                        database.collection(Constants.KEY_COLLECTION_CONVERSATIONS)
                            .document(convId)
                            .update(updateMap)
                            .addOnSuccessListener {
                                Toast.makeText(this, "Nicknames updated successfully", Toast.LENGTH_SHORT).show()
                                // Cập nhật giao diện: nếu current user là receiver, cập nhật receiverUser.name
                                if (newReceiverNickname.isNotEmpty()) {
                                    receiverUser.name = newReceiverNickname
                                }
                                // Nếu cần cập nhật giao diện cho sender (trường hợp hiển thị sender ở nơi khác), cập nhật tại đây.
                                loadReceiverDetails()
                            }
                            .addOnFailureListener {
                                Toast.makeText(this, "Failed to update nicknames", Toast.LENGTH_SHORT).show()
                            }
                    } ?: run {
                        // Nếu chưa có conversionId, cập nhật cục bộ (ví dụ đối với receiver)
                        if (newReceiverNickname.isNotEmpty()) {
                            receiverUser.name = newReceiverNickname
                            loadReceiverDetails()
                        }
                        Toast.makeText(this, "Nicknames updated locally", Toast.LENGTH_SHORT).show()
                    }
                }
                dialogInterface.dismiss()
            }
            builder.setNegativeButton("Cancel") { dialogInterface, _ ->
                dialogInterface.dismiss()
            }
            builder.show()
            // Giả sử "dialog" ở đây là dialog của Options đã được tạo sẵn, nếu cần đóng dialog Options:
            dialog.dismiss()
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

    // APPLY THEME
    private fun applyThemeColor(color: Int) {
        currentThemeColor = color
        binding.root.setBackgroundColor(color)
        binding.headerBackground.setBackgroundColor(color)
        chatAdapter.updateThemeColor(color)
    }

    // LƯU THEME
    private fun saveThemeColorToFirebase(color: Int) {
        // Kiểm tra conversionId có tồn tại hay không
        conversionId?.let { convId ->
            // Tạo map cập nhật, thêm trường KEY_THEME_COLOR (đã khai báo trong Constants) và cập nhật timestamp
            val updateMap = hashMapOf<String, Any>(
                Constants.KEY_THEME_COLOR to color,
                Constants.KEY_TIMESTAMP to Date()
            )
            database.collection(Constants.KEY_COLLECTION_CONVERSATIONS)
                .document(convId)
                .update(updateMap)
                .addOnSuccessListener {
                    // Bạn có thể hiển thị thông báo thành công nếu cần
                }
                .addOnFailureListener { _ ->
                    // Xử lý lỗi nếu việc cập nhật thất bại
                }
        }
    }

    private fun loadThemeColor() {
        conversionId?.let { convId ->
            database.collection(Constants.KEY_COLLECTION_CONVERSATIONS)
                .document(convId)
                .get()
                .addOnSuccessListener { document ->
                    if (document.exists()) {
                        val themeColor = document.getLong(Constants.KEY_THEME_COLOR)
                        themeColor?.let {
                            applyThemeColor(it.toInt())
                        }
                    }
                }
        }
    }

    private fun sendMessage() {
        val messageText = binding.inputMessage.text.toString()
        val message = hashMapOf<String, Any>(
            Constants.KEY_SENDER_ID to (preferenceManager.getString(Constants.KEY_USER_ID) ?: ""),
            Constants.KEY_RECEIVER_ID to (receiverUser.id ?: ""),
            Constants.KEY_MESSAGE to messageText,
            Constants.KEY_TIMESTAMP to Date(),
            Constants.KEY_REACTION to ""
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
                //Constants.KEY_REACTION to ""  // nếu cần lưu reaction ở conversation
            )
            addConversion(conversion)
        }
        binding.inputMessage.setText("")
    }

    private fun addConversion(conversion: HashMap<String, Any>) {
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
                }
                receiverUser.token = value?.getString(Constants.KEY_FCM_TOKEN)

                if (isReceiverAvailable) {
                    binding.textAvailability.visibility = View.VISIBLE
                } else {
                    binding.textAvailability.visibility = View.GONE
                }
                receiverUser.token = value?.getString(Constants.KEY_FCM_TOKEN)
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
                        }
                        // Kiểm tra nếu tin nhắn là ảnh (có tiền tố "IMG:")
                        if (rawMessage.startsWith("IMG:")) {
                            chatMessage.isImage = true
                            // Lấy phần chuỗi Base64 sau "IMG:" và giải mã thành Bitmap
                            val base64Image = rawMessage.substring(4)
                            chatMessage.imageBitmap = getBitmapFromEncodedString(base64Image)
                            chatMessage.message = "" // hoặc "[Image]" nếu bạn muốn hiển thị text tạm thời
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
                        for (i in chatMessages.indices) {
                            val message = chatMessages[i]
                            if (message.senderId == modifiedSender &&
                                message.receiverId == modifiedReceiver &&
                                message.dateObject == modifiedTimestamp) {
                                // Cập nhật reaction
                                message.reaction = newReaction
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
                        // Có thể xử lý DocumentChange.Type.REMOVED nếu cần
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

    private fun getBitmapFromEncodedString(encodedImage: String): Bitmap {
        val bytes = Base64.decode(encodedImage, Base64.DEFAULT)
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
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
    }

    private fun checkForConversionRemotely(senderId: String, receiverId: String) {
        database.collection(Constants.KEY_COLLECTION_CONVERSATIONS)
            .whereEqualTo(Constants.KEY_SENDER_ID, senderId)
            .whereEqualTo(Constants.KEY_RECEIVER_ID, receiverId)
            .get()
            .addOnCompleteListener(conversionOnCompleteListener)
    }

    private val conversionOnCompleteListener = OnCompleteListener<QuerySnapshot> { task ->
        if (task.isSuccessful && task.result != null && (task.result?.documents?.size ?: 0) > 0) {
            val documentSnapshot = task.result?.documents?.get(0)
            conversionId = documentSnapshot?.id
            // Kiểm tra và áp dụng theme nếu đã lưu trước đó
            val themeColorLong = documentSnapshot?.getLong(Constants.KEY_THEME_COLOR)
            themeColorLong?.let {
                applyThemeColor(it.toInt())
            }
        }
    }

    private fun getReadableDateTime(date: Date): String {
        val dateFormat = java.text.SimpleDateFormat("hh:mm a", java.util.Locale.getDefault())
        return dateFormat.format(date)
    }

    override fun onResume() {
        super.onResume()
        listenAvailabilityOfReceiver()
        loadThemeColor()
    }
}
