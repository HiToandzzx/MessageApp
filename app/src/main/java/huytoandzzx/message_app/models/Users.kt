package huytoandzzx.message_app.models

import java.io.Serializable

//Data class để tự động tạo getter, setter, toString(), equals(), hashCode().
data class User(
    var id: String? = null,
    var name: String? = null,
    var image: String? = null,
    var email: String? = null,
    var token: String? = null
) : Serializable
