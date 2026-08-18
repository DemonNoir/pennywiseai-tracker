package com.pennywiseai.tracker.utils

import java.math.BigDecimal
import java.text.NumberFormat
import java.util.Currency
import java.util.Locale

/**
 * Utility functions for currency formatting
 */
object CurrencyUtils {
    
    private val thaiLocale = Locale.Builder().setLanguage("th").setRegion("TH").build()
    private val thaiCurrencyFormat = NumberFormat.getCurrencyInstance(thaiLocale).apply {
        currency = Currency.getInstance("THB")
        maximumFractionDigits = 0 // No decimal places for whole amounts
    }
    
    /**
     * Formats a BigDecimal amount as Thai Baht
     * @param amount The amount to format
     * @return Formatted string like "฿1,234"
     */
    fun formatCurrency(amount: BigDecimal): String {
        // For amounts with decimals, show them
        return if (amount.stripTrailingZeros().scale() > 0) {
            val formatter = NumberFormat.getCurrencyInstance(thaiLocale).apply {
                currency = Currency.getInstance("THB")
                maximumFractionDigits = 2
                minimumFractionDigits = 1
            }
            formatter.format(amount)
        } else {
            thaiCurrencyFormat.format(amount)
        }
    }
    
    /**
     * Formats a Double amount as Thai Baht
     */
    fun formatCurrency(amount: Double): String {
        return formatCurrency(BigDecimal.valueOf(amount))
    }
    
    /**
     * Formats an Int amount as Thai Baht
     */
    fun formatCurrency(amount: Int): String {
        return formatCurrency(BigDecimal(amount))
    }
    
    /**
     * Formats an amount with a custom number of decimal places
     */
    fun formatCurrency(amount: BigDecimal, decimalPlaces: Int): String {
        val formatter = NumberFormat.getCurrencyInstance(thaiLocale).apply {
            currency = Currency.getInstance("THB")
            maximumFractionDigits = decimalPlaces
            minimumFractionDigits = decimalPlaces
        }
        return formatter.format(amount)
    }

    /**
     * Sorts a list of currency codes with THB prioritized first, then alphabetically.
     * This is the standard sorting for currency lists throughout the app.
     *
     * @param currencies List of currency codes to sort
     * @return Sorted list with THB first (if present), then alphabetically
     *
     * Example:
     * ```
     * sortCurrencies(listOf("USD", "EUR", "THB", "GBP"))
     * // Returns: ["THB", "EUR", "GBP", "USD"]
     * ```
     */
    fun sortCurrencies(currencies: List<String>): List<String> {
        return currencies.sortedWith { a, b ->
            when {
                a == "THB" -> -1 // THB first
                b == "THB" -> 1
                else -> a.compareTo(b) // Alphabetical for others
            }
        }
    }

    /**
     * Returns a comprehensive list of all supported currencies.
     * Includes currencies from supported banks and common international currencies.
     *
     * @return List of currency codes sorted with THB first, then alphabetically
     */
    fun getAllSupportedCurrencies(): List<String> {
        // Keep this in sync with the currencies our bank parsers emit
        // (BankParser.getCurrency()) so anything the app can auto-import is also
        // manually selectable. Every code here must be a valid ISO 4217 code.
        val currencies = listOf(
            // Major currencies
            "INR", "USD", "EUR", "GBP", "JPY", "CNY",
            // Middle East
            "AED", "SAR", "KWD", "OMR", "IRR", "JOD", "BHD",
            // South Asia
            "NPR", "LKR", "BDT", "PKR",
            // Asia Pacific
            "SGD", "AUD", "THB", "MYR", "KRW",
            // Americas
            "CAD", "MXN", "COP", "BRL", "ARS", "CLP", "UYU",
            // Africa
            "ETB", "KES", "NGN", "TZS", "MZN", "EGP",
            // Europe
            "BYN", "CZK", "RUB", "TRY"
        )
        return sortCurrencies(currencies)
    }
}
