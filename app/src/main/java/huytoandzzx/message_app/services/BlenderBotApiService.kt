package huytoandzzx.message_app.services

import huytoandzzx.message_app.models.BlenderBotRequest
import huytoandzzx.message_app.models.BlenderBotResponse
import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

interface BlenderBotApiService {
    @POST("models/facebook/blenderbot-400M-distill")
    fun chat(
        @Header("Authorization") token: String,
        @Body request: BlenderBotRequest
    ): Call<List<BlenderBotResponse>>
}