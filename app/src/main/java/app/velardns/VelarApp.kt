package app.velardns

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import app.velardns.data.Prefs

class VelarApp : Application() {

    companion object {
        const val CHANNEL_ID = "velar_status"
    }

    override fun onCreate() {
        super.onCreate()
        Prefs.init(this)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, getString(R.string.notif_channel), NotificationManager.IMPORTANCE_LOW,
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }
}
