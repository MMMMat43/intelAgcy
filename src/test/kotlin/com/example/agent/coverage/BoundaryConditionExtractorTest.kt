package com.example.agent.coverage

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BoundaryConditionExtractorTest {

    private fun hintsFor(resource: String, function: String): BoundaryHints {
        val analysis = CoverageTestSupport.analysisOf(resource)
        val analyzed = analysis.functions.first { it.info.name == function }
        return BoundaryConditionExtractor().extract(analyzed)
    }

    @Test
    fun `finds thresholds around the member discount constant for a derived variable`() {
        val hints = hintsFor("OrderProcessor.kt", "calculateDiscount")

        assertTrue(100.0 in hints.constants)
        assertTrue(0.0 in hints.constants)
        assertTrue(10.0 in hints.constants)
        assertTrue(hints.constants.any { it == 200.0 }, "expected folded constant MEMBER_DISCOUNT_THRESHOLD * 2 = 200.0")
    }

    @Test
    fun `finds integer neighbours of the quantity condition`() {
        val hints = hintsFor("OrderProcessor.kt", "calculateDiscount")

        val quantity = hints.values.getValue("quantity")
        assertTrue(quantity.containsAll(listOf("-1", "0", "1", "9", "10", "11")), quantity.toString())
    }

    @Test
    fun `finds neighbours of constants in customer categorisation`() {
        val hints = hintsFor("OrderProcessor.kt", "categorizeCustomer")

        val purchaseCount = hints.values.getValue("purchaseCount")
        assertTrue(purchaseCount.containsAll(listOf("4", "5", "19", "20")), purchaseCount.toString())
        val totalSpent = hints.values.getValue("totalSpent")
        assertTrue(totalSpent.contains("5000.0"), totalSpent.toString())
    }

    @Test
    fun `finds string literals and null checks`() {
        val analysis = CoverageTestSupport.analysisOfCode(
            """
            class Greeter {
                fun greet(name: String?, mode: String): String {
                    if (name == null) return "nobody"
                    if (mode == "loud") return name.uppercase()
                    if (name.isEmpty()) return "empty"
                    return name
                }
            }
            """
        )
        val hints = BoundaryConditionExtractor().extract(analysis.functions.first { it.info.name == "greet" })

        assertTrue(hints.values.getValue("name").contains(null))
        assertTrue(hints.values.getValue("name").contains(""))
        assertTrue(hints.values.getValue("mode").containsAll(listOf("loud", "loudx")))
    }

    @Test
    fun `resolves top-level constants and when subjects`() {
        val analysis = CoverageTestSupport.analysisOfCode(
            """
            const val LIMIT = 50

            fun classify(level: Int): String = when (level) {
                LIMIT -> "limit"
                in 1..9 -> "low"
                else -> "other"
            }
            """
        )
        val hints = BoundaryConditionExtractor().extract(analysis.functions.first { it.info.name == "classify" })

        assertTrue(hints.values.getValue("level").containsAll(listOf("49", "50", "51", "1", "9")), hints.values.toString())
    }
}
