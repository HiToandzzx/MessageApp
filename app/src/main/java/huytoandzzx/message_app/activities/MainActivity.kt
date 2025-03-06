package huytoandzzx.message_app.activities

import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Base64
import android.view.View
import android.view.animation.LinearInterpolator
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.core.view.GravityCompat
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.EventListener
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.QuerySnapshot
import com.google.firebase.messaging.FirebaseMessaging
import huytoandzzx.message_app.R
import huytoandzzx.message_app.adapters.RecentConversationsAdapter
import huytoandzzx.message_app.databinding.ActivityMainBinding
import huytoandzzx.message_app.listeners.ConversionListener
import huytoandzzx.message_app.models.ChatMessage
import huytoandzzx.message_app.models.User
import huytoandzzx.message_app.utilities.Constants
import huytoandzzx.message_app.utilities.PreferenceManager
import java.util.Date

class MainActivity : BaseActivity(), ConversionListener {
    private lateinit var binding: ActivityMainBinding
    private lateinit var preferenceManager: PreferenceManager
    private var conversations: MutableList<ChatMessage> = mutableListOf()
    private lateinit var conversationsAdapter: RecentConversationsAdapter
    private lateinit var database : FirebaseFirestore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val chatAI = findViewById<ImageView>(R.id.chatAI)

        // Hiệu ứng xoay tròn
        val rotateAnimation = ObjectAnimator.ofFloat(chatAI, "rotation", 0f, 360f)
        rotateAnimation.duration = 2000
        rotateAnimation.repeatCount = ObjectAnimator.INFINITE
        rotateAnimation.interpolator = LinearInterpolator()
        rotateAnimation.start()

        preferenceManager = PreferenceManager(applicationContext)
        init()
        loadUserDetails()
        //loadNavUserDetails()
        getToken()
        setListeners()
        listenConversations()

        // Thêm TextWatcher cho EditText tìm kiếm
        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {}
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                filterConversations(s.toString())
            }
        })
    }

    private fun init() {
        conversations = ArrayList()
        conversationsAdapter = RecentConversationsAdapter(conversations, this)
        binding.conversationsRecyclerView.adapter = conversationsAdapter
        database = FirebaseFirestore.getInstance()
    }

    private fun setListeners() {
        //binding.imgSignOut.setOnClickListener { signOut() }

        binding.fabNewChat.setOnClickListener{
            startActivity(Intent(applicationContext, UsersActivity::class.java))
        }

        binding.imgProfile.setOnClickListener {
            loadNavUserDetails()
            binding.drawerLayoutMain.openDrawer(GravityCompat.START)
        }

        // Xử lý sự kiện khi nhấn vào các mục trong Navigation Drawer
        binding.navigationView.setNavigationItemSelectedListener { menuItem ->
            when (menuItem.itemId) {
                R.id.nav_profile -> {
                    startActivity(Intent(applicationContext, ProfileActivity::class.java))
                }
                R.id.nav_settings -> {
                    //startActivity(Intent(applicationContext, SettingsActivity::class.java))
                }
                R.id.nav_logout -> {
                    signOut()
                }
            }
            binding.drawerLayoutMain.closeDrawer(GravityCompat.START)
            true
        }

        binding.chatAI.setOnClickListener{
            startActivity(Intent(applicationContext, BlenderBotAiActivity::class.java))
        }
    }

    // Hàm tìm kiếm tên cuộc trò chuyện
    private fun filterConversations(query: String) {
        val filteredList = if (query.isEmpty()) {
            conversations
        } else {
            conversations.filter { chatMessage ->
                chatMessage.conversationName.contains(query, ignoreCase = true)
            }
        }
        conversationsAdapter.updateList(filteredList)
    }

    // Hàm hiển thị ảnh và tên user
    private fun loadUserDetails() {
        val userId = preferenceManager.getString(Constants.KEY_USER_ID)
        if (!userId.isNullOrEmpty()) {
            FirebaseFirestore.getInstance()
                .collection(Constants.KEY_COLLECTION_USERS)
                .document(userId)
                .get()
                .addOnSuccessListener { document ->
                    if (document != null && document.exists()) {
                        val name = document.getString(Constants.KEY_NAME) ?: ""
                        val encodedImage = document.getString(Constants.KEY_IMAGE) ?: ""

                        // Cập nhật giao diện
                        binding.tvUsername.text = name
                        if (encodedImage.isNotEmpty()) {
                            val bytes = Base64.decode(encodedImage, Base64.DEFAULT)
                            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                            binding.imgProfile.setImageBitmap(bitmap)
                        } else {
                            binding.imgProfile.setImageResource(R.drawable.ic_default_profile)
                        }

                        // (Tùy chọn) Cập nhật lại preferenceManager nếu cần
                        preferenceManager.putString(Constants.KEY_NAME, name)
                        preferenceManager.putString(Constants.KEY_IMAGE, encodedImage)
                    }
                }
                .addOnFailureListener { e ->
                    showToast("Lỗi khi tải thông tin người dùng: ${e.message}")
                }
        }
    }

    // Hàm hiển thị ảnh và tên user ở nav
    private fun loadNavUserDetails() {
        val headerView = binding.navigationView.getHeaderView(0)
        val navUsername = headerView.findViewById<TextView>(R.id.tvHeaderUsername)
        val navImage = headerView.findViewById<ImageView>(R.id.imgHeaderProfile)

        navUsername.text = preferenceManager.getString(Constants.KEY_NAME)

        val encodedImage = preferenceManager.getString(Constants.KEY_IMAGE)
        if (!encodedImage.isNullOrEmpty()) {
            val bytes = Base64.decode(encodedImage, Base64.DEFAULT)
            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            navImage.setImageBitmap(bitmap)
        } else {
            navImage.setImageResource(R.drawable.ic_default_profile)
        }
    }

    private fun listenConversations() {
        database.collection(Constants.KEY_COLLECTION_CONVERSATIONS)
            .whereEqualTo(Constants.KEY_SENDER_ID, preferenceManager.getString(Constants.KEY_USER_ID))
            .addSnapshotListener(eventListener)

        database.collection(Constants.KEY_COLLECTION_CONVERSATIONS)
            .whereEqualTo(Constants.KEY_RECEIVER_ID, preferenceManager.getString(Constants.KEY_USER_ID))
            .addSnapshotListener(eventListener)
    }

    // Lấy token khi SignIn
    private fun getToken() {
        FirebaseMessaging.getInstance().token
            .addOnSuccessListener { token -> updateToken(token) }
    }

    // UPDATE TOKEN
    private fun updateToken(token: String) {
        preferenceManager.putString(Constants.KEY_FCM_TOKEN, token)
        val database = FirebaseFirestore.getInstance()
        val documentReference = database.collection(Constants.KEY_COLLECTION_USERS)
            .document(preferenceManager.getString(Constants.KEY_USER_ID) ?: "")

        documentReference.update(Constants.KEY_FCM_TOKEN, token)
            .addOnFailureListener { showToast("Unable to update token") }
    }

    // SIGN OUT
    private fun signOut() {
        showToast("Signing out...")

        val database = FirebaseFirestore.getInstance()
        val documentReference = database.collection(Constants.KEY_COLLECTION_USERS)
            .document(preferenceManager.getString(Constants.KEY_USER_ID)!!)

        val updates = hashMapOf<String, Any>(
            Constants.KEY_FCM_TOKEN to FieldValue.delete()
        )

        documentReference.update(updates)
            .addOnSuccessListener {
                preferenceManager.clear()
                startActivity(Intent(applicationContext, OnBoardingActivity::class.java))
                finish()
            }
            .addOnFailureListener { showToast("Unable to sign out") }
    }

    @SuppressLint("NotifyDataSetChanged")
    private val eventListener = EventListener<QuerySnapshot> { value, error ->
        if (error != null) { return@EventListener }

        value?.documentChanges?.forEach { documentChange ->
            if (documentChange.type == DocumentChange.Type.ADDED) {
                val chatMessage = ChatMessage().apply {
                    senderId = documentChange.document.getString(Constants.KEY_SENDER_ID) ?: ""
                    receiverId = documentChange.document.getString(Constants.KEY_RECEIVER_ID) ?: ""
                    message = documentChange.document.getString(Constants.KEY_LAST_MESSAGE) ?: ""
                    dateObject = documentChange.document.getDate(Constants.KEY_TIMESTAMP) ?: Date()

                    if (preferenceManager.getString(Constants.KEY_USER_ID) == senderId) {
                        conversationImage = documentChange.document.getString(Constants.KEY_RECEIVER_IMAGE) ?: ""
                        conversationName = documentChange.document.getString(Constants.KEY_RECEIVER_NAME) ?: ""
                        conversationId = documentChange.document.getString(Constants.KEY_RECEIVER_ID) ?: ""
                    } else {
                        conversationImage = documentChange.document.getString(Constants.KEY_SENDER_IMAGE) ?: ""
                        conversationName = documentChange.document.getString(Constants.KEY_SENDER_NAME) ?: ""
                        conversationId = documentChange.document.getString(Constants.KEY_SENDER_ID) ?: ""
                    }
                }
                conversations.add(chatMessage)
            } else if (documentChange.type == DocumentChange.Type.MODIFIED){
                for (i in 0 until conversations.size) {
                    val senderId = documentChange.document.getString(Constants.KEY_SENDER_ID)
                    val receiverId = documentChange.document.getString(Constants.KEY_RECEIVER_ID)
                    if (conversations[i].senderId == senderId && conversations[i].receiverId == receiverId) {
                        conversations[i].message = documentChange.document.getString(Constants.KEY_LAST_MESSAGE)!!
                        conversations[i].dateObject = documentChange.document.getDate(Constants.KEY_TIMESTAMP)!!
                        break
                    }
                }
            }
        }

        conversations.sortByDescending { it.dateObject }
        conversationsAdapter.notifyDataSetChanged()
        binding.conversationsRecyclerView.smoothScrollToPosition(0)
        binding.conversationsRecyclerView.visibility = View.VISIBLE
        binding.progressBar.visibility = View.GONE
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    // TOUCH ON RECENT
    override fun onConversionClicked(user: User?) {
        val intent = Intent(applicationContext, ChatActivity::class.java)
        intent.putExtra(Constants.KEY_USER, user)
        startActivity(intent)
    }

    override fun onResume() {
        super.onResume()
        loadUserDetails()  // Load lại thông tin người dùng mỗi khi Activity được hiển thị
    }
}