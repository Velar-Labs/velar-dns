package app.velardns.dns

/**
 * Leitura e montagem de pacotes IPv4/UDP que passam pelo túnel.
 * O túnel só recebe tráfego destinado ao IP virtual de DNS (e, opcionalmente,
 * a DNS públicos conhecidos), então só tratamos UDP na porta 53.
 */
object Packets {

    class DnsQuery(
        val srcIp: ByteArray,
        val dstIp: ByteArray,
        val srcPort: Int,
        val dstPort: Int,
        val payload: ByteArray,
    )

    private fun u16(b: ByteArray, i: Int): Int =
        ((b[i].toInt() and 0xFF) shl 8) or (b[i + 1].toInt() and 0xFF)

    /** Extrai a consulta DNS de um pacote IPv4/UDP, ou null se não for uma. */
    fun parseDnsQuery(pkt: ByteArray, len: Int): DnsQuery? {
        if (len < 28) return null
        val version = (pkt[0].toInt() and 0xF0) shr 4
        if (version != 4) return null
        val ihl = (pkt[0].toInt() and 0x0F) * 4
        if (ihl < 20 || len < ihl + 8) return null
        if ((pkt[9].toInt() and 0xFF) != 17) return null          // só UDP
        val totalLen = u16(pkt, 2)
        if (totalLen > len || totalLen < ihl + 8) return null
        // ignora fragmentos (MF ligado ou offset diferente de zero)
        val fragField = u16(pkt, 6)
        if (fragField and 0x3FFF != 0) return null

        val srcPort = u16(pkt, ihl)
        val dstPort = u16(pkt, ihl + 2)
        if (dstPort != 53) return null
        val udpLen = u16(pkt, ihl + 4)
        if (udpLen < 8 || ihl + udpLen > totalLen) return null
        val payload = pkt.copyOfRange(ihl + 8, ihl + udpLen)
        if (payload.size < 12) return null

        return DnsQuery(
            srcIp = pkt.copyOfRange(12, 16),
            dstIp = pkt.copyOfRange(16, 20),
            srcPort = srcPort,
            dstPort = dstPort,
            payload = payload,
        )
    }

    /** Monta o pacote de resposta, invertendo origem e destino da consulta. */
    fun buildDnsResponse(q: DnsQuery, dns: ByteArray): ByteArray {
        val udpLen = 8 + dns.size
        val total = 20 + udpLen
        val out = ByteArray(total)

        // Cabeçalho IPv4
        out[0] = 0x45
        out[1] = 0
        out[2] = (total shr 8).toByte(); out[3] = total.toByte()
        out[4] = 0; out[5] = 0                       // identificação
        out[6] = 0x40; out[7] = 0                    // DF
        out[8] = 64                                  // TTL
        out[9] = 17                                  // UDP
        System.arraycopy(q.dstIp, 0, out, 12, 4)    // origem = quem foi consultado
        System.arraycopy(q.srcIp, 0, out, 16, 4)    // destino = quem perguntou
        val ipSum = checksum(out, 0, 20)
        out[10] = (ipSum shr 8).toByte(); out[11] = ipSum.toByte()

        // Cabeçalho UDP
        out[20] = (q.dstPort shr 8).toByte(); out[21] = q.dstPort.toByte()
        out[22] = (q.srcPort shr 8).toByte(); out[23] = q.srcPort.toByte()
        out[24] = (udpLen shr 8).toByte(); out[25] = udpLen.toByte()
        out[26] = 0; out[27] = 0                     // checksum UDP opcional em IPv4
        System.arraycopy(dns, 0, out, 28, dns.size)

        val udpSum = udpChecksum(out, q.dstIp, q.srcIp, udpLen)
        out[26] = (udpSum shr 8).toByte(); out[27] = udpSum.toByte()
        return out
    }

    private fun checksum(b: ByteArray, off: Int, len: Int, initial: Long = 0): Int {
        var sum = initial
        var i = off
        while (i + 1 < off + len) {
            sum += u16(b, i).toLong()
            i += 2
        }
        if (i < off + len) sum += ((b[i].toInt() and 0xFF) shl 8).toLong()
        while (sum shr 16 != 0L) sum = (sum and 0xFFFF) + (sum shr 16)
        return (sum.inv() and 0xFFFF).toInt()
    }

    private fun udpChecksum(pkt: ByteArray, src: ByteArray, dst: ByteArray, udpLen: Int): Int {
        var pseudo = 0L
        pseudo += u16(src, 0) + u16(src, 2)
        pseudo += u16(dst, 0) + u16(dst, 2)
        pseudo += 17
        pseudo += udpLen
        val sum = checksum(pkt, 20, udpLen, pseudo)
        return if (sum == 0) 0xFFFF else sum
    }
}
