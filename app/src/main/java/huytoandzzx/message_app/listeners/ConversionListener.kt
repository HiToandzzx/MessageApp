package huytoandzzx.message_app.listeners

import huytoandzzx.message_app.models.User

interface ConversionListener{
    fun onConversionClicked(user: User?)
}