package app.velardns.filter

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Atualiza as listas de bloqueio a cada 24 horas usando AlarmManager.
 * Também faz a primeira atualização quando as listas estão vazias.
 */
class BlocklistUpdater : BroadcastReceiver() {

    companion object {
        private const val TAG = "BlocklistUpdater"
        private const val ACTION = "app.velardns.action.UPDATE_BLOCKLIST"
        private const val INTERVAL_MS = 24 * 60 * 60 * 1000L // 24 horas
        private const val INITIAL_DELAY_MS = 10_000L          // 10 segundos após agendar

        /**
         * Agenda a atualização periódica. Seguro chamar várias vezes:
         * o PendingIntent é o mesmo, então o alarme anterior é substituído.
         */
        fun schedule(context: Context) {
            val intent = Intent(context, BlocklistUpdater::class.java).setAction(ACTION)
            val flags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            val pending = PendingIntent.getBroadcast(context, 0, intent, flags)
            val alarm = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            alarm.setInexactRepeating(
                AlarmManager.ELAPSED_REALTIME,
                SystemClock.elapsedRealtime() + INITIAL_DELAY_MS,
                INTERVAL_MS,
                pending,
            )
            Log.i(TAG, "Atualização automática agendada a cada 24h")
        }

        /** Executa a atualização imediata se as listas estão vazias ou velhas. */
        fun updateIfNeeded(context: Context) {
            val last = BlocklistManager.lastUpdateMillis(context)
            val age = System.currentTimeMillis() - last
            if (BlocklistManager.matcher.size == 0 || age > INTERVAL_MS) {
                CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                    try {
                        val result = BlocklistManager.update()
                        BlocklistManager.saveLastUpdate(context)
                        Log.i(TAG, "Atualização inicial: ${result.totalDomains} domínios")
                    } catch (e: Exception) {
                        Log.w(TAG, "Falha na atualização inicial", e)
                    }
                }
            }
        }
    }

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != ACTION) return
        Log.i(TAG, "Alarme disparado — atualizando listas")
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val result = BlocklistManager.update()
                BlocklistManager.saveLastUpdate(context)
                // Salvar o contador total
                context.getSharedPreferences("velar", Context.MODE_PRIVATE)
                    .edit()
                    .putLong("blocked_total", BlockLog.blockedCount.value)
                    .apply()
                Log.i(TAG, "Listas atualizadas: ${result.totalDomains} domínios")
            } catch (e: Exception) {
                Log.w(TAG, "Falha ao atualizar listas", e)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
