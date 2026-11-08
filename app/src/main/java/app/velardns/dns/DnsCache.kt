package app.velardns.dns

import android.os.SystemClock

/**
 * Cache de respostas DNS em memória. Respeita o TTL do servidor (limitado a
 * [MAX_TTL_SEC]) e guarda no máximo [MAX_ENTRIES] respostas; ao encher, descarta a menos usada.
 * Só entram respostas NOERROR completas; falhas e bloqueios nunca são guardados.
 */
object DnsCache {

    private const val MAX_ENTRIES = 1000
    private const val MIN_TTL_SEC = 5
    private const val MAX_TTL_SEC = 600

    private class Entry(val response: ByteArray, val storedAt: Long, val ttlSec: Int)

    private val map = object : LinkedHashMap<String, Entry>(256, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Entry>?) = size > MAX_ENTRIES
    }

    /** Chave: nome em minúsculas + tipo + classe (os 4 últimos bytes da pergunta). */
    private fun key(query: ByteArray): String? {
        val end = DnsMessage.questionEnd(query)
        if (end < 17) return null
        val name = DnsMessage.extractDomain(query)?.lowercase() ?: return null
        val tc = ((query[end - 4].toInt() and 0xFF) shl 24) or ((query[end - 3].toInt() and 0xFF) shl 16) or
            ((query[end - 2].toInt() and 0xFF) shl 8) or (query[end - 1].toInt() and 0xFF)
        return "$name/$tc"
    }

    /** Resposta guardada para [query] (com o ID da consulta), ou null. */
    fun get(query: ByteArray): ByteArray? {
        val k = key(query) ?: return null
        val now = SystemClock.elapsedRealtime()
        val entry = synchronized(map) {
            val e = map[k]
            if (e != null && now - e.storedAt >= e.ttlSec * 1000L) { map.remove(k); null } else e
        }
        if (entry == null) return null
        val elapsed = ((now - entry.storedAt) / 1000).toInt()
        return DnsMessage.withId(DnsMessage.withTtlReduced(entry.response, elapsed), DnsMessage.id(query))
    }

    fun put(query: ByteArray, response: ByteArray) {
        val ttl = DnsMessage.cacheableTtl(response)
        if (ttl <= 0) return
        val k = key(query) ?: return
        val entry = Entry(response, SystemClock.elapsedRealtime(), ttl.coerceIn(MIN_TTL_SEC, MAX_TTL_SEC))
        synchronized(map) { map[k] = entry }
    }

    fun clear() = synchronized(map) { map.clear() }
}
