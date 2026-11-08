package app.velardns.filter

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Registro em memória dos domínios bloqueados (últimos N).
 * Não persiste em disco — reiniciar o app limpa o log.
 * O contador total persiste via SharedPreferences.
 */
object BlockLog {

    private const val MAX_ENTRIES = 200

    data class Entry(
        val domain: String,
        val timestamp: Long = System.currentTimeMillis(),
    ) {
        val timeFormatted: String
            get() = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(timestamp))
    }

    private val _entries = MutableStateFlow<List<Entry>>(emptyList())
    val entries: StateFlow<List<Entry>> = _entries.asStateFlow()

    private val _blockedCount = MutableStateFlow(0L)
    val blockedCount: StateFlow<Long> = _blockedCount.asStateFlow()

    fun record(domain: String) {
        val entry = Entry(domain)
        val current = _entries.value
        _entries.value = if (current.size >= MAX_ENTRIES) {
            listOf(entry) + current.dropLast(1)
        } else {
            listOf(entry) + current
        }
        _blockedCount.value++
    }

    /** Restaura o contador total salvo (chamado na inicialização). */
    fun restoreCount(saved: Long) {
        _blockedCount.value = saved
    }
}
