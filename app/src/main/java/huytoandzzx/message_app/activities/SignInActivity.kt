package huytoandzzx.message_app.activities

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.util.Base64
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.auth.api.signin.*
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import huytoandzzx.message_app.R
import huytoandzzx.message_app.databinding.ActivitySignInBinding
import huytoandzzx.message_app.utilities.Constants
import huytoandzzx.message_app.utilities.PreferenceManager
import java.io.ByteArrayOutputStream
import java.net.URL
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException

@Suppress("DEPRECATION")
class SignInActivity : AppCompatActivity() {
    private lateinit var binding: ActivitySignInBinding
    private lateinit var preferenceManager: PreferenceManager
    private lateinit var firebaseAuth: FirebaseAuth
    private lateinit var googleSignInClient: GoogleSignInClient

    companion object {
        private const val RC_SIGN_IN = 9002
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        preferenceManager = PreferenceManager(applicationContext)
        if (preferenceManager.getBoolean(Constants.KEY_IS_SIGNED_IN)) {
            startActivity(Intent(applicationContext, MainActivity::class.java))
            finish()
        }

        binding = ActivitySignInBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setListeners()

        // Khởi tạo FirebaseAuth và GoogleSignInClient
        firebaseAuth = FirebaseAuth.getInstance()
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id)) // Lấy từ Firebase Console
            .requestEmail()
            .build()
        googleSignInClient = GoogleSignIn.getClient(this, gso)
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

        // Sự kiện đăng nhập bằng Google
        binding.btnGoogle.setOnClickListener {
            signInWithGoogle()
        }
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    // Đăng nhập bằng email
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

    // ------------------- Google Sign-In -------------------

    // Khởi tạo đăng nhập bằng Google
    private fun signInWithGoogle() {
        val signInIntent = googleSignInClient.signInIntent
        startActivityForResult(signInIntent, RC_SIGN_IN)
    }

    // Xử lý kết quả đăng nhập Google
    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == RC_SIGN_IN) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(data)
            try {
                val account = task.getResult(ApiException::class.java)
                firebaseAuthWithGoogle(account)
            } catch (e: ApiException) {
                showToast("Google sign in failed: ${e.message}")
            }
        }
    }

    // Xác thực với Firebase sau khi đăng nhập Google
    private fun firebaseAuthWithGoogle(account: GoogleSignInAccount?) {
        val idToken = account?.idToken
        if (idToken == null) {
            showToast("ID Token is null")
            return
        }
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        firebaseAuth.signInWithCredential(credential)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    val user = firebaseAuth.currentUser
                    val database = FirebaseFirestore.getInstance()
                    // Kiểm tra xem user đã có trong Firestore hay chưa theo email
                    database.collection(Constants.KEY_COLLECTION_USERS)
                        .whereEqualTo(Constants.KEY_EMAIL, user?.email)
                        .get()
                        .addOnCompleteListener { queryTask ->
                            if (queryTask.isSuccessful) {
                                if (queryTask.result != null && queryTask.result!!.documents.size > 0) {
                                    // Nếu đã tồn tại, lấy dữ liệu user từ Firestore
                                    val documentSnapshot = queryTask.result!!.documents[0]
                                    preferenceManager.putBoolean(Constants.KEY_IS_SIGNED_IN, true)
                                    preferenceManager.putString(Constants.KEY_USER_ID, documentSnapshot.id)
                                    preferenceManager.putString(Constants.KEY_NAME, documentSnapshot.getString(Constants.KEY_NAME) ?: "")
                                    preferenceManager.putString(Constants.KEY_IMAGE, documentSnapshot.getString(Constants.KEY_IMAGE) ?: "")
                                    preferenceManager.putString(Constants.KEY_EMAIL, documentSnapshot.getString(Constants.KEY_EMAIL) ?: "")
                                    startMainActivity()
                                } else {
                                    // Nếu chưa tồn tại, tạo mới document cho user trong Firestore
                                    val userId = user?.uid ?: ""
                                    // Nếu user có photoUrl thì chuyển về base64 trước khi lưu
                                    if (user?.photoUrl != null) {
                                        convertImageUrlToBase64(user.photoUrl.toString()) { base64Image ->
                                            val userMap = hashMapOf<String, Any>(
                                                Constants.KEY_NAME to (user.displayName ?: ""),
                                                Constants.KEY_EMAIL to (user.email ?: ""),
                                                Constants.KEY_IMAGE to (base64Image ?: user.photoUrl.toString())
                                            )
                                            database.collection(Constants.KEY_COLLECTION_USERS)
                                                .document(userId)
                                                .set(userMap)
                                                .addOnSuccessListener {
                                                    preferenceManager.putBoolean(Constants.KEY_IS_SIGNED_IN, true)
                                                    preferenceManager.putString(Constants.KEY_USER_ID, userId)
                                                    preferenceManager.putString(Constants.KEY_NAME, user.displayName ?: "")
                                                    preferenceManager.putString(Constants.KEY_IMAGE, userMap[Constants.KEY_IMAGE] as String)
                                                    preferenceManager.putString(Constants.KEY_EMAIL, user.email ?: "")
                                                    startMainActivity()
                                                }
                                                .addOnFailureListener {
                                                    showToast("Failed to save user data")
                                                }
                                        }
                                    } else {
                                        // Nếu không có photoUrl
                                        val userMap = hashMapOf<String, Any>(
                                            Constants.KEY_NAME to (user?.displayName ?: ""),
                                            Constants.KEY_EMAIL to (user?.email ?: ""),
                                            Constants.KEY_IMAGE to ""
                                        )
                                        database.collection(Constants.KEY_COLLECTION_USERS)
                                            .document(userId)
                                            .set(userMap)
                                            .addOnSuccessListener {
                                                preferenceManager.putBoolean(Constants.KEY_IS_SIGNED_IN, true)
                                                preferenceManager.putString(Constants.KEY_USER_ID, userId)
                                                preferenceManager.putString(Constants.KEY_NAME, user?.displayName ?: "")
                                                preferenceManager.putString(Constants.KEY_IMAGE, userMap[Constants.KEY_IMAGE] as String)
                                                preferenceManager.putString(Constants.KEY_EMAIL, user?.email ?: "")
                                                startMainActivity()
                                            }
                                            .addOnFailureListener {
                                                showToast("Failed to save user data")
                                            }
                                    }
                                }
                            } else {
                                showToast("Failed to retrieve user data")
                            }
                        }
                } else {
                    showToast("Firebase Authentication failed.")
                }
            }
    }

    private fun startMainActivity() {
        val intent = Intent(applicationContext, MainActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        startActivity(intent)
    }

    // Hàm chuyển đổi ảnh từ URL sang chuỗi Base64
    private fun convertImageUrlToBase64(url: String, callback: (String?) -> Unit) {
        Thread {
            try {
                val connection = URL(url).openConnection()
                connection.connect()
                val inputStream = connection.getInputStream()
                val bitmap = BitmapFactory.decodeStream(inputStream)
                inputStream.close()
                val outputStream = ByteArrayOutputStream()
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
                val byteArray = outputStream.toByteArray()
                val base64String = Base64.encodeToString(byteArray, Base64.DEFAULT)
                runOnUiThread {
                    callback(base64String)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                runOnUiThread {
                    callback(null)
                }
            }
        }.start()
    }

    // ------------------- End Google Sign-In -------------------

    // Hàm băm mật khẩu (dành cho đăng nhập email)
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
        if (binding.etEmailSignIn.text.toString().trim().isEmpty()) {
            showToast("Email is required")
            return false
        } else if (!android.util.Patterns.EMAIL_ADDRESS.matcher(binding.etEmailSignIn.text.toString()).matches()) {
            showToast("Not a valid email")
            return false
        }
        if (binding.etPasswordSignIn.text.toString().trim().isEmpty()) {
            showToast("Password is required")
            return false
        }
        return true
    }

    // Hiển thị/ẩn progress loading
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
