package app.velardns.vpn

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import app.velardns.R
import app.velardns.VelarApp
import app.velardns.data.DohProvider
import app.velardns.data.Prefs
import app.velardns.filter.BlockLog
import app.velardns.filter.BlockStats
import app.velardns.dns.FailoverResolver
import app.velardns.dns.Packets
import app.velardns.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean
import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants
import android.system.StructPollfd

/**
 * VPN local que captura só o tráfego DNS.
 *
 * O túnel recebe apenas pacotes destinados ao IP virtual de DNS (e, com
 * "Forçar todo DNS" ligado, a DNS públicos conhecidos). Cada consulta é
 * enviada criptografada por HTTPS (DoH) e a resposta volta pelo túnel.
 * O restante da internet não passa pelo app.
 */
class VelarVpnService : VpnService() {

    companion object {
        const val ACTION_START = "app.velardns.action.START"
        const val ACTION_STOP = "app.velardns.action.STOP"
        const val ACTION_RELOAD = "app.velardns.action.RELOAD"

        private const val NOTIFICATION_ID = 7
        private const val VIRTUAL_ADDRESS = "10.111.222.2"
        const val VIRTUAL_DNS = "10.111.222.1"

        /** DNS públicos que alguns apps usam direto, ignorando o DNS do sistema. */
        private val PUBLIC_DNS = listOf(
            "8.8.8.8", "8.8.4.4", "1.1.1.1", "1.0.0.1", "9.9.9.9", "149.112.112.112",
            "208.67.222.222", "208.67.220.220", "94.140.14.14", "94.140.15.15",
        )

        fun start(context: Context) {
            val intent = Intent(context, VelarVpnService::class.java).setAction(ACTION_START)
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            context.startService(Intent(context, VelarVpnService::class.java).setAction(ACTION_STOP))
        }

        /** Troca de servidor sem derrubar o túnel. */
        fun reload(context: Context) {
            if (!VpnState.isOn) return
            context.startService(Intent(context, VelarVpnService::class.java).setAction(ACTION_RELOAD))
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val limiter = Semaphore(64)
    private var tunnel: ParcelFileDescriptor? = null
    private var readJob: Job? = null
    private var loopRunning: AtomicBoolean? = null
    private var resolver: FailoverResolver? = null
    private var startedAt = 0L

    override fun onCreate() {
        super.onCreate()
        Prefs.init(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return when (intent?.action) {
            ACTION_STOP -> {
                stopProtection(userRequested = true)
                START_NOT_STICKY
            }
            ACTION_RELOAD -> {
                val r = resolver
                if (r != null) r.reset(Prefs.current.selectedProvider) else startProtection()
                START_STICKY
            }
            // ACTION_START, reinício pelo sistema (intent nula) ou "VPN sempre ativa"
            else -> {
                startProtection()
                START_STICKY
            }
        }
    }

    private fun startProtection() {
        VpnState.status.value = VpnStatus.Starting
        val settings = Prefs.current
        val provider = settings.selectedProvider
        enterForeground(provider)
        closeTunnel()

        val builder = Builder()
            .setSession(getString(R.string.app_name))
            .addAddress(VIRTUAL_ADDRESS, 32)
            .addDnsServer(VIRTUAL_DNS)
            .addRoute(VIRTUAL_DNS, 32)
            .setMtu(16384)
            .setBlocking(true)
        if (settings.forceAll) PUBLIC_DNS.forEach { builder.addRoute(it, 32) }
        // O próprio app fica fora do túnel: é ele quem fala com o servidor DoH.
        try {
            builder.addDisallowedApplication(packageName)
        } catch (_: PackageManager.NameNotFoundException) {
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) builder.setMetered(false)

        val pfd = try {
            builder.establish()
        } catch (e: Exception) {
            null
        }
        if (pfd == null) {
            fail("Não foi possível ativar. Abra o app e aceite a permissão de VPN.")
            return
        }

        startedAt = System.currentTimeMillis()
        val r = FailoverResolver(
            selected = provider,
            onProviderChanged = { p ->
                VpnState.status.value = VpnStatus.On(p, startedAt)
                updateNotification(p)
            },
            onLatency = VpnState::recordLatency,
        )
        tunnel = pfd
        resolver = r
        VpnState.latencyMs.value = null
        VpnState.status.value = VpnStatus.On(provider, startedAt)
        Prefs.update { it.copy(wasEnabled = true) }
        val running = AtomicBoolean(true)
        loopRunning = running
        readJob = scope.launch { readLoop(pfd, r, running) }
    }

    /**
     * Lê pacotes do túnel. Cada laço é dono do seu descritor e o fecha ao sair,
     * assim um túnel novo nunca reaproveita o descritor de um laço antigo.
     */
    private fun readLoop(pfd: ParcelFileDescriptor, r: FailoverResolver, running: AtomicBoolean) {
        val input = FileInputStream(pfd.fileDescriptor)
        val output = FileOutputStream(pfd.fileDescriptor)
        val buffer = ByteArray(32767)
        val pollFds = arrayOf(StructPollfd().apply {
            fd = pfd.fileDescriptor
            events = OsConstants.POLLIN.toShort()
        })
        val errorMask = OsConstants.POLLERR or OsConstants.POLLHUP or OsConstants.POLLNVAL
        try {
            while (running.get()) {
                val ready = try {
                    Os.poll(pollFds, 250)
                } catch (e: ErrnoException) {
                    if (e.errno == OsConstants.EINTR) continue else break
                }
                if (!running.get()) break
                if (ready == 0) continue
                if (pollFds[0].revents.toInt() and errorMask != 0) break
                val n = try {
                    input.read(buffer)
                } catch (_: IOException) {
                    break
                }
                if (n < 0) break
                if (n == 0) continue
                val query = Packets.parseDnsQuery(buffer, n) ?: continue
                scope.launch {
                    limiter.withPermit {
                        val answer = r.resolve(query.payload)
                        if (!running.get()) return@withPermit
                        val packet = Packets.buildDnsResponse(query, answer)
                        try {
                            synchronized(output) { output.write(packet) }
                        } catch (_: IOException) {
                        }
                    }
                }
            }
        } finally {
            running.set(false)
            try {
                pfd.close()
            } catch (_: IOException) {
            }
        }
    }

    private fun closeTunnel() {
        // O laço de leitura fecha o descritor sozinho em até 250 ms.
        loopRunning?.set(false)
        loopRunning = null
        readJob = null
        tunnel = null
        resolver = null
    }

    private fun stopProtection(userRequested: Boolean) {
        // Salvar contador de bloqueios antes de parar
        getSharedPreferences("velar", MODE_PRIVATE)
            .edit()
            .putLong("blocked_total", BlockLog.blockedCount.value)
            .apply()
        BlockStats.save()
        closeTunnel()
        VpnState.status.value = VpnStatus.Off
        VpnState.latencyMs.value = null
        if (userRequested) Prefs.update { it.copy(wasEnabled = false) }
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun fail(message: String) {
        closeTunnel()
        VpnState.status.value = VpnStatus.Error(message)
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    /** Outra VPN assumiu ou o usuário desligou pelas configurações do Android. */
    override fun onRevoke() {
        stopProtection(userRequested = true)
    }

    override fun onDestroy() {
        closeTunnel()
        scope.cancel()
        if (VpnState.status.value !is VpnStatus.Error) VpnState.status.value = VpnStatus.Off
        super.onDestroy()
    }

    // ---- Notificação obrigatória do serviço em primeiro plano ----

    private fun buildNotification(provider: DohProvider): Notification {
        val flags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), flags)
        val stop = PendingIntent.getService(
            this, 1, Intent(this, VelarVpnService::class.java).setAction(ACTION_STOP), flags,
        )
        return NotificationCompat.Builder(this, VelarApp.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_velar)
            .setContentTitle("Velar DNS ativo")
            .setContentText(
                if (Prefs.current.protection) "DNS criptografado + proteção via ${provider.name}"
                else "DNS criptografado via ${provider.name}"
            )
            .setOngoing(true)
            .setContentIntent(open)
            .addAction(0, "Desligar", stop)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    private fun enterForeground(provider: DohProvider) {
        val notification = buildNotification(provider)
        if (Build.VERSION.SDK_INT >= 34) {
            ServiceCompat.startForeground(
                this, NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun updateNotification(provider: DohProvider) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIFICATION_ID, buildNotification(provider))
    }
}
