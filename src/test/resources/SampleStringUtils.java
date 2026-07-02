public class SampleStringUtils {

    public String normalize(String input) {
        if (input == null) {
            throw new IllegalArgumentException("Input must not be null");
        }
        String trimmed = input.trim();
        if (trimmed.isEmpty()) {
            return "";
        }
        return trimmed.toLowerCase();
    }

    public boolean containsDigit(String text) {
        for (int i = 0; i < text.length(); i++) {
            if (Character.isDigit(text.charAt(i))) {
                return true;
            }
        }
        return false;
    }
}
