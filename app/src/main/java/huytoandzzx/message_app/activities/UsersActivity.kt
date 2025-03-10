package huytoandzzx.message_app.activities

import android.content.Intent
import android.os.Bundle
import android.view.View
import com.google.firebase.firestore.FirebaseFirestore
import huytoandzzx.message_app.adapters.UsersAdapter
import huytoandzzx.message_app.databinding.ActivityUsersBinding
import huytoandzzx.message_app.listeners.UserListener
import huytoandzzx.message_app.models.User
import huytoandzzx.message_app.utilities.Constants
import huytoandzzx.message_app.utilities.PreferenceManager
import java.util.Locale

class UsersActivity : BaseActivity() , UserListener {
    private lateinit var binding: ActivityUsersBinding
    private lateinit var preferenceManager: PreferenceManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityUsersBinding.inflate(layoutInflater)
        setContentView(binding.root)
        preferenceManager = PreferenceManager(applicationContext)
        setListeners()
        getUsers()
    }

    private fun setListeners() {
        binding.imageBack.setOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }
    }

    private fun getUsers(){
        loading(true)
        val database = FirebaseFirestore.getInstance()
        database.collection(Constants.KEY_COLLECTION_USERS)
            .get()
            .addOnCompleteListener { task ->
                loading(false)
                val currentUserId = preferenceManager.getString(Constants.KEY_USER_ID)

                if (task.isSuccessful && task.result != null) {
                    val users = ArrayList<User>()

                    for (queryDocumentSnapshot in task.result!!) {
                        if (currentUserId == queryDocumentSnapshot.id) {
                            continue
                        }
                        val user = User().apply {
                            name = queryDocumentSnapshot.getString(Constants.KEY_NAME)
                            email = queryDocumentSnapshot.getString(Constants.KEY_EMAIL)
                            image = queryDocumentSnapshot.getString(Constants.KEY_IMAGE)
                            token = queryDocumentSnapshot.getString(Constants.KEY_FCM_TOKEN)
                            id = queryDocumentSnapshot.id
                        }
                        users.add(user)
                    }

                    if (users.isNotEmpty()) {
                        users.sortBy { it.name?.lowercase(Locale.ROOT) }

                        val usersAdapter = UsersAdapter(users, this)
                        binding.userRecyclerView.adapter = usersAdapter
                        binding.userRecyclerView.visibility = View.VISIBLE
                    } else {
                        showErrorMessage()
                    }
                } else {
                    showErrorMessage()
                }
            }
    }

    private fun showErrorMessage(){
        binding.tvErrorMessage.text = String.format("%s", "No user available")
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