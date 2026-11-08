package app.velardns

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import app.velardns.data.Prefs
import app.velardns.filter.BlockLog
import app.velardns.filter.BlockStats
import app.velardns.filter.BlocklistManager
import app.velardns.filter.BlocklistUpdater

class VelarApp : Application() {

    companion object {
        const val CHANNEL_ID = "velar_status"
    }

    override fun onCreate() {
        super.onCreate()
        Prefs.init(this)
        BlocklistManager.init(this)
        BlockStats.init(this)
        BlockLog.restoreCount(
            getSharedPreferences("velar", MODE_PRIVATE).getLong("blocked_total", 0L)
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, getString(R.string.notif_channel), NotificationManager.IMPORTANCE_LOW,
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
        // Proteção: agenda atualização a cada 24h e baixa se necessário.
        BlocklistUpdater.schedule(this)
        BlocklistUpdater.updateIfNeeded(this)
    }
}
