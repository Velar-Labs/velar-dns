package app.velardns

import app.velardns.data.UpdateChecker
import app.velardns.dns.DnsCache
import app.velardns.dns.DnsMessage
import app.velardns.dns.Packets
import app.velardns.filter.DomainMatcher
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DomainMatcherTest {

    private fun matcher(vararg lines: String) = DomainMatcher().apply { load(lines.asSequence()) }

    @Test fun blocksExactAndSubdomains() {
        val m = matcher("0.0.0.0 tracker.com")
        assertTrue(m.isBlocked("tracker.com"))
        assertTrue(m.isBlocked("x.y.tracker.com"))
        assertTrue(m.isBlocked("TRACKER.COM."))
        assertFalse(m.isBlocked("notracker.com"))
        assertFalse(m.isBlocked("com"))
    }

    @Test fun parsesHostsAndPlainFormats() {
        assertEquals("a.com", matcher().parseLine("127.0.0.1 a.com"))
        assertEquals("b.com", matcher().parseLine("b.com # comentário"))
        assertNull(matcher().parseLine("# só comentário"))
        assertNull(matcher().parseLine("0.0.0.0 localhost"))
    }

    @Test fun parsesAdblockSyntax() {
        val m = matcher()
        assertEquals("ads.example.com", m.parseLine("||ads.example.com^"))
        assertEquals("ads.example.com", m.parseLine("||ads.example.com^\$important"))
        assertNull(m.parseLine("@@||ok.example.com^"))
        assertNull(m.parseLine("||*.example.com^"))
        assertNull(m.parseLine("||example.com/path^"))
        assertNull(m.parseLine("! comentário"))
    }
}

class DnsMessageTest {

    @Test fun blockedAnswersByType() {
        val a = DnsMessage.blocked(DnsMessage.buildQuery("bad.com", DnsMessage.TYPE_A))
        assertEquals(1, a[7].toInt())                       // ANCOUNT = 1
        assertArrayEquals(ByteArray(4), a.copyOfRange(a.size - 4, a.size))

        val aaaa = DnsMessage.blocked(DnsMessage.buildQuery("bad.com", DnsMessage.TYPE_AAAA))
        assertEquals(1, aaaa[7].toInt())
        assertArrayEquals(ByteArray(16), aaaa.copyOfRange(aaaa.size - 16, aaaa.size))

        val mx = DnsMessage.blocked(DnsMessage.buildQuery("bad.com", 15))
        assertEquals(0, mx[7].toInt())                      // sem respostas
    }

    @Test fun keepsQueryIdOnBlockedAnswer() {
        val q = DnsMessage.buildQuery("bad.com")
        assertEquals(DnsMessage.id(q), DnsMessage.id(DnsMessage.blocked(q)))
    }

    @Test fun extractsDomain() {
        assertEquals("example.org", DnsMessage.extractDomain(DnsMessage.buildQuery("example.org")))
    }

    @Test fun servFailHasRcode2() {
        val r = DnsMessage.servFail(DnsMessage.buildQuery("x.com"))
        assertEquals(2, r[3].toInt() and 0x0F)
    }
}

class DnsCacheTest {

    @Test fun storesNoErrorAnswersOnly() {
        DnsCache.clear()
        val q = DnsMessage.buildQuery("cached.com")
        // Resposta de bloqueio tem TTL 60 e NOERROR: é uma resposta válida para o teste de ida e volta.
        val answer = DnsMessage.blocked(q)
        DnsCache.put(q, answer)
        val hit = DnsCache.get(DnsMessage.buildQuery("CACHED.com"))
        assertNotNull(hit)
        // O cache devolve com o ID da nova consulta.
        val q2 = DnsMessage.buildQuery("cached.com")
        assertEquals(DnsMessage.id(q2), DnsMessage.id(DnsCache.get(q2)!!))

        DnsCache.put(DnsMessage.buildQuery("fail.com"), DnsMessage.servFail(DnsMessage.buildQuery("fail.com")))
        assertNull(DnsCache.get(DnsMessage.buildQuery("fail.com")))
    }

    @Test fun differentTypesDoNotCollide() {
        DnsCache.clear()
        val qa = DnsMessage.buildQuery("t.com", DnsMessage.TYPE_A)
        DnsCache.put(qa, DnsMessage.blocked(qa))
        assertNull(DnsCache.get(DnsMessage.buildQuery("t.com", DnsMessage.TYPE_AAAA)))
    }
}

class PacketsTest {

    private fun ipv4UdpQuery(dns: ByteArray, srcPort: Int = 40000): ByteArray {
        val udpLen = 8 + dns.size
        val total = 20 + udpLen
        val p = ByteArray(total)
        p[0] = 0x45
        p[2] = (total shr 8).toByte(); p[3] = total.toByte()
        p[8] = 64; p[9] = 17
        intArrayOf(10, 111, 222, 2).forEachIndexed { i, v -> p[12 + i] = v.toByte() }
        intArrayOf(10, 111, 222, 1).forEachIndexed { i, v -> p[16 + i] = v.toByte() }
        p[20] = (srcPort shr 8).toByte(); p[21] = srcPort.toByte()
        p[22] = 0; p[23] = 53
        p[24] = (udpLen shr 8).toByte(); p[25] = udpLen.toByte()
        System.arraycopy(dns, 0, p, 28, dns.size)
        return p
    }

    @Test fun parsesQueryAndBuildsValidResponse() {
        val dns = DnsMessage.buildQuery("example.com")
        val pkt = ipv4UdpQuery(dns)
        val q = Packets.parseDnsQuery(pkt, pkt.size)
        assertNotNull(q)
        assertEquals(40000, q!!.srcPort)
        assertArrayEquals(dns, q.payload)

        val reply = Packets.buildDnsResponse(q, DnsMessage.blocked(dns))
        // Origem e destino trocados
        assertArrayEquals(byteArrayOf(10, 111.toByte(), 222.toByte(), 1), reply.copyOfRange(12, 16))
        assertArrayEquals(byteArrayOf(10, 111.toByte(), 222.toByte(), 2), reply.copyOfRange(16, 20))
        // Soma de verificação do cabeçalho IPv4 confere: a soma de todas as palavras dá 0xFFFF.
        var sum = 0
        for (i in 0 until 20 step 2) sum += ((reply[i].toInt() and 0xFF) shl 8) or (reply[i + 1].toInt() and 0xFF)
        while (sum shr 16 != 0) sum = (sum and 0xFFFF) + (sum shr 16)
        assertEquals(0xFFFF, sum)
    }

    @Test fun ignoresNonDnsAndMalformedPackets() {
        val pkt = ipv4UdpQuery(DnsMessage.buildQuery("example.com"))
        val tcp = pkt.copyOf().also { it[9] = 6 }
        assertNull(Packets.parseDnsQuery(tcp, tcp.size))
        val wrongPort = pkt.copyOf().also { it[23] = 80 }
        assertNull(Packets.parseDnsQuery(wrongPort, wrongPort.size))
        val v6 = pkt.copyOf().also { it[0] = 0x60 }
        assertNull(Packets.parseDnsQuery(v6, v6.size))
        assertNull(Packets.parseDnsQuery(pkt, 10))
        val truncated = pkt.copyOf(30)
        assertNull(Packets.parseDnsQuery(truncated, truncated.size))
    }
}

class UpdateCheckerTest {
    @Test fun comparesVersions() {
        assertTrue(UpdateChecker.isNewer("1.4.0", "1.3.2"))
        assertTrue(UpdateChecker.isNewer("v1.10.0", "1.9.9"))
        assertTrue(UpdateChecker.isNewer("2.0", "1.9.9"))
        assertFalse(UpdateChecker.isNewer("1.3.2", "1.3.2"))
        assertFalse(UpdateChecker.isNewer("1.3.1", "1.3.2"))
        assertFalse(UpdateChecker.isNewer("lixo", "1.3.2"))
    }
}
