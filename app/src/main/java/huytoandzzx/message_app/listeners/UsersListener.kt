package huytoandzzx.message_app.listeners

import huytoandzzx.message_app.models.User

interface UserListener {
    fun onUserClicked(user: User?)
}

