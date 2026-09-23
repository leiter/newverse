package com.together.newverse.android.data

import com.together.newverse.data.parser.BnnParser
import com.together.newverse.domain.model.Article
import com.together.newverse.domain.model.Product
import com.together.newverse.domain.model.ProductCategory
import com.together.newverse.domain.model.ProductPricing
import com.together.newverse.domain.model.ProductUnit
import com.together.newverse.domain.model.SellerArticle
import com.together.newverse.domain.model.SellerArticleData

/**
 * The complete Terra price list "Obst & Gemüse KW 31/26" (317 rows) at a deliberately low
 * price: every article is uploaded, only the 30 hand-picked [offerArticles] are available.
 *
 * The BNN file is supplier data and stays out of git. Copy it to
 * `tmp/androidTest-assets/terra/plf.bnn` (same file as `tmp/plf.bnn`); the androidTest
 * source set packs that folder as assets, see `androidApp/build.gradle.kts`.
 *
 * Prices follow [ProductPricing.sellPrice] like everywhere else, with the markup of the
 * article's [PriceTier]. The tiers target the organic range of a standard supermarket
 * (REWE/Edeka Bio, Aldi/Lidl Bio), which keeps them below a bio supermarket. Where the
 * supermarket sells organic at or below Terra's list price plus VAT, the floor
 * [LOW_PRICE_MIN_MARKUP] applies.
 * Reference prices: supermarktcheck.de, last reported prices as of September 2026.
 */

/** No article is ever sold with less than 15 % on the supplier price. */
const val LOW_PRICE_MIN_MARKUP = 1.15

/** Asset path of the Terra BNN file, see the file comment. */
const val LOW_PRICE_BNN_ASSET = "terra/plf.bnn"

/** Rows in the BNN file, header and end marker excluded. */
const val LOW_PRICE_ARTICLE_COUNT = 317

enum class PriceTier(val markupFactor: Double) {
    /**
     * Staples the supermarkets sell cheapest: potatoes, carrots, onions, apples, bananas,
     * citrus, eggs, cabbage, berries. Bio bananas 1,59-1,99 €/kg vs. Terra 1,59 net,
     * Bio carrots 1,19-1,39 €/kg vs. 1,71 net — the floor is already above the supermarket.
     */
    BASIS(LOW_PRICE_MIN_MARKUP),

    /**
     * Where the supermarket has room: stone fruit, zucchini, exotics, bunched herbs,
     * mushrooms. Bio zucchini 2,58-4,98 €/kg vs. 2,71 here, flat peaches 5,58-5,98 vs. 5,26,
     * champignons 9,96-12,45 €/kg vs. 9,31.
     */
    FRISCH(1.30),

    /**
     * High supermarket margin: head lettuce, broccoli, bell peppers. Bio iceberg lettuce
     * 1,99-2,49 € vs. 1,51 here, broccoli 6,63-8,30 €/kg vs. 6,40, pepper mix 7,23-8,73 €/kg
     * vs. 6,39.
     */
    MARGE(1.50)
}

/**
 * How one BNN row is priced and filed. [weightPerPiece] is the fallback piece weight in kg
 * for rows whose name carries no pack weight; the buyer app divides kg by it.
 */
class PriceRule(
    val pattern: Regex,
    val category: ProductCategory,
    val tier: PriceTier,
    val weightPerPiece: Double
)

private fun rule(pattern: String, category: ProductCategory, tier: PriceTier, weight: Double) =
    PriceRule(Regex(pattern, RegexOption.IGNORE_CASE), category, tier, weight)

private val OBST = ProductCategory.OBST
private val GEMUESE = ProductCategory.GEMUESE
private val KARTOFFELN = ProductCategory.KARTOFFELN
private val SALAT = ProductCategory.SALAT
private val KRAEUTER = ProductCategory.KRAEUTER
private val PILZE = ProductCategory.PILZE
private val EIER = ProductCategory.EIER
private val KONSERVEN = ProductCategory.KONSERVEN
private val SONSTIGES = ProductCategory.SONSTIGES
private val BASIS = PriceTier.BASIS
private val FRISCH = PriceTier.FRISCH
private val MARGE = PriceTier.MARGE

/** Matched against the BNN product name, first match wins: specific names go first. */
val lowPriceRules: List<PriceRule> = listOf(
    // Terra's gastro range: peeled, cut and pre-cooked, sold per kg bag
    rule("^>.*kartoffel|^vorgegarte kartoffel", KARTOFFELN, BASIS, 1.0),
    rule("^>", GEMUESE, BASIS, 1.0),

    // Obst
    rule("granat", OBST, FRISCH, 0.400),
    rule("zitronen(?!melisse)", OBST, BASIS, 0.120),
    rule("limette", OBST, BASIS, 0.070),
    rule("^(saft-)?orange", OBST, BASIS, 0.220),
    rule("apfel", OBST, BASIS, 0.170),
    rule("birne", OBST, BASIS, 0.180),
    rule("aprikose", OBST, FRISCH, 0.050),
    rule("pfirsich", OBST, FRISCH, 0.150),
    rule("nektarine", OBST, FRISCH, 0.150),
    rule("pflaume", OBST, FRISCH, 0.050),
    rule("zwetschge", OBST, FRISCH, 0.035),
    rule("kirsche", OBST, FRISCH, 0.008),
    rule("heidelbeere", OBST, BASIS, 0.002),
    rule("himbeere|brombeere|johannisbeere", OBST, BASIS, 0.003),
    rule("mini-avocado", OBST, FRISCH, 0.120),
    rule("avocado", OBST, FRISCH, 0.200),
    rule("kiwi", OBST, BASIS, 0.100),
    rule("mini-wassermelone", OBST, FRISCH, 1.500),
    rule("wassermelone", OBST, FRISCH, 3.000),
    rule("melone", OBST, FRISCH, 1.200),
    rule("baby-banane", OBST, BASIS, 0.080),
    rule("banane", OBST, BASIS, 0.180),
    rule("trauben", OBST, BASIS, 0.500),
    rule("datteln am zweig", OBST, BASIS, 0.500),
    rule("dattel", OBST, BASIS, 0.020),
    rule("mango", OBST, FRISCH, 0.400),
    rule("papaya", OBST, FRISCH, 0.500),
    rule("maracuja|passionsfrucht", OBST, FRISCH, 0.060),
    rule("aloe", SONSTIGES, FRISCH, 0.400),
    rule("maronen", SONSTIGES, FRISCH, 0.200),
    rule("walnüsse", SONSTIGES, BASIS, 0.012),

    // Kräuter, Sprossen — before the vegetables whose names they contain
    rule("im topf", KRAEUTER, BASIS, 0.100),
    rule("sprossen|mischung|friedrichshainer", GEMUESE, BASIS, 0.050),
    rule("microgreens", KRAEUTER, BASIS, 0.050),
    rule("kresse", KRAEUTER, BASIS, 0.050),
    rule("wurzelpetersilie", GEMUESE, BASIS, 0.120),
    rule(
        "petersilie|schnittlauch|bohnenkraut|basilikum|dill|estragon|koriander|lavendel|" +
            "melisse|oregano|minze|rosmarin|salbei|sauerampfer|thymian",
        KRAEUTER, FRISCH, 0.030
    ),

    // Wurzeln, Knollen, Zwiebeln
    rule("möhre", GEMUESE, BASIS, 0.090),
    rule("radieschen", GEMUESE, FRISCH, 0.250),
    rule("rettich", GEMUESE, FRISCH, 0.350),
    rule("fenchel", GEMUESE, BASIS, 0.300),
    rule("kohlrabi", GEMUESE, FRISCH, 0.300),
    rule("rote bete", GEMUESE, BASIS, 0.200),
    rule("knollensellerie", GEMUESE, BASIS, 0.600),
    rule("stangensellerie", GEMUESE, BASIS, 0.500),
    rule("süßkartoffel", KARTOFFELN, BASIS, 0.350),
    rule("kartoffel", KARTOFFELN, BASIS, 0.080),
    rule("ingwer", GEMUESE, FRISCH, 0.080),
    rule("kurkuma", GEMUESE, FRISCH, 0.030),
    rule("lauchzwiebel", GEMUESE, FRISCH, 0.150),
    rule("gemüsezwiebel", GEMUESE, BASIS, 0.300),
    rule("schalotte", GEMUESE, BASIS, 0.030),
    rule("zwiebel", GEMUESE, BASIS, 0.110),
    rule("schwarzer knoblauch", KONSERVEN, BASIS, 0.060),
    rule("knoblauch", GEMUESE, FRISCH, 0.060),
    rule("lauch|porree", GEMUESE, FRISCH, 0.300),

    // Salate, Blattgemüse
    rule("feldsalat", SALAT, BASIS, 0.080),
    rule("chicor", SALAT, BASIS, 0.150),
    rule("rucola", SALAT, FRISCH, 0.100),
    rule("mixsalat|schnittsalat", SALAT, BASIS, 0.100),
    rule("radicchio", SALAT, FRISCH, 0.300),
    rule(
        "romanasalat|kopfsalat|bataviasalat|eichblatt|salanova|mix-salat|salat-mix|eisbergsalat",
        SALAT, MARGE, 0.300
    ),
    rule("spinat", SALAT, FRISCH, 0.250),
    rule("mangold", GEMUESE, FRISCH, 0.500),

    // Kohl
    rule("bimi", GEMUESE, BASIS, 0.200),
    rule("rotkohl", GEMUESE, BASIS, 0.900),
    rule("weißkohl", GEMUESE, BASIS, 1.500),
    rule("spitzkohl", GEMUESE, BASIS, 0.800),
    rule("wirsing|chinakohl", GEMUESE, BASIS, 1.000),
    rule("blumenkohl", GEMUESE, FRISCH, 0.800),
    rule("broccoli|brokkoli", GEMUESE, MARGE, 0.400),
    rule("sauerkraut|kimchi|küstengold", KONSERVEN, BASIS, 0.410),

    // Fruchtgemüse
    rule("cherry|cocktail", GEMUESE, BASIS, 0.020),
    rule("fleischtomate", GEMUESE, BASIS, 0.250),
    rule("tomate", GEMUESE, BASIS, 0.100),
    rule("aubergine", GEMUESE, BASIS, 0.300),
    rule("bratpaprika", GEMUESE, BASIS, 0.010),
    rule("peperoni", GEMUESE, BASIS, 0.020),
    rule("spitzpaprika", GEMUESE, BASIS, 0.120),
    rule("snack-paprika", GEMUESE, BASIS, 0.030),
    // German greenhouse peppers cost almost twice the Spanish ones: floor only
    rule("^roter paprika", GEMUESE, BASIS, 0.200),
    rule("paprika", GEMUESE, MARGE, 0.200),
    rule("mini-salatgurke", GEMUESE, BASIS, 0.150),
    rule("salatgurke", GEMUESE, BASIS, 0.400),
    rule("gurke", GEMUESE, BASIS, 0.300),
    rule("zucchiniblüte", GEMUESE, BASIS, 0.100),
    rule("zucchini", GEMUESE, FRISCH, 0.300),
    rule("patisson", GEMUESE, BASIS, 0.500),
    rule("kürbis", GEMUESE, BASIS, 1.000),
    rule("bohnen", GEMUESE, FRISCH, 0.005),
    rule("zuckermais", GEMUESE, BASIS, 0.300),

    // Pilze
    rule("champignon", PILZE, FRISCH, 0.020),
    rule("austernpilz", PILZE, FRISCH, 0.030),
    rule("portobello|kräuterseitling|pfifferling|shiitake", PILZE, BASIS, 0.030),

    // Eier
    rule("picknick-eier", EIER, BASIS, 0.360),
    rule("eier", EIER, BASIS, 0.060)
)

/** The rule for a BNN product name, or null when no rule covers it. */
fun lowPriceRuleFor(bnnName: String): PriceRule? = lowPriceRules.firstOrNull { it.pattern.containsMatchIn(bnnName) }

/**
 * Builds all [LOW_PRICE_ARTICLE_COUNT] articles from the BNN file content.
 * The 30 [offerArticles] keep their hand-written data and stay available, re-priced by
 * their tier; every other row is uploaded unavailable.
 */
fun lowPriceArticles(bnnContent: String): List<SellerArticle> {
    val handPicked = offerArticles.associateBy { it.article.productId }
    return BnnParser().parse(bnnContent).map { product ->
        val rule = lowPriceRuleFor(product.productName)
            ?: error("No price rule for BNN row ${product.productId} '${product.productName}'")
        handPicked[product.productId]?.let { reprice(it, rule.tier) }
            ?: fromBnn(product, rule)
    }
}

private fun reprice(offer: SellerArticle, tier: PriceTier): SellerArticle {
    val sellerData = requireNotNull(offer.sellerData)
    val article = offer.article
    return SellerArticle(
        article = article.copy(
            available = true,
            price = ProductPricing.sellPrice(sellerData.acquirePrice, tier.markupFactor, article.taxRate)
        ),
        sellerData = sellerData.copy(markupFactor = tier.markupFactor)
    )
}

/** "Kopfsalat, grün  10 St" or "Avocado Hass  18-20 St": pieces in a Kiste. */
private val PIECES_IN_NAME = Regex("""(\d+)(?:\s*-\s*(\d+))?\s*St\b""")

/** "1,5 kg Netz", "4 x100 g-Schale", "200g-Schachtel": pack weight in the name. */
private val WEIGHT_IN_NAME = Regex("""(\d+(?:,\d+)?)\s*(kg|g)\b""", RegexOption.IGNORE_CASE)

private fun fromBnn(product: Product, rule: PriceRule): SellerArticle {
    val bnnUnit = product.unit.uppercase()
    // A Kiste is Terra's trade unit only: sold per piece, at the per-piece list price
    val piecesPerCase = if (bnnUnit == "KI") piecesInName(product.productName) else 1.0
    val unit = when (bnnUnit) {
        "KG" -> ProductUnit.KG
        "BD" -> ProductUnit.BUND
        "BT", "NE" -> ProductUnit.BEUTEL
        "SC", "SCH" -> ProductUnit.SCHALE
        "GL" -> ProductUnit.GLAS
        else -> ProductUnit.STUECK // ST, KI, PA (Packung), TO (Topf), KT (Karton)
    }
    val weightPerPiece = when {
        unit == ProductUnit.KG || bnnUnit == "KI" -> rule.weightPerPiece
        else -> weightInName(product.productName) ?: rule.weightPerPiece
    }
    val acquirePrice = roundCents(product.acquirePrice / piecesPerCase)
    val markup = rule.tier.markupFactor
    val article = Article(
        id = "",
        productId = product.productId,
        productName = OFFER_NAME_PREFIX + displayName(product.productName, bnnUnit),
        available = false,
        unit = unit.displayName,
        price = ProductPricing.sellPrice(acquirePrice, markup, product.taxRate),
        weightPerPiece = weightPerPiece,
        imageUrl = "",
        category = rule.category.displayName,
        searchTerms = product.searchTerms,
        detailInfo = product.detailInfo,
        taxRate = product.taxRate
    )
    return SellerArticle(
        article = article,
        sellerData = SellerArticleData(
            acquirePrice = acquirePrice,
            markupFactor = markup,
            supplier = product.supplier,
            origin = product.origin,
            certification = product.certification,
            quality = product.quality,
            barcode = product.barcode.orEmpty(),
            packageSize = product.packageSize * piecesPerCase
        )
    )
}

private fun piecesInName(name: String): Double {
    val match = PIECES_IN_NAME.find(name) ?: error("Kiste without a piece count: '$name'")
    val low = match.groupValues[1].toDouble()
    val high = match.groupValues[2].toDoubleOrNull() ?: low
    return (low + high) / 2.0
}

private fun weightInName(name: String): Double? {
    val match = WEIGHT_IN_NAME.find(name) ?: return null
    val amount = match.groupValues[1].replace(',', '.').toDouble()
    return if (match.groupValues[2].equals("kg", ignoreCase = true)) amount else amount / 1000.0
}

/** " 10 St", ", 26 St", " 12x6 St", " /32 St", ", 1,3 Kg/ 12-15 St": the pieces in Terra's trade unit. */
private const val TRADE_COUNT = """[,/\s]+(\d+(,\d+)?\s*Kg/\s*)?(\d+\s*x\s*)?\d+(\s*-\s*\d+)?\s*St\b"""

/**
 * Drops Terra's order markers ("AKTION:", the gastro ">") and the pieces per trade unit:
 * at the end of any name, and with everything after it for a Kiste, which is sold per piece.
 * Pack contents like "2 St/ 250g" stay.
 */
private fun displayName(bnnName: String, bnnUnit: String): String {
    val tradeCount = if (bnnUnit == "KI") Regex("$TRADE_COUNT.*$") else Regex("$TRADE_COUNT\\s*$")
    return bnnName.removePrefix("AKTION:").trimStart('>', ' ')
        .replace(tradeCount, "")
        .replace(Regex("""\s+"""), " ")
        .trim()
}

/** Half up, like [ProductPricing.sellPrice]. */
private fun roundCents(value: Double): Double = kotlin.math.floor(value * 100.0 + 0.5) / 100.0
