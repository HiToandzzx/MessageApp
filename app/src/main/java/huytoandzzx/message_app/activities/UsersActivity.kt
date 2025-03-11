package huytoandzzx.message_app.activities

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import com.google.firebase.firestore.FirebaseFirestore
import huytoandzzx.message_app.adapters.UsersAdapter
import huytoandzzx.message_app.databinding.ActivityUsersBinding
import huytoandzzx.message_app.listeners.UserListener
import huytoandzzx.message_app.models.User
import huytoandzzx.message_app.utilities.Constants
import huytoandzzx.message_app.utilities.PreferenceManager
import java.util.Locale

class UsersActivity : BaseActivity(), UserListener {
    private lateinit var binding: ActivityUsersBinding
    private lateinit var preferenceManager: PreferenceManager
    // Biến lưu danh sách user đã tải
    private var usersList = ArrayList<User>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityUsersBinding.inflate(layoutInflater)
        setContentView(binding.root)
        preferenceManager = PreferenceManager(applicationContext)
        setListeners()
        getUsers()
        // Thêm TextWatcher cho tìm kiếm theo tên
        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {}
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                filterUsers(s.toString())
            }
        })
    }

    private fun setListeners() {
        binding.imageBack.setOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }
    }

    private fun getUsers() {
        loading(true)
        val database = FirebaseFirestore.getInstance()
        database.collection(Constants.KEY_COLLECTION_USERS)
            .get()
            .addOnCompleteListener { task ->
                loading(false)
                val currentUserId = preferenceManager.getString(Constants.KEY_USER_ID)
                if (task.isSuccessful && task.result != null) {
                    usersList.clear()
                    for (queryDocumentSnapshot in task.result!!) {
                        if (currentUserId == queryDocumentSnapshot.id) {
                            continue
                        }
                        val user = User().apply {
                            name = queryDocumentSnapshot.getString(Constants.KEY_NAME)
                            email = queryDocumentSnapshot.getString(Constants.KEY_EMAIL)
                            image = queryDocumentSnapshot.getString(Constants.KEY_IMAGE)
                            token = queryDocumentSnapshot.getString(Constants.KEY_ONESIGNAL_PLAYER_ID)
                            id = queryDocumentSnapshot.id
                        }
                        usersList.add(user)
                    }
                    if (usersList.isNotEmpty()) {
                        usersList.sortBy { it.name?.lowercase(Locale.ROOT) }
                        val usersAdapter = UsersAdapter(usersList, this)
                        binding.userRecyclerView.adapter = usersAdapter
                        binding.userRecyclerView.visibility = View.VISIBLE
                        binding.tvErrorMessage.visibility = View.GONE
                    } else {
                        showErrorMessage()
                    }
                } else {
                    showErrorMessage()
                }
            }
    }

    // Hàm lọc danh sách người dùng theo tên
    @SuppressLint("SetTextI18n")
    private fun filterUsers(query: String) {
        val filteredList = if (query.isEmpty()) {
            usersList
        } else {
            usersList.filter { user ->
                user.name?.contains(query, ignoreCase = true) == true
            }
        }
        if (filteredList.isNotEmpty()) {
            val usersAdapter = UsersAdapter(ArrayList(filteredList), this)
            binding.userRecyclerView.adapter = usersAdapter
            binding.userRecyclerView.visibility = View.VISIBLE
            binding.tvErrorMessage.visibility = View.GONE
        } else {
            binding.userRecyclerView.visibility = View.GONE
            binding.tvErrorMessage.text = "No user found"
            binding.tvErrorMessage.visibility = View.VISIBLE
        }
    }

    @SuppressLint("SetTextI18n")
    private fun showErrorMessage() {
        binding.tvErrorMessage.text = "No user available"
        binding.tvErrorMessage.visibility = View.VISIBLE
    }

    private fun loading(isLoading: Boolean) {
        if (isLoading) {
            binding.progressBarLoadUser.visibility = View.VISIBLE
        } else {
            binding.progressBarLoadUser.visibility = View.INVISIBLE
        }
    }

    override fun onUserClicked(user: User?) {
        val intent = Intent(applicationContext, ChatActivity::class.java)
        intent.putExtra(Constants.KEY_USER, user)
        startActivity(intent)
        finish()
    }
}