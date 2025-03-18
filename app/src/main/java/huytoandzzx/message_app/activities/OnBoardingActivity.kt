package huytoandzzx.message_app.activities

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Base64
import android.widget.Button
import android.widget.ImageButton
import android.widget.Toast
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import huytoandzzx.message_app.R
import huytoandzzx.message_app.utilities.Constants
import huytoandzzx.message_app.utilities.PreferenceManager
import java.io.ByteArrayOutputStream
import java.net.URL

@Suppress("DEPRECATION")
class OnBoardingActivity : AppCompatActivity() {
    private lateinit var preferenceManager: PreferenceManager
    private lateinit var firebaseAuth: FirebaseAuth
    private lateinit var googleSignInClient: GoogleSignInClient

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        preferenceManager = PreferenceManager(applicationContext)

        // Kiểm tra nếu đã đăng nhập, chuyển thẳng sang MainActivity
        if (preferenceManager.getBoolean(Constants.KEY_IS_SIGNED_IN)) {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
            return
        }

        setContentView(R.layout.activity_on_boarding)

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
        val btnSignUp = findViewById<Button>(R.id.btnSignUp)
        val btnSignIn = findViewById<Button>(R.id.btnSignIn)
        val btnSignUpGoogle = findViewById<ImageButton>(R.id.btnSignUpGoogle)

        btnSignUp.setOnClickListener {
            startActivity(Intent(this, SignUpActivity::class.java))
        }

        btnSignIn.setOnClickListener {
            startActivity(Intent(this, SignInActivity::class.java))
        }

        btnSignUpGoogle.setOnClickListener {
            signInWithGoogle()
        }
    }

    // ------------------- Google Sign-In -------------------

    // Khởi tạo đăng nhập bằng Google
    private fun signInWithGoogle() {
        val signInIntent = googleSignInClient.signInIntent
        startActivityForResult(signInIntent, SignInActivity.RC_SIGN_IN)
    }

    // Xử lý kết quả đăng nhập Google
    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == SignInActivity.RC_SIGN_IN) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(data)
            try {
                val account = task.getResult(ApiException::class.java)
                firebaseAuthWithGoogle(account)
            } catch (e: ApiException) {
                showToast("Google sign in failed: ${e.message}")
            }
        }
    }

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

                    // Kiểm tra xem user đã tồn tại trong Firestore chưa
                    database.collection(Constants.KEY_COLLECTION_USERS)
                        .whereEqualTo(Constants.KEY_EMAIL, user?.email)
                        .get()
                        .addOnCompleteListener { queryTask ->
                            if (queryTask.isSuccessful) {
                                if (queryTask.result != null && queryTask.result!!.documents.size > 0) {
                                    // Nếu user đã tồn tại -> lấy dữ liệu từ Firestore
                                    val documentSnapshot = queryTask.result!!.documents[0]
                                    preferenceManager.putBoolean(Constants.KEY_IS_SIGNED_IN, true)
                                    preferenceManager.putString(Constants.KEY_USER_ID, documentSnapshot.id)
                                    preferenceManager.putString(Constants.KEY_NAME, documentSnapshot.getString(Constants.KEY_NAME) ?: "")
                                    preferenceManager.putString(Constants.KEY_IMAGE, documentSnapshot.getString(Constants.KEY_IMAGE) ?: "")
                                    preferenceManager.putString(Constants.KEY_EMAIL, documentSnapshot.getString(Constants.KEY_EMAIL) ?: "")
                                    startMainActivity()
                                } else {
                                    // Nếu user chưa tồn tại -> tạo mới user trong Firestore
                                    val userId = user?.uid ?: ""
                                    val userName = user?.displayName ?: ""
                                    val userEmail = user?.email ?: ""

                                    // Xử lý ảnh đại diện nếu có
                                    if (user?.photoUrl != null) {
                                        Thread {
                                            try {
                                                // Tải ảnh từ URL
                                                val inputStream = URL(user.photoUrl.toString()).openStream()
                                                val bitmap = BitmapFactory.decodeStream(inputStream)
                                                inputStream.close()

                                                // Mã hóa ảnh thành Base64
                                                val base64Image = encodeImage(bitmap)

                                                // Tạo user map để lưu vào Firestore
                                                val userMap = hashMapOf<String, Any>(
                                                    Constants.KEY_NAME to userName,
                                                    Constants.KEY_EMAIL to userEmail,
                                                    Constants.KEY_IMAGE to base64Image
                                                )

                                                // Lưu user mới vào Firestore
                                                database.collection(Constants.KEY_COLLECTION_USERS)
                                                    .document(userId)
                                                    .set(userMap)
                                                    .addOnSuccessListener {
                                                        preferenceManager.putBoolean(Constants.KEY_IS_SIGNED_IN, true)
                                                        preferenceManager.putString(Constants.KEY_USER_ID, userId)
                                                        preferenceManager.putString(Constants.KEY_NAME, userName)
                                                        preferenceManager.putString(Constants.KEY_IMAGE, base64Image)
                                                        preferenceManager.putString(Constants.KEY_EMAIL, userEmail)
                                                        runOnUiThread { startMainActivity() }
                                                    }
                                                    .addOnFailureListener {
                                                        runOnUiThread { showToast("Failed to save user data") }
                                                    }
                                            } catch (e: Exception) {
                                                e.printStackTrace()
                                                runOnUiThread { showToast("Failed to process profile image") }
                                            }
                                        }.start()
                                    } else {
                                        // Trường hợp user không có ảnh đại diện
                                        val userMap = hashMapOf<String, Any>(
                                            Constants.KEY_NAME to userName,
                                            Constants.KEY_EMAIL to userEmail,
                                            Constants.KEY_IMAGE to ""
                                        )

                                        database.collection(Constants.KEY_COLLECTION_USERS)
                                            .document(userId)
                                            .set(userMap)
                                            .addOnSuccessListener {
                                                preferenceManager.putBoolean(Constants.KEY_IS_SIGNED_IN, true)
                                                preferenceManager.putString(Constants.KEY_USER_ID, userId)
                                                preferenceManager.putString(Constants.KEY_NAME, userName)
                                                preferenceManager.putString(Constants.KEY_IMAGE, "")
                                                preferenceManager.putString(Constants.KEY_EMAIL, userEmail)
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

    private fun encodeImage(bitmap: Bitmap): String {
        val previewWidth = 40
        val previewHeight = bitmap.height * previewWidth / bitmap.width
        val previewBitmap = Bitmap.createScaledBitmap(bitmap, previewWidth, previewHeight, false)
        val byteArrayOutputStream = ByteArrayOutputStream()
        previewBitmap.compress(Bitmap.CompressFormat.JPEG, 50, byteArrayOutputStream)
        val bytes = byteArrayOutputStream.toByteArray()
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }

    // ------------------- End Google Sign-In -------------------

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}
