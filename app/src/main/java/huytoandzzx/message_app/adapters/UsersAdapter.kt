package huytoandzzx.message_app.adapters

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import huytoandzzx.message_app.databinding.ItemContainerUserBinding
import huytoandzzx.message_app.listeners.UserListener
import huytoandzzx.message_app.models.User

class UsersAdapter(
    private val users: List<User>,
    private val userListener: UserListener
) : RecyclerView.Adapter<UsersAdapter.UserViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): UserViewHolder {
        val binding = ItemContainerUserBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return UserViewHolder(binding)
    }

    override fun onBindViewHolder(holder: UserViewHolder, position: Int) {
        holder.setUserData(users[position])
    }

    override fun getItemCount(): Int = users.size

    inner class UserViewHolder(private val binding: ItemContainerUserBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun setUserData(user: User) {
            binding.tvUsername.text = user.name
            binding.tvUserEmail.text = user.email
            binding.imageProfile.setImageBitmap(user.image?.let { getUserImage(it) })
            binding.root.setOnClickListener{ userListener.onUserClicked(user) }
        }
    }

    private fun getUserImage(encodedImage: String): Bitmap {
        val bytes: ByteArray = Base64.decode(encodedImage, Base64.DEFAULT)
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }
}
