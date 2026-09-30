class OrderProcessor {

    companion object {
        const val MEMBER_DISCOUNT_THRESHOLD = 100.0
        const val MAX_QUANTITY_PER_ORDER = 1000
    }

    fun calculateDiscount(price: Double, quantity: Int, isMember: Boolean): Double {
        if (quantity <= 0) {
            throw IllegalArgumentException("Quantity must be positive")
        }
        if (price < 0) {
            throw IllegalArgumentException("Price cannot be negative")
        }

        val total = price * quantity
        var discount = 0.0

        if (isMember) {
            if (total >= MEMBER_DISCOUNT_THRESHOLD) {
                discount = if (quantity >= 10) {
                    total * 0.20
                } else {
                    total * 0.15
                }
            } else {
                discount = total * 0.05
            }
        } else {
            if (total >= MEMBER_DISCOUNT_THRESHOLD * 2) {
                discount = total * 0.10
            }
        }

        return discount
    }

    fun validateOrder(quantity: Int, price: Double): Boolean {
        if (quantity <= 0) {
            throw IllegalArgumentException("Quantity must be positive")
        }
        if (quantity > MAX_QUANTITY_PER_ORDER) {
            throw IllegalStateException("Quantity exceeds maximum allowed per order")
        }
        if (price <= 0) {
            throw IllegalArgumentException("Price must be positive")
        }
        return true
    }

    fun categorizeCustomer(purchaseCount: Int, totalSpent: Double): String {
        return if (purchaseCount <= 0) {
            "NEW"
        } else if (purchaseCount < 5) {
            "BRONZE"
        } else if (purchaseCount < 20 && totalSpent < 5000.0) {
            "SILVER"
        } else if (totalSpent >= 5000.0) {
            "PLATINUM"
        } else {
            "GOLD"
        }
    }

    fun processInventory(currentStock: Int, requestedQuantity: Int): Int {
        if (requestedQuantity <= 0) {
            throw IllegalArgumentException("Requested quantity must be positive")
        }
        if (currentStock < 0) {
            throw IllegalStateException("Stock cannot be negative")
        }

        var remaining = requestedQuantity
        var fulfilled = 0
        val batchSize = if (currentStock > 0) currentStock / 4 + 1 else 1

        for (i in 0 until 10) {
            if (remaining <= 0) {
                break
            }
            val take = minOf(batchSize, minOf(remaining, currentStock - fulfilled))
            if (take <= 0) {
                break
            }
            fulfilled += take
            remaining -= take
        }

        return fulfilled
    }

    fun requiresEscalation(daysSinceOrder: Int, isVip: Boolean, orderValue: Double): Boolean {
        val overdue = daysSinceOrder > 7
        val highValue = orderValue > 1000.0

        if (isVip && (overdue || highValue)) {
            return true
        }
        if (!isVip && overdue && highValue) {
            return true
        }
        return daysSinceOrder > 30
    }
}
