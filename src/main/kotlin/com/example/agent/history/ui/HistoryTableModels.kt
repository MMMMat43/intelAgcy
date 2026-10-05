package com.example.agent.history.ui

import com.example.agent.history.BranchRow
import com.example.agent.history.FunctionRow
import com.example.agent.history.RunSummaryRow
import com.example.agent.history.TestCaseRow
import com.example.agent.storage.RunNote
import javax.swing.table.AbstractTableModel

class Column<T>(val title: String, val width: Int, val value: (T) -> Any?)

open class ListTableModel<T>(private val columns: List<Column<T>>) : AbstractTableModel() {

    var rows: List<T> = emptyList()
        private set

    fun update(items: List<T>) {
        rows = items
        fireTableDataChanged()
    }

    fun rowAt(index: Int): T? = rows.getOrNull(index)

    fun preferredWidth(column: Int): Int = columns[column].width

    override fun getRowCount(): Int = rows.size

    override fun getColumnCount(): Int = columns.size

    override fun getColumnName(column: Int): String = columns[column].title

    override fun getValueAt(rowIndex: Int, columnIndex: Int): Any? = columns[columnIndex].value(rows[rowIndex])

    override fun isCellEditable(rowIndex: Int, columnIndex: Int): Boolean = false
}

class RunsTableModel : ListTableModel<RunSummaryRow>(
    listOf(
        Column("Проект", 170) { it.project },
        Column("Начало", 130) { HistoryFormat.date(it.startedAt) },
        Column("Статус", 90) { HistoryFormat.status(it.status) },
        Column("Длительность", 100) { HistoryFormat.seconds(it.durationMillis) },
        Column("Функций", 75) { it.functions },
        Column("Тест-кейсов", 95) { it.testCases },
        Column("Ветви", 90) { HistoryFormat.branches(it.coveredBranches, it.totalBranches) },
        Column("Покрытие", 90) { HistoryFormat.percent(it.coverage) }
    )
)

class FunctionsTableModel : ListTableModel<FunctionRow>(
    listOf(
        Column("Класс", 150) { it.className },
        Column("Функция", 160) { it.functionName },
        Column("Параметры", 320) { it.signature },
        Column("Сложность", 90) { it.complexity ?: "—" },
        Column("Ветви", 80) { HistoryFormat.branches(it.coveredBranches, it.totalBranches) },
        Column("Покрытие", 90) { HistoryFormat.percent(it.coverage) },
        Column("Тест-кейсов", 90) { it.testCases }
    )
)

class BranchesTableModel : ListTableModel<BranchRow>(
    listOf(
        Column("Функция", 270) { it.function },
        Column("Ветвь", 110) { it.code.substringAfter('#') },
        Column("Условие", 430) { it.label },
        Column("Состояние", 150) { HistoryFormat.branchState(it.state) }
    )
)

class TestCasesTableModel : ListTableModel<TestCaseRow>(
    listOf(
        Column("Функция", 230) { it.function },
        Column("Вид", 100) { HistoryFormat.scenario(it.scenarioType) },
        Column("Источник", 135) { HistoryFormat.origin(it.origin) },
        Column("Входные данные", 320) { it.inputs },
        Column("Ожидаемый результат", 230) { it.expectedResult ?: "—" }
    )
)

class NotesTableModel : ListTableModel<RunNote>(
    listOf(
        Column("Время", 140) { HistoryFormat.date(it.createdAt) },
        Column("Текст заметки", 820) { it.text }
    )
)
