class SampleCalculator {

    fun divide(numerator: Int, denominator: Int): Int {
        if (denominator == 0) {
            throw ArithmeticException("Division by zero")
        }
        var result = 0
        for (i in 0 until numerator) {
            if (i % denominator == 0) {
                result++
            }
        }
        return result
    }

    fun isPositive(value: Int): Boolean {
        return if (value > 0) {
            true
        } else {
            false
        }
    }
}
