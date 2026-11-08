package app.velardns.filter

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * Estatísticas de bloqueio guardadas no aparelho: bloqueios por dia (últimos 7)
 * e domínios mais bloqueados. Nada disso sai do aparelho.
 */
object BlockStats {

    private const val KEEP_DAYS = 7
    private const val MAX_DOMAINS = 100
    private const val SAVE_EVERY = 10

    data class Snapshot(
        /** Do dia mais antigo ao de hoje: (yyyyMMdd, bloqueios). Sempre [KEEP_DAYS] itens. */
        val days: List<Pair<String, Int>> = emptyList(),
        /** Os mais bloqueados, do maior para o menor. */
        val top: List<Pair<String, Int>> = emptyList(),
    )

    private var sp: SharedPreferences? = null
    private val dayCounts = HashMap<String, Int>()
    private val domainCounts = HashMap<String, Int>()
    private var sinceSave = 0

    private val _snapshot = MutableStateFlow(Snapshot())
    val snapshot: StateFlow<Snapshot> = _snapshot.asStateFlow()

    @Synchronized
    fun init(context: Context) {
        if (sp != null) return
        val prefs = context.applicationContext.getSharedPreferences("velar_stats", Context.MODE_PRIVATE)
        sp = prefs
        parse(prefs.getString("days", ""), dayCounts)
        parse(prefs.getString("domains", ""), domainCounts)
        publish()
    }

    @Synchronized
    fun record(domain: String) {
        val name = domain.lowercase().trimEnd('.')
        dayCounts.merge(dayKey(0), 1, Int::plus)
        domainCounts.merge(name, 1, Int::plus)
        if (domainCounts.size > MAX_DOMAINS) {
            // Descarta o menos bloqueado, para a lista não crescer sem limite.
            domainCounts.entries.minByOrNull { it.value }?.let { domainCounts.remove(it.key) }
        }
        publish()
        if (++sinceSave >= SAVE_EVERY) save()
    }

    @Synchronized
    fun save() {
        val prefs = sp ?: return
        sinceSave = 0
        prefs.edit()
            .putString("days", dayCounts.entries.joinToString(",") { "${it.key}=${it.value}" })
            .putString("domains", domainCounts.entries.joinToString(",") { "${it.key}=${it.value}" })
            .apply()
    }

    private fun publish() {
        val keys = (KEEP_DAYS - 1 downTo 0).map { dayKey(-it) }
        dayCounts.keys.retainAll(keys.toSet())
        _snapshot.value = Snapshot(
            days = keys.map { it to (dayCounts[it] ?: 0) },
            top = domainCounts.entries
                .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
                .take(5).map { it.key to it.value },
        )
    }

    private fun dayKey(offsetDays: Int): String {
        val cal = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, offsetDays) }
        return SimpleDateFormat("yyyyMMdd", Locale.US).format(cal.time)
    }

    private fun parse(text: String?, into: MutableMap<String, Int>) {
        text.orEmpty().split(',').forEach { part ->
            val i = part.lastIndexOf('=')
            val n = if (i > 0) part.substring(i + 1).toIntOrNull() else null
            if (n != null) into[part.substring(0, i)] = n
        }
    }
}
