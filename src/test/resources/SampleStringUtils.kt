class SampleStringUtils {

    fun normalize(input: String?): String {
        if (input == null) {
            throw IllegalArgumentException("Input must not be null")
        }
        val trimmed = input.trim()
        if (trimmed.isEmpty()) {
            return ""
        }
        return trimmed.lowercase()
    }

    fun containsDigit(text: String): Boolean {
        for (i in 0 until text.length) {
            if (text[i].isDigit()) {
                return true
            }
        }
        return false
    }
}
