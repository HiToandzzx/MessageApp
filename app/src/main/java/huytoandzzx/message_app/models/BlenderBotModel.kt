package huytoandzzx.message_app.models

// Data class cho request gửi lên Inference API
data class BlenderBotRequest(
    val inputs: String,
    val parameters: Map<String, Any>? = null
)

// Data class cho response từ API
data class BlenderBotResponse(
    val generated_text: String?
)