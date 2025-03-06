package huytoandzzx.message_app.activities

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.firestore.FirebaseFirestore
import huytoandzzx.message_app.databinding.ActivitySignInBinding
import huytoandzzx.message_app.utilities.Constants
import huytoandzzx.message_app.utilities.PreferenceManager
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException

class SignInActivity : AppCompatActivity() {
    private lateinit var binding: ActivitySignInBinding
    private lateinit var preferenceManager: PreferenceManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        preferenceManager = PreferenceManager(applicationContext)
        if (preferenceManager.getBoolean(Constants.KEY_IS_SIGNED_IN)) {
            val intent = Intent(applicationContext, MainActivity::class.java)
            startActivity(intent)
            finish()
        }

        binding = ActivitySignInBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setListeners()
    }

    private fun setListeners() {
        binding.btnCreateAcc.setOnClickListener {
            startActivity(Intent(applicationContext, SignUpActivity::class.java))
        }

        binding.btnSignIn.setOnClickListener {
            if (isValidSignInDetails()) {
                signIn()
            }
        }
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private fun signIn() {
        loading(true)
        val hashedPassword = hashPassword(binding.etPasswordSignIn.text.toString())
        val database = FirebaseFirestore.getInstance()
        database.collection(Constants.KEY_COLLECTION_USERS)
            .whereEqualTo(Constants.KEY_EMAIL, binding.etEmailSignIn.text.toString())
            .whereEqualTo(Constants.KEY_PASSWORD, hashedPassword)
            .get()
            .addOnCompleteListener { task ->
                loading(false)
                if (task.isSuccessful && task.result != null && task.result!!.documents.size > 0) {
                    val documentSnapshot = task.result!!.documents[0]
                    preferenceManager.putBoolean(Constants.KEY_IS_SIGNED_IN, true)
                    preferenceManager.putString(Constants.KEY_USER_ID, documentSnapshot.id)
                    preferenceManager.putString(Constants.KEY_NAME, documentSnapshot.getString(Constants.KEY_NAME) ?: "")
                    preferenceManager.putString(Constants.KEY_IMAGE, documentSnapshot.getString(Constants.KEY_IMAGE) ?: "")
                    preferenceManager.putString(Constants.KEY_EMAIL, documentSnapshot.getString(Constants.KEY_EMAIL) ?: "")

                    val intent = Intent(applicationContext, MainActivity::class.java)
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                    startActivity(intent)
                } else {
                    showToast("Invalid email or password")
                }
            }
    }

    // HASH PASSWORD
    private fun hashPassword(password: String): String {
        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            val hashBytes = digest.digest(password.toByteArray(StandardCharsets.UTF_8))
            val hexString = StringBuilder()
            for (b in hashBytes) {
                hexString.append(String.format("%02x", b))
            }
            hexString.toString()
        } catch (e: NoSuchAlgorithmException) {
            e.printStackTrace()
            ""
        }
    }

    private fun isValidSignInDetails(): Boolean {
        /*var isValid = true

        // Reset lỗi trước khi kiểm tra
        binding.tvErrorEmailSignIn.text = ""
        binding.tvErrorPasswordSignIn.text = ""

        binding.tvErrorEmailSignIn.visibility = View.GONE
        binding.tvErrorPasswordSignIn.visibility = View.GONE*/

        if (binding.etEmailSignIn.text.toString().trim().isEmpty()) {
            /*binding.tvErrorEmailSignIn.text = "Email is required"
            binding.tvErrorEmailSignIn.visibility = View.VISIBLE
            isValid = false*/
            showToast("Email is required")
            return false
        }else if (!android.util.Patterns.EMAIL_ADDRESS.matcher(binding.etEmailSignIn.text.toString()).matches()) {
            /*binding.tvErrorEmailSignIn.text = "Not a valid email"
            binding.tvErrorEmailSignIn.visibility = View.VISIBLE
            isValid = false*/
            showToast("Not a valid email")
            return false
        }
        if (binding.etPasswordSignIn.text.toString().trim().isEmpty()) {
            /*binding.tvErrorPasswordSignIn.text = "Password is required"
            binding.tvErrorPasswordSignIn.visibility = View.VISIBLE
            isValid = false*/
            showToast("Password is required")
            return false
        }
        return true
    }

    // PROGRESS LOADING
    private fun loading(isLoading: Boolean) {
        if (isLoading) {
            binding.btnSignIn.visibility = View.INVISIBLE
            binding.progressBarSignIn.visibility = View.VISIBLE
        } else {
            binding.btnSignIn.visibility = View.VISIBLE
            binding.progressBarSignIn.visibility = View.INVISIBLE
        }
    }
}
