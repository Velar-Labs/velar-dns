package app.velardns.dns

import java.io.ByteArrayOutputStream
import kotlin.random.Random

/** Utilidades mínimas de mensagens DNS (RFC 1035) — só o que o app precisa. */
object DnsMessage {

    const val TYPE_A = 1
    const val TYPE_AAAA = 28

    /** Monta uma consulta simples (recursão ligada) para [name]. */
    fun buildQuery(name: String, type: Int = TYPE_A): ByteArray {
        val out = ByteArrayOutputStream(64)
        val id = Random.nextInt(0, 0x10000)
        out.write(id shr 8); out.write(id and 0xFF)
        out.write(0x01); out.write(0x00)    // flags: RD
        out.write(0x00); out.write(0x01)    // QDCOUNT = 1
        repeat(6) { out.write(0x00) }       // AN, NS, AR = 0
        for (label in name.trimEnd('.').split('.')) {
            val bytes = label.toByteArray(Charsets.US_ASCII)
            out.write(bytes.size)
            out.write(bytes, 0, bytes.size)
        }
        out.write(0x00)
        out.write(type shr 8); out.write(type and 0xFF)
        out.write(0x00); out.write(0x01)    // CLASS IN
        return out.toByteArray()
    }

    fun id(msg: ByteArray): Int =
        if (msg.size < 2) 0 else ((msg[0].toInt() and 0xFF) shl 8) or (msg[1].toInt() and 0xFF)

    fun withId(msg: ByteArray, id: Int): ByteArray {
        if (msg.size < 2) return msg
        val copy = msg.copyOf()
        copy[0] = (id shr 8).toByte()
        copy[1] = id.toByte()
        return copy
    }

    /** Posição logo após o fim de um nome (trata ponteiros de compressão). */
    private fun skipName(msg: ByteArray, start: Int): Int {
        var p = start
        while (p < msg.size) {
            val len = msg[p].toInt() and 0xFF
            when {
                len == 0 -> return p + 1
                len and 0xC0 == 0xC0 -> return p + 2
                else -> p += len + 1
            }
        }
        return -1
    }

    /** Fim da seção de perguntas, ou -1 se a mensagem estiver malformada. */
    fun questionEnd(msg: ByteArray): Int {
        if (msg.size < 12) return -1
        val qd = ((msg[4].toInt() and 0xFF) shl 8) or (msg[5].toInt() and 0xFF)
        var p = 12
        repeat(qd) {
            p = skipName(msg, p)
            if (p < 0 || p + 4 > msg.size) return -1
            p += 4
        }
        return p
    }

    /** Resposta SERVFAIL para devolver quando nenhum servidor DoH respondeu. */
    fun servFail(query: ByteArray): ByteArray {
        val end = questionEnd(query)
        val base = if (end > 0) query.copyOf(end) else query.copyOf(12)
        val rd = base[2].toInt() and 0x01
        base[2] = (0x80 or rd).toByte()      // QR=1, mantém RD
        base[3] = 0x82.toByte()              // RA=1, RCODE=2 (SERVFAIL)
        for (i in 6 until 12) base[i] = 0    // AN, NS, AR = 0
        if (end <= 0) { base[4] = 0; base[5] = 0 }
        return base
    }

    /**
     * Extrai o nome de domínio da seção de perguntas de uma mensagem DNS.
     * Retorna null se a mensagem estiver malformada.
     */
    fun extractDomain(msg: ByteArray): String? {
        if (msg.size < 13) return null
        val parts = mutableListOf<String>()
        var p = 12
        while (p < msg.size) {
            val len = msg[p].toInt() and 0xFF
            if (len == 0) break
            if (len and 0xC0 == 0xC0) break  // ponteiro de compressão — não esperado na query
            p++
            if (p + len > msg.size) return null
            parts.add(String(msg, p, len, Charsets.US_ASCII))
            p += len
        }
        return if (parts.isEmpty()) null else parts.joinToString(".")
    }

    /** Tipo (QTYPE) da primeira pergunta, ou -1 se a mensagem estiver malformada. */
    fun questionType(msg: ByteArray): Int {
        val end = questionEnd(msg)
        if (end < 16) return -1
        return ((msg[end - 4].toInt() and 0xFF) shl 8) or (msg[end - 3].toInt() and 0xFF)
    }

    /**
     * Resposta de bloqueio, de acordo com o tipo da pergunta:
     * A -> 0.0.0.0, AAAA -> ::, qualquer outro tipo -> NOERROR sem registros.
     * Assim o domínio bloqueado também não escapa pelo IPv6.
     */
    fun blocked(query: ByteArray): ByteArray {
        val end = questionEnd(query)
        if (end < 0) return servFail(query)
        val type = questionType(query)
        val rdLen = when (type) {
            TYPE_A -> 4
            TYPE_AAAA -> 16
            else -> 0
        }
        val out = java.io.ByteArrayOutputStream(end + 32)
        out.write(query[0].toInt() and 0xFF)
        out.write(query[1].toInt() and 0xFF)
        val rd = query[2].toInt() and 0x01
        out.write(0x80 or rd) // QR=1, mantém RD
        out.write(0x80)       // RA=1, RCODE=0 (NOERROR)
        out.write(query[4].toInt() and 0xFF)
        out.write(query[5].toInt() and 0xFF)
        out.write(0x00); out.write(if (rdLen > 0) 0x01 else 0x00) // ANCOUNT
        repeat(4) { out.write(0x00) }                              // NS = 0, AR = 0
        out.write(query, 12, end - 12)                             // pergunta
        if (rdLen == 0) return out.toByteArray()
        out.write(0xC0); out.write(0x0C)                           // nome: ponteiro p/ a pergunta
        out.write(type shr 8); out.write(type and 0xFF)
        out.write(0x00); out.write(0x01)                           // CLASS IN
        out.write(0x00); out.write(0x00); out.write(0x00); out.write(0x3C) // TTL = 60 s
        out.write(0x00); out.write(rdLen)
        repeat(rdLen) { out.write(0x00) }                          // 0.0.0.0 ou ::
        return out.toByteArray()
    }

    /** Resposta é NOERROR, completa (sem TC) e com ao menos um registro? Só essas vão para o cache. */
    fun cacheableTtl(msg: ByteArray): Int {
        if (msg.size < 12) return 0
        val flags2 = msg[2].toInt() and 0xFF
        val rcode = msg[3].toInt() and 0x0F
        val tc = flags2 and 0x02 != 0
        val an = ((msg[6].toInt() and 0xFF) shl 8) or (msg[7].toInt() and 0xFF)
        if (flags2 and 0x80 == 0 || tc || rcode != 0 || an == 0) return 0
        var min = Int.MAX_VALUE
        forEachAnswerTtl(msg) { _, ttl -> if (ttl < min) min = ttl }
        return if (min == Int.MAX_VALUE) 0 else min
    }

    /** Devolve cópia de [msg] com o TTL dos registros de resposta reduzido em [elapsedSec]. */
    fun withTtlReduced(msg: ByteArray, elapsedSec: Int): ByteArray {
        val copy = msg.copyOf()
        forEachAnswerTtl(copy) { pos, ttl ->
            val v = (ttl - elapsedSec).coerceAtLeast(1)
            copy[pos] = (v ushr 24).toByte(); copy[pos + 1] = (v ushr 16).toByte()
            copy[pos + 2] = (v ushr 8).toByte(); copy[pos + 3] = v.toByte()
        }
        return copy
    }

    /** Percorre os registros da seção de respostas; [action] recebe a posição do TTL e o seu valor. */
    private inline fun forEachAnswerTtl(msg: ByteArray, action: (Int, Int) -> Unit) {
        val an = ((msg[6].toInt() and 0xFF) shl 8) or (msg[7].toInt() and 0xFF)
        var p = questionEnd(msg)
        if (p < 0) return
        repeat(an) {
            p = skipName(msg, p)
            if (p < 0 || p + 10 > msg.size) return
            val ttl = ((msg[p + 4].toInt() and 0xFF) shl 24) or ((msg[p + 5].toInt() and 0xFF) shl 16) or
                ((msg[p + 6].toInt() and 0xFF) shl 8) or (msg[p + 7].toInt() and 0xFF)
            val rdLen = ((msg[p + 8].toInt() and 0xFF) shl 8) or (msg[p + 9].toInt() and 0xFF)
            action(p + 4, ttl.coerceAtLeast(0))
            p += 10 + rdLen
            if (p > msg.size) return
        }
    }

    /** Endereços IPv4 (registros A) da seção de respostas. */
    fun parseA(msg: ByteArray): List<String> {
        val result = mutableListOf<String>()
        if (msg.size < 12) return result
        val an = ((msg[6].toInt() and 0xFF) shl 8) or (msg[7].toInt() and 0xFF)
        var p = questionEnd(msg)
        if (p < 0) return result
        repeat(an) {
            p = skipName(msg, p)
            if (p < 0 || p + 10 > msg.size) return result
            val type = ((msg[p].toInt() and 0xFF) shl 8) or (msg[p + 1].toInt() and 0xFF)
            val rdLen = ((msg[p + 8].toInt() and 0xFF) shl 8) or (msg[p + 9].toInt() and 0xFF)
            p += 10
            if (p + rdLen > msg.size) return result
            if (type == TYPE_A && rdLen == 4) {
                result += (0 until 4).joinToString(".") { (msg[p + it].toInt() and 0xFF).toString() }
            }
            p += rdLen
        }
        return result
    }
}
