package app.velardns.dns

import android.os.SystemClock
import app.velardns.data.DohProvider
import app.velardns.data.Prefs
import app.velardns.data.Providers
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Dns
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.net.InetAddress
import java.util.concurrent.TimeUnit
import app.velardns.filter.BlockLog
import app.velardns.filter.BlocklistManager
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.cancellation.CancellationException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Cliente DNS-over-HTTPS (RFC 8484, método POST). */
object Doh {

    /** Domínio de teste com resposta conhecida e estável. */
    const val TEST_DOMAIN = "one.one.one.one"
    val TEST_EXPECTED = setOf("1.1.1.1", "1.0.0.1")

    private val dnsMessageType = "application/dns-message".toMediaType()

    /** IPs fixos dos servidores conhecidos: não dependemos do DNS da rede para achá-los. */
    private val bootstrap: Map<String, List<InetAddress>> =
        Providers.builtIn.associate { p -> p.host to p.bootstrap.map { InetAddress.getByName(it) } }

    val client: OkHttpClient = OkHttpClient.Builder()
        .dns(object : Dns {
            override fun lookup(hostname: String): List<InetAddress> =
                bootstrap[hostname] ?: Dns.SYSTEM.lookup(hostname)
        })
        .protocols(listOf(Protocol.HTTP_2, Protocol.HTTP_1_1))
        .connectTimeout(4, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .callTimeout(7, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    suspend fun query(provider: DohProvider, message: ByteArray): ByteArray {
        val request = Request.Builder()
            .url(provider.url)
            .header("Accept", "application/dns-message")
            .post(message.toRequestBody(dnsMessageType))
            .build()
        return client.newCall(request).await()
    }

    data class Probe(val ms: Long, val ips: List<String>)

    /** Mede o tempo de resposta e devolve os IPs obtidos para [name]. */
    suspend fun probe(provider: DohProvider, name: String = TEST_DOMAIN): Probe {
        val start = SystemClock.elapsedRealtime()
        val response = query(provider, DnsMessage.buildQuery(name))
        return Probe(SystemClock.elapsedRealtime() - start, DnsMessage.parseA(response))
    }

    private suspend fun Call.await(): ByteArray = suspendCancellableCoroutine { cont ->
        cont.invokeOnCancellation { cancel() }
        enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (cont.isActive) cont.resumeWithException(e)
            }

            override fun onResponse(call: Call, response: Response) {
                val result = runCatching {
                    response.use { r ->
                        val body = r.body?.bytes()
                        if (!r.isSuccessful || body == null || body.size < 12) {
                            throw IOException("Resposta inválida do servidor (HTTP ${r.code})")
                        }
                        body
                    }
                }
                if (!cont.isActive) return
                result.fold(
                    onSuccess = { cont.resume(it) },
                    onFailure = { cont.resumeWithException(it) },
                )
            }
        })
    }
}

/**
 * Resolve consultas pelo servidor escolhido. Se ele falhar várias vezes
 * seguidas e a troca automática estiver ligada, passa para o próximo da lista.
 */
class FailoverResolver(
    selected: DohProvider,
    private val onProviderChanged: (DohProvider) -> Unit,
    private val onLatency: (Long) -> Unit,
) {
    @Volatile private var order: List<DohProvider> = Providers.failoverOrder(selected)
    @Volatile private var index = 0
    private val failures = AtomicInteger(0)

    val current: DohProvider get() = order[index]

    @Synchronized
    fun reset(selected: DohProvider) {
        order = Providers.failoverOrder(selected)
        index = 0
        failures.set(0)
        onProviderChanged(current)
    }

    suspend fun resolve(query: ByteArray): ByteArray {
        val id = DnsMessage.id(query)

        // Proteção: verifica se o domínio está numa lista de ameaças.
        if (Prefs.current.protection) {
            val domain = DnsMessage.extractDomain(query)
            if (domain != null && BlocklistManager.shouldBlock(domain)) {
                BlockLog.record(domain)
                return DnsMessage.withId(DnsMessage.blocked(query), id)
            }
        }

        val first = current
        return try {
            val start = SystemClock.elapsedRealtime()
            val response = Doh.query(first, query)
            failures.set(0)
            onLatency(SystemClock.elapsedRealtime() - start)
            DnsMessage.withId(response, id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (!Prefs.current.failover || order.size < 2) return DnsMessage.servFail(query)
            if (failures.incrementAndGet() >= 3) advanceFrom(first)
            // Tenta esta mesma consulta no próximo servidor da lista.
            val list = order
            val pos = list.indexOfFirst { it.id == first.id }.coerceAtLeast(0)
            val alternative = list[(pos + 1) % list.size]
            try {
                DnsMessage.withId(Doh.query(alternative, query), id)
            } catch (e2: CancellationException) {
                throw e2
            } catch (e2: Exception) {
                DnsMessage.servFail(query)
            }
        }
    }

    @Synchronized
    private fun advanceFrom(failed: DohProvider) {
        if (current.id != failed.id) return
        index = (index + 1) % order.size
        failures.set(0)
        onProviderChanged(current)
    }
}
