package com.example.agent.history.ui

import java.awt.Color
import java.awt.Component
import java.awt.Font
import java.awt.GraphicsEnvironment
import javax.swing.BorderFactory
import javax.swing.JScrollPane
import javax.swing.JTable
import javax.swing.UIManager
import javax.swing.border.Border
import javax.swing.plaf.FontUIResource
import javax.swing.table.DefaultTableCellRenderer

object HistoryTheme {

    val accent = Color(0x2F, 0x55, 0x97)
    val panelBackground = Color(0xF5, 0xF7, 0xFA)
    val coveredTint = Color(0xE6, 0xF2, 0xDE)
    val uncoveredTint = Color(0xFB, 0xE3, 0xD6)
    val stripe = Color(0xF7, 0xF9, 0xFC)
    val border = Color(0xC9, 0xD1, 0xDC)

    private val preferredFamilies = listOf("Segoe UI", "Arial", "Tahoma", "Verdana")
    private const val sample = "Журнал запусков генерации тестов"

    @Volatile
    private var installedFont: Font? = null

    fun install(size: Int = 13): Font {
        installedFont?.let { return it }
        val available = GraphicsEnvironment.getLocalGraphicsEnvironment().availableFontFamilyNames.toSet()
        val family = preferredFamilies.firstOrNull { it in available && Font(it, Font.PLAIN, size).canDisplayUpTo(sample) == -1 }
            ?: Font.DIALOG
        val font = Font(family, Font.PLAIN, size)
        UIManager.put("swing.boldMetal", false)
        val keys = UIManager.getDefaults().keys().toList()
        for (key in keys) {
            if (UIManager.get(key) is FontUIResource) UIManager.put(key, FontUIResource(font))
        }
        installedFont = font
        return font
    }

    fun font(): Font = installedFont ?: install()

    fun bold(size: Float = font().size2D): Font = font().deriveFont(Font.BOLD, size)

    fun sectionBorder(title: String): Border = BorderFactory.createCompoundBorder(
        BorderFactory.createTitledBorder(BorderFactory.createLineBorder(border), title, 0, 0, bold(), accent),
        BorderFactory.createEmptyBorder(4, 6, 6, 6)
    )

    fun scroll(table: JTable): JScrollPane {
        val pane = JScrollPane(table)
        pane.setColumnHeaderView(table.tableHeader)
        pane.viewport.background = Color.WHITE
        return pane
    }

    fun style(table: JTable, tint: (Int) -> Color? = { null }) {
        table.font = font()
        table.rowHeight = 24
        table.fillsViewportHeight = true
        table.showVerticalLines = false
        table.gridColor = border
        table.autoResizeMode = JTable.AUTO_RESIZE_SUBSEQUENT_COLUMNS
        table.tableHeader.font = bold()
        table.tableHeader.reorderingAllowed = false
        table.setDefaultRenderer(Any::class.java, TintRenderer(tint))
        val model = table.model
        if (model is ListTableModel<*>) {
            for (index in 0 until table.columnModel.columnCount) {
                table.columnModel.getColumn(index).preferredWidth = model.preferredWidth(index)
            }
        }
    }

    private class TintRenderer(private val tint: (Int) -> Color?) : DefaultTableCellRenderer() {
        override fun getTableCellRendererComponent(
            table: JTable,
            value: Any?,
            isSelected: Boolean,
            hasFocus: Boolean,
            row: Int,
            column: Int
        ): Component {
            val component = super.getTableCellRendererComponent(table, value, isSelected, false, row, column)
            component.font = table.font
            horizontalAlignment = if (value is Number) RIGHT else LEFT
            if (!isSelected) {
                component.background = tint(row) ?: if (row % 2 == 1) stripe else table.background
                component.foreground = table.foreground
            }
            border = BorderFactory.createEmptyBorder(0, 6, 0, 6)
            return component
        }
    }
}
