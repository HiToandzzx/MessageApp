package huytoandzzx.message_app.activities

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.provider.MediaStore
import android.util.Base64
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import com.google.firebase.firestore.FirebaseFirestore
import huytoandzzx.message_app.databinding.ActivityProfileBinding
import huytoandzzx.message_app.utilities.Constants
import huytoandzzx.message_app.utilities.PreferenceManager
import java.io.ByteArrayOutputStream
import java.io.FileNotFoundException
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException

class ProfileActivity : BaseActivity() {
    private lateinit var binding: ActivityProfileBinding
    private lateinit var preferenceManager: PreferenceManager
    // encodedImage sẽ lưu giá trị mới (nếu người dùng chọn ảnh) hoặc giữ giá trị cũ
    private var encodedImage: String = ""
    private var userDocumentId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProfileBinding.inflate(layoutInflater)
        setContentView(binding.root)
        preferenceManager = PreferenceManager(applicationContext)

        loadUserDetails()

        binding.btnBack.setOnClickListener{
            finish()
        }

        binding.btnUpdateProfile.setOnClickListener{
            if (isValidUpdateDetails()) {
                updateProfile()
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

    // Load thông tin người dùng từ PreferenceManager
    private fun loadUserDetails() {
        binding.etUserNameProfile.setText(preferenceManager.getString(Constants.KEY_NAME))
        binding.etEmailProfile.setText(preferenceManager.getString(Constants.KEY_EMAIL))

        val image = preferenceManager.getString(Constants.KEY_IMAGE)
        val decodedBytes = Base64.decode(image, Base64.DEFAULT)
        val bitmap = BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
        binding.myImage.setImageBitmap(bitmap)

        userDocumentId = preferenceManager.getString(Constants.KEY_USER_ID)
    }

    // Kiểm tra dữ liệu nhập vào cho cập nhật profile
    private fun isValidUpdateDetails(): Boolean {
        val emailInput = binding.etEmailProfile.text.toString().trim()
        if (emailInput.isNotEmpty() && !android.util.Patterns.EMAIL_ADDRESS.matcher(emailInput).matches()) {
            showToast("Not a valid email")
            return false
        }

        val currentPassword = binding.etCurrentPassword.text.toString().trim()
        val newPassword = binding.etNewPassword.text.toString().trim()
        val confirmNewPassword = binding.etConfirmNewPassword.text.toString().trim()

        // Lấy mật khẩu đã lưu (nếu có). Tài khoản Google thường không có mật khẩu lưu sẵn.
        val storedHashedPassword = preferenceManager.getString(Constants.KEY_PASSWORD)

        if (storedHashedPassword.isNullOrEmpty()) {
            // Tài khoản Google (không có mật khẩu)
            if (currentPassword.isNotEmpty() || newPassword.isNotEmpty() || confirmNewPassword.isNotEmpty()) {
                // Nếu người dùng muốn cập nhật mật khẩu, chỉ cần nhập newPassword và confirmNewPassword
                if (newPassword.isEmpty()) {
                    showToast("New password is required")
                    return false
                }
                if (confirmNewPassword.isEmpty()) {
                    showToast("Confirm new password is required")
                    return false
                }
                if (newPassword != confirmNewPassword) {
                    showToast("New passwords do not match")
                    return false
                }
            }
        } else {
            // Tài khoản có mật khẩu (đăng ký email)
            if (currentPassword.isNotEmpty() || newPassword.isNotEmpty() || confirmNewPassword.isNotEmpty()) {
                if (currentPassword.isEmpty()) {
                    showToast("Current password is required")
                    return false
                }
                if (newPassword.isEmpty()) {
                    showToast("New password is required")
                    return false
                }
                if (confirmNewPassword.isEmpty()) {
                    showToast("Confirm new password is required")
                    return false
                }
                if (newPassword != confirmNewPassword) {
                    showToast("New passwords do not match")
                    return false
                }
                if (hashPassword(currentPassword) != storedHashedPassword) {
                    showToast("Current password is incorrect")
                    return false
                }
            }
        }
        return true
    }

    // Hàm băm mật khẩu sử dụng SHA-256
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

    private fun updateProfile() {
        loading(true)
        val database = FirebaseFirestore.getInstance()
        val userUpdates = mutableMapOf<String, Any>()

        val nameInput = binding.etUserNameProfile.text.toString().trim()
        val emailInput = binding.etEmailProfile.text.toString().trim()

        if (nameInput.isNotEmpty()) {
            userUpdates[Constants.KEY_NAME] = nameInput
        }
        if (emailInput.isNotEmpty()) {
            userUpdates[Constants.KEY_EMAIL] = emailInput
        }
        // Nếu người dùng đã chọn ảnh mới hoặc encodedImage vẫn có giá trị cũ
        if (encodedImage.isNotEmpty()) {
            userUpdates[Constants.KEY_IMAGE] = encodedImage
        }

        // Xử lý cập nhật mật khẩu nếu có nhập
        val newPassword = binding.etNewPassword.text.toString().trim()
        if (newPassword.isNotEmpty()) {
            userUpdates[Constants.KEY_PASSWORD] = hashPassword(newPassword)
        }

        // Nếu không có trường nào được cập nhật thì thông báo và thoát
        if (userUpdates.isEmpty()) {
            loading(false)
            showToast("No changes to update")
            return
        }

        if (userDocumentId != null) {
            database.collection(Constants.KEY_COLLECTION_USERS)
                .document(userDocumentId!!)
                .update(userUpdates)
                .addOnSuccessListener {
                    loading(false)
                    // Cập nhật lại PreferenceManager nếu có thay đổi
                    if (userUpdates.containsKey(Constants.KEY_NAME))
                        preferenceManager.putString(Constants.KEY_NAME, nameInput)
                    if (userUpdates.containsKey(Constants.KEY_EMAIL))
                        preferenceManager.putString(Constants.KEY_EMAIL, emailInput)
                    if (userUpdates.containsKey(Constants.KEY_IMAGE))
                        preferenceManager.putString(Constants.KEY_IMAGE, encodedImage)
                    if (userUpdates.containsKey(Constants.KEY_PASSWORD))
                        preferenceManager.putString(Constants.KEY_PASSWORD, hashPassword(newPassword))
                    showToast("Profile updated successfully")
                }
                .addOnFailureListener { exception ->
                    loading(false)
                    showToast("Update failed: ${exception.message}")
                }
        } else {
            loading(false)
            showToast("User not found")
        }
    }

    // Nén và mã hóa hình ảnh sang Base64
    private fun encodeImage(bitmap: Bitmap): String {
        val previewWidth = 40
        val previewHeight = bitmap.height * previewWidth / bitmap.width
        val previewBitmap = Bitmap.createScaledBitmap(bitmap, previewWidth, previewHeight, false)
        val byteArrayOutputStream = ByteArrayOutputStream()
        previewBitmap.compress(Bitmap.CompressFormat.JPEG, 50, byteArrayOutputStream)
        val bytes = byteArrayOutputStream.toByteArray()
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }

    // Callback khi chọn hình ảnh từ thư viện
    private val pickImage = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            result.data?.data?.let { imageUri ->
                try {
                    val inputStream = contentResolver.openInputStream(imageUri)
                    val bitmap = BitmapFactory.decodeStream(inputStream)
                    binding.myImage.setImageBitmap(bitmap)
                    binding.tvAddImage.visibility = View.GONE
                    // Cập nhật encodedImage với ảnh mới đã chọn
                    encodedImage = encodeImage(bitmap)
                } catch (e: FileNotFoundException) {
                    e.printStackTrace()
                }
            }
        }
    }

    // Hiển thị hoặc ẩn ProgressBar
    private fun loading(isLoading: Boolean) {
        if (isLoading) {
            binding.btnUpdateProfile.visibility = View.INVISIBLE
            binding.progressBarProfile.visibility = View.VISIBLE
        } else {
            binding.btnUpdateProfile.visibility = View.VISIBLE
            binding.progressBarProfile.visibility = View.INVISIBLE
        }
    }
}
