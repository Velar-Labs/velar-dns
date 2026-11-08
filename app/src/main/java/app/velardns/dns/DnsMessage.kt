package app.velardns.dns

import java.io.ByteArrayOutputStream
import kotlin.random.Random

/** Utilidades mínimas de mensagens DNS (RFC 1035) — só o que o app precisa. */
object DnsMessage {

    const val TYPE_A = 1

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

    /**
     * Monta uma resposta DNS com o IP 0.0.0.0 (NXDOMAIN efetivo).
     * Usada para bloquear domínios maliciosos sem sair da rede.
     */
    fun blocked(query: ByteArray): ByteArray {
        val end = questionEnd(query)
        if (end < 0) return servFail(query)
        val out = java.io.ByteArrayOutputStream(end + 16)
        // Header: copia ID, define QR=1, RA=1, RCODE=0 (NOERROR), ANCOUNT=1
        out.write(query[0].toInt() and 0xFF)
        out.write(query[1].toInt() and 0xFF)
        val rd = query[2].toInt() and 0x01
        out.write(0x80 or rd) // QR=1, mantém RD
        out.write(0x80)       // RA=1, RCODE=0
        // QDCOUNT = original
        out.write(query[4].toInt() and 0xFF)
        out.write(query[5].toInt() and 0xFF)
        // ANCOUNT = 1
        out.write(0x00); out.write(0x01)
        // NS = 0, AR = 0
        out.write(0x00); out.write(0x00)
        out.write(0x00); out.write(0x00)
        // Seção de perguntas (cópia integral)
        out.write(query, 12, end - 12)
        // Resposta: ponteiro para o nome na pergunta (offset 12)
        out.write(0xC0); out.write(0x0C)
        // TYPE A
        out.write(0x00); out.write(0x01)
        // CLASS IN
        out.write(0x00); out.write(0x01)
        // TTL = 60 segundos
        out.write(0x00); out.write(0x00); out.write(0x00); out.write(0x3C)
        // RDLENGTH = 4
        out.write(0x00); out.write(0x04)
        // RDATA = 0.0.0.0
        out.write(0x00); out.write(0x00); out.write(0x00); out.write(0x00)
        return out.toByteArray()
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
