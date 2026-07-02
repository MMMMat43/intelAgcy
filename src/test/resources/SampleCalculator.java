public class SampleCalculator {

    public int divide(int numerator, int denominator) {
        if (denominator == 0) {
            throw new ArithmeticException("Division by zero");
        }
        int result = 0;
        for (int i = 0; i < numerator; i++) {
            if (i % denominator == 0) {
                result++;
            }
        }
        return result;
    }

    public boolean isPositive(int value) {
        if (value > 0) {
            return true;
        } else {
            return false;
        }
    }
}
