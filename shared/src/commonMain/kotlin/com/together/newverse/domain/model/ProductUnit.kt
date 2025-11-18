package com.together.newverse.domain.model

/**
 * Units of measurement for products
 * Based on common German produce units
 */
enum class ProductUnit(val displayName: String, val isCountable: Boolean) {
    KG("kg", false),              // Kilogram (weight)
    G("g", false),                // Gram (weight)
    L("L", false),                // Liter (volume)
    ML("ml", false),              // Milliliter (volume)
    STUECK("Stück", true),        // Pieces (countable)
    BUND("Bund", true),           // Bunch (countable)
    BEUTEL("Beutel", true),       // Bag (countable)
    SCHALE("Schale", true),       // Tray/Container (countable)
    KASTEN("Kasten", true),       // Crate/Box (countable)
    GLAS("Glas", true),           // Jar (countable)
    FLASCHE("Flasche", true),     // Bottle (countable)
    DOSE("Dose", true);           // Can (countable)

    companion object {
        /**
         * Get all unit display names as a list
         */
        fun getAllDisplayNames(): List<String> = entries.map { it.displayName }

        /**
         * Find unit by display name
         */
        fun fromDisplayName(displayName: String): ProductUnit? {
            return entries.find {
                it.displayName.equals(displayName, ignoreCase = true)
            }
        }

        /**
         * Get unit from string (for backward compatibility)
         */
        fun fromString(unit: String): ProductUnit {
            return when (unit.lowercase().trim()) {
                "kg", "kilogram", "kilogramm" -> KG
                "g", "gram", "gramm" -> G
                "l", "liter" -> L
                "ml", "milliliter" -> ML
                "stück", "stueck", "st", "stck" -> STUECK
                "bund", "bd" -> BUND
                "beutel", "bt", "btl" -> BEUTEL
                "schale", "sc", "sch" -> SCHALE
                "kasten", "kst" -> KASTEN
                "glas", "gl" -> GLAS
                "flasche", "fl" -> FLASCHE
                "dose", "ds" -> DOSE
                else -> STUECK // Default to pieces
            }
        }

        /**
         * Get countable units (pieces, bunches, etc.)
         */
        fun getCountableUnits(): List<ProductUnit> {
            return entries.filter { it.isCountable }
        }

        /**
         * Get weight/volume units (kg, g, L, ml)
         */
        fun getMeasurableUnits(): List<ProductUnit> {
            return entries.filter { !it.isCountable }
        }
    }
}
