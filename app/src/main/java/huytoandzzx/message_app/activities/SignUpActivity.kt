package huytoandzzx.message_app.activities

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.provider.MediaStore
import android.util.Base64
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import com.google.firebase.firestore.FirebaseFirestore
import huytoandzzx.message_app.databinding.ActivitySignUpBinding
import huytoandzzx.message_app.utilities.Constants
import huytoandzzx.message_app.utilities.PreferenceManager
import java.io.ByteArrayOutputStream
import java.io.FileNotFoundException
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException

class SignUpActivity : AppCompatActivity() {
    private lateinit var binding: ActivitySignUpBinding
    private lateinit var preferenceManager: PreferenceManager
    private lateinit var encodedImage: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySignUpBinding.inflate(layoutInflater)
        setContentView(binding.root)
        preferenceManager = PreferenceManager(applicationContext)
        setListeners()
    }

    private fun setListeners() {
        binding.btnBackToSignIn.setOnClickListener {
            startActivity(Intent(applicationContext, SignInActivity::class.java))
        }

        binding.btnSignUp.setOnClickListener {
            if (isValidSignUpDetails()) {
                isExistEmail()
            }
        }

        binding.layoutImage.setOnClickListener {
            val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            pickImage.launch(intent)
        }
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    // CHECK EMAIL EXISTS
    private fun isExistEmail() {
        loading(true)
        val database = FirebaseFirestore.getInstance()
        val email = binding.etEmailSignUp.text.toString().trim()

        // Kiểm tra email đã tồn tại chưa
        database.collection(Constants.KEY_COLLECTION_USERS)
            .whereEqualTo(Constants.KEY_EMAIL, email)
            .get()
            .addOnSuccessListener { documents ->
                if (!documents.isEmpty) {
                    // Email đã tồn tại
                    loading(false)
                    showToast("Email already exists!")
                } else {
                    // Tiếp tục đăng ký
                    signUp(database)
                }
            }
            .addOnFailureListener { exception ->
                loading(false)
                showToast("Error: ${exception.message}")
            }
    }

    // HASH PASSWORD
    private fun hashPassword(password: String): String {
        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            val hashBytes = digest.digest(password.toByteArray(Charsets.UTF_8))
            hashBytes.joinToString("") { "%02x".format(it) }
        } catch (e: NoSuchAlgorithmException) {
            e.printStackTrace()
            ""
        }
    }

    // SIGN UP
    private fun signUp(database: FirebaseFirestore) {
        val encodedPassword = hashPassword(binding.etPasswordSignUp.text.toString())

        val user = hashMapOf(
            Constants.KEY_NAME to binding.etUserNameSignUp.text.toString(),
            Constants.KEY_EMAIL to binding.etEmailSignUp.text.toString(),
            Constants.KEY_PASSWORD to encodedPassword, // Hashed password
            Constants.KEY_IMAGE to encodedImage
        )

        database.collection(Constants.KEY_COLLECTION_USERS)
            .add(user)
            .addOnSuccessListener { documentReference ->
                loading(false)
                preferenceManager.putBoolean(Constants.KEY_IS_SIGNED_IN, true)
                preferenceManager.putString(Constants.KEY_USER_ID, documentReference.id)
                preferenceManager.putString(Constants.KEY_NAME, binding.etUserNameSignUp.text.toString())
                preferenceManager.putString(Constants.KEY_IMAGE, encodedImage)
                preferenceManager.putString(Constants.KEY_EMAIL, binding.etEmailSignUp.text.toString())

                val intent = Intent(applicationContext, MainActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intent)
            }
            .addOnFailureListener { exception ->
                loading(false)
                showToast("Sign-up failed: ${exception.message}")
            }
    }

    private fun encodeImage(bitmap: Bitmap): String {
        val previewWidth = 40
        val previewHeight = bitmap.height * previewWidth / bitmap.width
        val previewBitmap = Bitmap.createScaledBitmap(bitmap, previewWidth, previewHeight, false)
        val byteArrayOutputStream = ByteArrayOutputStream()
        previewBitmap.compress(Bitmap.CompressFormat.JPEG, 50, byteArrayOutputStream)
        val bytes = byteArrayOutputStream.toByteArray()
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }

    // PICK IMAGE FOR USER
    private val pickImage = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            result.data?.data?.let { imageUri ->
                try {
                    val inputStream = contentResolver.openInputStream(imageUri)
                    val bitmap = BitmapFactory.decodeStream(inputStream)
                    binding.myImage.setImageBitmap(bitmap)
                    binding.tvAddImage.visibility = View.GONE
                    encodedImage = encodeImage(bitmap)
                } catch (e: FileNotFoundException) {
                    e.printStackTrace()
                }
            }
        }
    }

    private fun isValidSignUpDetails(): Boolean {
        if (!::encodedImage.isInitialized || encodedImage.isEmpty()) {
            showToast("Profile image is required")
            return false
        }
        if (binding.etUserNameSignUp.text.toString().trim().isEmpty()) {
            showToast("Username is required")
            return false
        }
        if (binding.etEmailSignUp.text.toString().trim().isEmpty()) {
            showToast("Email is required")
            return false
        } else if (!android.util.Patterns.EMAIL_ADDRESS.matcher(binding.etEmailSignUp.text.toString()).matches()) {
            showToast("Not a valid email")
            return false
        }
        if (binding.etPasswordSignUp.text.toString().trim().isEmpty()) {
            showToast("Password is required")
            return false
        }
        if (binding.etConfirmPasswordSignUp.text.toString().trim().isEmpty()) {
            showToast("Confirm your password")
            return false
        } else if (binding.etPasswordSignUp.text.toString() != binding.etConfirmPasswordSignUp.text.toString()) {
            showToast("Passwords do not match")
            return false
        }
        return true
    }

    // PROGRESS LOADING
    private fun loading(isLoading: Boolean) {
        if (isLoading) {
            binding.btnSignUp.visibility = View.INVISIBLE
            binding.progressBarSignUp.visibility = View.VISIBLE
        } else {
            binding.btnSignUp.visibility = View.VISIBLE
            binding.progressBarSignUp.visibility = View.INVISIBLE
        }
    }
}
