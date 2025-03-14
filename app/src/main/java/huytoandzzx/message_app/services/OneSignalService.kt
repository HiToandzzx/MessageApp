package huytoandzzx.message_app.services

import android.app.Application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

import com.onesignal.OneSignal
import com.onesignal.debug.LogLevel
import huytoandzzx.message_app.utilities.Constants

class OneSignalService : Application() {
    private val appId = Constants.ONESIGNAL_APP_ID
    override fun onCreate() {
        super.onCreate()

        // Verbose Logging set to help debug issues, remove before releasing your app.
        OneSignal.Debug.logLevel = LogLevel.VERBOSE

        // OneSignal Initialization
        OneSignal.initWithContext(this, appId)

        // Xử lý khi nhấn vào thông báo
        OneSignal.Notifications.addClickListener(NotificationConversionHandler(this))

        // requestPermission will show the native Android notification permission prompt.
        // NOTE: It's recommended to use a OneSignal In-App Message to prompt instead.
        CoroutineScope(Dispatchers.IO).launch {
            OneSignal.Notifications.requestPermission(false)
        }
    }
}

