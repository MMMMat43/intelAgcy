public class OrderProcessor {

    private static final double MEMBER_DISCOUNT_THRESHOLD = 100.0;
    private static final int MAX_QUANTITY_PER_ORDER = 1000;

    /**
     * Вычисляет итоговую скидку на заказ с учётом статуса участника
     * программы лояльности, количества товара и суммы заказа.
     * Несколько вложенных условий -> высокая цикломатическая сложность.
     */
    public double calculateDiscount(double price, int quantity, boolean isMember) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("Quantity must be positive");
        }
        if (price < 0) {
            throw new IllegalArgumentException("Price cannot be negative");
        }

        double total = price * quantity;
        double discount = 0.0;

        if (isMember) {
            if (total >= MEMBER_DISCOUNT_THRESHOLD) {
                if (quantity >= 10) {
                    discount = total * 0.20;
                } else {
                    discount = total * 0.15;
                }
            } else {
                discount = total * 0.05;
            }
        } else {
            if (total >= MEMBER_DISCOUNT_THRESHOLD * 2) {
                discount = total * 0.10;
            }
        }

        return discount;
    }

    /**
     * Проверяет корректность заказа перед оформлением.
     * Бросает разные исключения в зависимости от типа нарушения.
     */
    public boolean validateOrder(int quantity, double price) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("Quantity must be positive");
        }
        if (quantity > MAX_QUANTITY_PER_ORDER) {
            throw new IllegalStateException("Quantity exceeds maximum allowed per order");
        }
        if (price <= 0) {
            throw new IllegalArgumentException("Price must be positive");
        }
        return true;
    }

    /**
     * Относит клиента к одной из категорий лояльности на основе истории
     * покупок. Несколько взаимоисключающих веток (switch-подобная логика
     * через if/else if).
     */
    public String categorizeCustomer(int purchaseCount, double totalSpent) {
        if (purchaseCount <= 0) {
            return "NEW";
        } else if (purchaseCount < 5) {
            return "BRONZE";
        } else if (purchaseCount < 20 && totalSpent < 5000.0) {
            return "SILVER";
        } else if (totalSpent >= 5000.0) {
            return "PLATINUM";
        } else {
            return "GOLD";
        }
    }

    /**
     * Списывает товар со склада, пытаясь удовлетворить запрос частями,
     * если единой партии не хватает. Демонстрирует цикл и обработку
     * деления на количество партий.
     */
    public int processInventory(int currentStock, int requestedQuantity) {
        if (requestedQuantity <= 0) {
            throw new IllegalArgumentException("Requested quantity must be positive");
        }
        if (currentStock < 0) {
            throw new IllegalStateException("Stock cannot be negative");
        }

        int remaining = requestedQuantity;
        int fulfilled = 0;
        int batchSize = currentStock > 0 ? currentStock / 4 + 1 : 1;

        for (int i = 0; i < 10 && remaining > 0; i++) {
            int take = Math.min(batchSize, Math.min(remaining, currentStock - fulfilled));
            if (take <= 0) {
                break;
            }
            fulfilled += take;
            remaining -= take;
        }

        return fulfilled;
    }

    /**
     * Определяет, требуется ли эскалация проблемы с заказом службе
     * поддержки, комбинируя несколько условий с логическими операторами.
     */
    public boolean requiresEscalation(int daysSinceOrder, boolean isVip, double orderValue) {
        boolean overdue = daysSinceOrder > 7;
        boolean highValue = orderValue > 1000.0;

        if (isVip && (overdue || highValue)) {
            return true;
        }
        if (!isVip && overdue && highValue) {
            return true;
        }
        return daysSinceOrder > 30;
    }
}
