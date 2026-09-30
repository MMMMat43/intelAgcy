package com.example.agent.coverage

import com.example.agent.analysis.AnalysisResult
import com.example.agent.analysis.AnalyzedFunction
import com.example.agent.analysis.collectAll
import com.example.agent.analysis.lineOf
import com.example.agent.execution.SourceUnit
import com.example.agent.model.isTestable
import com.example.agent.model.key
import org.jetbrains.kotlin.psi.KtBlockExpression
import org.jetbrains.kotlin.psi.KtCatchClause
import org.jetbrains.kotlin.psi.KtDoWhileExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtForExpression
import org.jetbrains.kotlin.psi.KtIfExpression
import org.jetbrains.kotlin.psi.KtLoopExpression
import org.jetbrains.kotlin.psi.KtWhenExpression
import org.jetbrains.kotlin.psi.KtWhileExpression
import java.lang.reflect.Method

const val PROBE_PACKAGE = "agentcov"
const val PROBE_OBJECT = "AgentCoverageProbe"
const val PROBE_STATE = "AgentCoverageState"
const val PROBE_FILE_NAME = "AgentCoverageProbe.kt"
private const val PROBE_CALL = "$PROBE_PACKAGE.$PROBE_OBJECT.hit"

data class InstrumentedProgram(
    val units: List<SourceUnit>,
    val probes: List<BranchProbe>
)

private data class Edit(val offset: Int, val text: String, val order: Int)

class BranchInstrumenter {

    fun instrument(analysis: AnalysisResult): InstrumentedProgram {
        val probes = mutableListOf<BranchProbe>()
        val units = analysis.parsedSources.map { source ->
            val text = source.ktFile.text
            val edits = mutableListOf<Edit>()
            analysis.functions
                .filter { it.source === source && it.info.isTestable() }
                .forEach { Collector(it, text, edits, probes).collect() }
            SourceUnit(source.file.fileName, apply(text, edits))
        }
        return InstrumentedProgram(units + SourceUnit(PROBE_FILE_NAME, probeSource(probes.size)), probes)
    }

    private fun apply(text: String, edits: List<Edit>): String {
        val builder = StringBuilder(text)
        edits
            .sortedWith(compareByDescending<Edit> { it.offset }.thenByDescending { it.order })
            .forEach { builder.insert(it.offset, it.text) }
        return builder.toString()
    }

    private class Collector(
        private val analyzed: AnalyzedFunction,
        private val text: String,
        private val edits: MutableList<Edit>,
        private val probes: MutableList<BranchProbe>
    ) {
        private var ordinal = 0

        fun collect() {
            val body = analyzed.psi.bodyExpression ?: return

            body.collectAll(KtIfExpression::class.java).forEach { ifExpression ->
                val group = ifExpression.textRange.startOffset + 1
                val condition = compact(ifExpression.condition?.text.orEmpty())
                val start = ifExpression.textRange.startOffset
                ifExpression.then?.let { cover(it, register("if-then", "if ($condition)", start), group) }
                val elseBranch = ifExpression.`else`
                val elseProbe = register("if-else", "else of if ($condition)", start)
                if (elseBranch != null) {
                    cover(elseBranch, elseProbe, group)
                } else {
                    edits += Edit(ifExpression.textRange.endOffset, " else { ${call(elseProbe)} }", -group * 10 + 1)
                }
            }

            body.collectAll(KtLoopExpression::class.java).forEach { loop ->
                val loopBody = loop.body ?: return@forEach
                val (kind, label) = when (loop) {
                    is KtForExpression -> "for" to "for (${compact(loop.loopRange?.text.orEmpty())})"
                    is KtWhileExpression -> "while" to "while (${compact(loop.condition?.text.orEmpty())})"
                    is KtDoWhileExpression -> "do-while" to "do-while (${compact(loop.condition?.text.orEmpty())})"
                    else -> "loop" to "loop"
                }
                val group = loop.textRange.startOffset + 1
                cover(loopBody, register(kind, label, loop.textRange.startOffset), group)
            }

            body.collectAll(KtWhenExpression::class.java).forEach { whenExpression ->
                whenExpression.entries.forEach { entry ->
                    val expression = entry.expression ?: return@forEach
                    val label = if (entry.isElse) {
                        "when: else"
                    } else {
                        "when: " + compact(entry.conditions.joinToString(", ") { it.text })
                    }
                    val group = entry.textRange.startOffset + 1
                    cover(expression, register("when", label, entry.textRange.startOffset), group)
                }
            }

            body.collectAll(KtCatchClause::class.java).forEach { catchClause ->
                val catchBody = catchClause.catchBody ?: return@forEach
                val type = compact(catchClause.catchParameter?.typeReference?.text ?: "Throwable")
                val group = catchClause.textRange.startOffset + 1
                cover(catchBody, register("catch", "catch ($type)", catchClause.textRange.startOffset), group)
            }
        }

        private fun register(kind: String, label: String, offset: Int): BranchProbe {
            val info = analyzed.info
            val probe = BranchProbe(
                index = probes.size,
                id = "${info.className}.${info.name}#${++ordinal}:$kind",
                functionKey = info.key(),
                kind = kind,
                label = label,
                line = lineOf(text, offset)
            )
            probes += probe
            return probe
        }

        private fun call(probe: BranchProbe): String = "$PROBE_CALL(${probe.index})"

        private fun cover(expression: KtExpression, probe: BranchProbe, group: Int) {
            if (expression is KtBlockExpression) {
                val brace = expression.lBrace
                if (brace == null) {
                    probes[probe.index] = probe.copy(instrumentable = false)
                    return
                }
                edits += Edit(brace.textRange.endOffset, " ${call(probe)}; ", group)
            } else {
                edits += Edit(expression.textRange.startOffset, "{ ${call(probe)}; ", group)
                edits += Edit(expression.textRange.endOffset, " }", -group * 10)
            }
        }

        private fun compact(value: String): String = value.replace(Regex("\\s+"), " ").trim().take(80)
    }
}

fun probeSource(size: Int): String =
    "package $PROBE_PACKAGE\n\n" +
        "class $PROBE_STATE(size: Int) {\n" +
        "    @JvmField val hits = BooleanArray(size)\n" +
        "    @Volatile @JvmField var cancelled = false\n" +
        "}\n\n" +
        "object $PROBE_OBJECT {\n" +
        "    private val local = ThreadLocal<$PROBE_STATE>()\n" +
        "    private const val SIZE = ${maxOf(size, 1)}\n\n" +
        "    @JvmStatic fun begin(): $PROBE_STATE {\n" +
        "        val state = $PROBE_STATE(SIZE)\n" +
        "        local.set(state)\n" +
        "        return state\n" +
        "    }\n\n" +
        "    @JvmStatic fun hit(id: Int) {\n" +
        "        val state = local.get() ?: return\n" +
        "        state.hits[id] = true\n" +
        "        if (state.cancelled) throw IllegalStateException(\"agent-cancelled\")\n" +
        "    }\n" +
        "}\n"

class ProbeSession(private val state: Any) {

    private val hits = state.javaClass.getField("hits").get(state) as BooleanArray
    private val cancelledField = state.javaClass.getField("cancelled")

    fun snapshot(): Set<Int> {
        val result = HashSet<Int>()
        for (index in hits.indices) {
            if (hits[index]) result += index
        }
        return result
    }

    fun cancel() {
        cancelledField.setBoolean(state, true)
    }
}

class CoverageProbeHandle(classLoader: ClassLoader) {

    private val beginMethod: Method =
        Class.forName("$PROBE_PACKAGE.$PROBE_OBJECT", true, classLoader).getMethod("begin")

    fun begin(): ProbeSession = ProbeSession(beginMethod.invoke(null))
}
