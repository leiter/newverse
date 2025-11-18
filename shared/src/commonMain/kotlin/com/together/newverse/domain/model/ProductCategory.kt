package com.together.newverse.domain.model

/**
 * Product categories for organizing articles
 * Based on common produce and food categories
 */
enum class ProductCategory(val displayName: String) {
    OBST("Obst"),                   // Fruit
    GEMUESE("Gemüse"),              // Vegetables
    KARTOFFELN("Kartoffeln"),        // Potatoes
    SALAT("Salat"),                  // Salad/Lettuce
    KRAEUTER("Kräuter"),            // Herbs
    PILZE("Pilze"),                  // Mushrooms
    EIER("Eier"),                    // Eggs
    MILCHPRODUKTE("Milchprodukte"), // Dairy products
    FLEISCH("Fleisch"),              // Meat
    BACKWAREN("Backwaren"),          // Bakery goods
    GETRAENKE("Getränke"),          // Beverages
    KONSERVEN("Konserven"),          // Preserves/Canned goods
    SONSTIGES("Sonstiges");          // Other

    companion object {
        /**
         * Get all category display names as a list
         */
        fun getAllDisplayNames(): List<String> = entries.map { it.displayName }

        /**
         * Find category by display name
         */
        fun fromDisplayName(displayName: String): ProductCategory? {
            return entries.find { it.displayName == displayName }
        }

        /**
         * Get category from string (for backward compatibility with old data)
         * Maps specific product names to general categories
         */
        fun fromString(category: String): ProductCategory {
            return when (category.lowercase()) {
                // Fruit
                "äpfel", "apfel", "birnen", "birne", "bananen", "banane",
                "erdbeeren", "erdbeere", "kirschen", "kirsche", "pflaumen", "pflaume",
                "trauben", "weintrauben", "orangen", "zitronen", "limetten",
                "pfirsiche", "pfirsich", "nektarinen", "nektarine", "melonen", "melone",
                "beeren", "himbeeren", "brombeeren", "heidelbeeren", "johannisbeeren"
                -> OBST

                // Vegetables
                "tomaten", "tomate", "gurken", "gurke", "paprika", "zwiebeln", "zwiebel",
                "karotten", "karotte", "möhren", "möhre", "zucchini", "auberginen", "aubergine",
                "brokkoli", "blumenkohl", "kohlrabi", "kohl", "weißkohl", "rotkohl",
                "sellerie", "stangensellerie", "knollensellerie", "lauch", "porree",
                "bohnen", "erbsen", "linsen", "kürbis", "kürbisse", "rote beete", "rüben",
                "radieschen", "rettich", "fenchel", "spargel", "artischocken", "artischocke",
                "zuckermais", "mais", "spinat", "mangold"
                -> GEMUESE

                // Potatoes
                "kartoffeln", "kartoffel", "süßkartoffeln", "süßkartoffel"
                -> KARTOFFELN

                // Salad
                "salat", "blattsalat", "kopfsalat", "eisbergsalat", "feldsalat",
                "rucola", "rukola", "lollo rosso", "radicchio", "endivien", "römersalat",
                "chicorée"
                -> SALAT

                // Herbs
                "petersilie", "basilikum", "schnittlauch", "dill", "koriander",
                "minze", "pfefferminze", "oregano", "thymian", "rosmarin", "salbei",
                "majoran", "estragon", "liebstöckel", "bärlauch", "kräuter"
                -> KRAEUTER

                // Mushrooms
                "champignons", "champignon", "pilze", "pilz", "pfifferlinge",
                "steinpilze", "austernpilze", "shiitake"
                -> PILZE

                // Eggs
                "eier", "ei"
                -> EIER

                // Dairy
                "milch", "käse", "butter", "sahne", "schmand", "joghurt",
                "quark", "frischkäse", "molkereiprodukte", "milchprodukte"
                -> MILCHPRODUKTE

                // Meat
                "fleisch", "wurst", "schinken", "geflügel", "huhn", "hähnchen",
                "rind", "rindfleisch", "schwein", "schweinefleisch", "lamm", "lammfleisch"
                -> FLEISCH

                // Bakery
                "brot", "brötchen", "kuchen", "gebäck", "backwaren"
                -> BACKWAREN

                // Beverages
                "saft", "wasser", "tee", "kaffee", "milch", "getränke", "getränk",
                "limonade", "bier", "wein"
                -> GETRAENKE

                // Preserves
                "marmelade", "konfitüre", "honig", "konserven", "eingelegtes",
                "eingemachtes", "kompott"
                -> KONSERVEN

                else -> SONSTIGES
            }
        }
    }
}
