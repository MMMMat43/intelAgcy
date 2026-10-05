package com.example.agent.history.ui

import com.example.agent.storage.BranchState
import com.example.agent.storage.RunStatus
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

object HistoryFormat {

    private val locale = Locale.forLanguageTag("ru-RU")
    private val dateFormat = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm", locale)

    var zone: ZoneId = ZoneId.systemDefault()

    fun date(value: Instant): String = dateFormat.format(value.atZone(zone))

    fun percent(value: Double?): String = if (value == null) "—" else String.format(locale, "%.1f %%", value * 100)

    fun branches(covered: Int, total: Int): String = if (total == 0) "—" else "$covered / $total"

    fun seconds(millis: Long): String = String.format(locale, "%.1f с", millis / 1000.0)

    fun status(value: RunStatus): String = when (value) {
        RunStatus.COMPLETED -> "Завершён"
        RunStatus.FAILED -> "Ошибка"
    }

    fun branchState(value: BranchState): String = when (value) {
        BranchState.COVERED -> "Покрыта"
        BranchState.NOT_FOUND -> "Не найдено значение"
        BranchState.NOT_INSTRUMENTABLE -> "Не инструментируется"
    }

    fun scenario(value: String): String = when (value) {
        "POSITIVE" -> "Позитивный"
        "NEGATIVE" -> "Негативный"
        "BOUNDARY" -> "Граничный"
        else -> value
    }

    fun origin(value: String): String = when (value) {
        "HEURISTIC" -> "Эвристика типов"
        "BOUNDARY" -> "Границы условий"
        "SEARCH" -> "Поиск по покрытию"
        else -> value
    }
}
