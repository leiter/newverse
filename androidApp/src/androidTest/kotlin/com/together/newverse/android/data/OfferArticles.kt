package com.together.newverse.android.data

import com.together.newverse.domain.model.Article
import com.together.newverse.domain.model.ProductCategory
import com.together.newverse.domain.model.ProductUnit
import com.together.newverse.domain.model.TaxRate

/**
 * The 30 long-storable fruit & vegetable articles from `offer.md`
 * (Terra Naturkost Handels KG, BNN price list "Obst & Gemüse KW 31/26",
 * valid 27.07.-02.08.2026, source file `tmp/plf.bnn`).
 *
 * [acquirePrice] is the supplier's net list price per base unit as printed in the BNN file.
 * [Article.price] is never written by hand — it is derived by [offerArticle] using the exact
 * same formula the seller app uses in CreateProductViewModel.recalculateSellPrice:
 *
 *     price = acquirePrice * markupFactor * (1 + taxRate)
 *
 * so a product uploaded here reopens in the seller form without the price jumping.
 */

/** Default gross margin applied to the supplier list price. Adjust for the real offer. */
const val OFFER_MARKUP_FACTOR = 1.45

/** Set to e.g. "TEST-" to make uploaded articles easy to spot and delete again. */
const val OFFER_NAME_PREFIX = ""

/**
 * Builds an [Article] with a sell price derived from the supplier price, so the offer data
 * cannot drift out of sync with the markup.
 */
fun offerArticle(
    productId: String,
    productName: String,
    acquirePrice: Double,
    unit: ProductUnit,
    category: ProductCategory,
    searchTerms: String,
    detailInfo: String,
    weightPerPiece: Double,
    markupFactor: Double = OFFER_MARKUP_FACTOR,
    taxRate: TaxRate = TaxRate.REDUCED
): Article {
    val gross = acquirePrice * markupFactor * (1.0 + taxRate.rate)
    val price = (gross * 100).toLong() / 100.0
    return Article(
        id = "",
        productId = productId,
        productName = OFFER_NAME_PREFIX + productName,
        available = true,
        unit = unit.displayName,
        price = price,
        weightPerPiece = weightPerPiece,
        imageUrl = "",
        category = category.displayName,
        searchTerms = searchTerms,
        detailInfo = detailInfo,
        acquirePrice = acquirePrice,
        markupFactor = markupFactor,
        taxRate = taxRate.rate
    )
}

/** Expected size of [offerArticles] — kept as a constant so the test fails loudly on edits. */
const val OFFER_ARTICLE_COUNT = 30

/**
 * Article 1-30 of `offer.md`, in the same order: 12 Obst & Nüsse, then 18 Gemüse & Wurzeln.
 */
val offerArticles: List<Article> = listOf(

    // ---------------------------------------------------------------- Obst & Nüsse (12)

    offerArticle(
        productId = "112108", productName = "Apfel Topaz",
        acquirePrice = 1.96, unit = ProductUnit.KG, category = ProductCategory.OBST,
        searchTerms = "apfel,äpfel,topaz,obst,lagerapfel",
        detailInfo = "Bio-Apfel der Sorte Topaz aus Deutschland. Robuste Lagersorte, " +
            "säuerlich-aromatisch. Gebinde 9 kg.",
        weightPerPiece = 0.170
    ),
    offerArticle(
        productId = "112082", productName = "Apfel Milwa",
        acquirePrice = 1.88, unit = ProductUnit.KG, category = ProductCategory.OBST,
        searchTerms = "apfel,äpfel,milwa,diwa,obst",
        detailInfo = "Bio-Apfel der Sorte Milwa aus Deutschland. Süß-aromatisch, sehr saftig. " +
            "Gebinde 9 kg.",
        weightPerPiece = 0.170
    ),
    offerArticle(
        productId = "112112", productName = "Apfel Red Jonaprince",
        acquirePrice = 1.96, unit = ProductUnit.KG, category = ProductCategory.OBST,
        searchTerms = "apfel,äpfel,jonaprince,jonagold,obst",
        detailInfo = "Bio-Apfel Red Jonaprince aus Deutschland. Kräftig rot, süß-säuerlich. " +
            "Gebinde 9 kg.",
        weightPerPiece = 0.190
    ),
    offerArticle(
        productId = "112099", productName = "Apfel Natyra",
        acquirePrice = 2.33, unit = ProductUnit.KG, category = ProductCategory.OBST,
        searchTerms = "apfel,äpfel,natyra,obst,lagerapfel",
        detailInfo = "Bio-Apfel der Sorte Natyra aus Deutschland. Schorfresistent, " +
            "besonders lange lagerfähig. Gebinde 9 kg.",
        weightPerPiece = 0.175
    ),
    offerArticle(
        productId = "111116", productName = "Zitronen gelb Eureka",
        acquirePrice = 3.99, unit = ProductUnit.KG, category = ProductCategory.OBST,
        searchTerms = "zitrone,zitronen,eureka,zitrusfrucht,obst",
        detailInfo = "Bio-Zitronen der Sorte Eureka, Kaliber 2-3, aus Südafrika. " +
            "Unbehandelte Schale. Gebinde 6 kg.",
        weightPerPiece = 0.120
    ),
    offerArticle(
        productId = "111512", productName = "Orange Midknight",
        acquirePrice = 2.34, unit = ProductUnit.KG, category = ProductCategory.OBST,
        searchTerms = "orange,orangen,midknight,zitrusfrucht,obst",
        detailInfo = "Bio-Orangen der Sorte Midknight, Kaliber 2-3, aus Spanien. " +
            "Saftig und kernarm. Gebinde 10 kg.",
        weightPerPiece = 0.220
    ),
    offerArticle(
        productId = "111133", productName = "Limetten",
        acquirePrice = 5.29, unit = ProductUnit.KG, category = ProductCategory.OBST,
        searchTerms = "limette,limetten,lime,zitrusfrucht,obst",
        detailInfo = "Bio-Limetten aus Brasilien. Aromatisch-herb. Gebinde 2 kg.",
        weightPerPiece = 0.070
    ),
    offerArticle(
        productId = "1162269", productName = "Kiwi Hayward",
        acquirePrice = 0.59, unit = ProductUnit.STUECK, category = ProductCategory.OBST,
        searchTerms = "kiwi,hayward,obst",
        detailInfo = "Bio-Kiwi der Sorte Hayward, Kaliber 33, aus Argentinien. " +
            "Gelegt im 32er-Gebinde, reift langsam nach.",
        weightPerPiece = 0.115
    ),
    offerArticle(
        // Listenpreis 19,20 EUR je Kiste mit 9-10 Stück (ca. 3,8 kg) -> ca. 2,02 EUR/Stück
        productId = "1180493", productName = "Granatapfel",
        acquirePrice = 2.02, unit = ProductUnit.STUECK, category = ProductCategory.OBST,
        searchTerms = "granatapfel,granatäpfel,obst",
        detailInfo = "Bio-Granatäpfel aus Peru. Umgerechnet aus der Kiste mit 9-10 Stück " +
            "(ca. 3,8 kg, 19,20 EUR netto).",
        weightPerPiece = 0.400
    ),
    offerArticle(
        productId = "114405", productName = "Walnüsse AOP Grenoble",
        acquirePrice = 5.95, unit = ProductUnit.KG, category = ProductCategory.SONSTIGES,
        searchTerms = "walnuss,walnüsse,nuss,nüsse,grenoble",
        detailInfo = "Bio-Walnüsse AOP Grenoble, Kaliber 30-32, aus Frankreich. " +
            "In der Schale, sehr lange lagerfähig. Gebinde 5 kg.",
        weightPerPiece = 0.012
    ),
    offerArticle(
        productId = "118018", productName = "Medjuhl-Datteln",
        acquirePrice = 10.40, unit = ProductUnit.KG, category = ProductCategory.OBST,
        searchTerms = "dattel,datteln,medjool,medjuhl,trockenfrucht",
        detailInfo = "Bio-Medjuhl-Datteln aus Israel. Groß, weich und karamellig. Gebinde 2 kg.",
        weightPerPiece = 0.023
    ),
    offerArticle(
        productId = "118009", productName = "Datteln Deglet Nour, 400 g",
        acquirePrice = 3.08, unit = ProductUnit.BEUTEL, category = ProductCategory.OBST,
        searchTerms = "dattel,datteln,deglet nour,trockenfrucht,beutel",
        detailInfo = "Bio-Datteln Deglet Nour aus Tunesien im 400-g-Beutel. " +
            "Feinfruchtig, ungeschwefelt.",
        weightPerPiece = 0.400
    ),

    // ------------------------------------------------------------ Gemüse & Wurzeln (18)

    offerArticle(
        productId = "120605", productName = "Möhren",
        acquirePrice = 1.71, unit = ProductUnit.KG, category = ProductCategory.GEMUESE,
        searchTerms = "möhre,möhren,karotte,karotten,gemüse",
        detailInfo = "Bio-Möhren vom Ökodorf Brodowin, regional. Waschware, lagerfähig. " +
            "Gebinde 10 kg.",
        weightPerPiece = 0.090
    ),
    offerArticle(
        productId = "124231", productName = "Zwiebeln gelb",
        acquirePrice = 1.54, unit = ProductUnit.KG, category = ProductCategory.GEMUESE,
        searchTerms = "zwiebel,zwiebeln,gelb,gemüse",
        detailInfo = "Gelbe Bio-Zwiebeln aus Deutschland. Trocken gelagert, monatelang haltbar. " +
            "Gebinde 8 kg.",
        weightPerPiece = 0.110
    ),
    offerArticle(
        productId = "124253", productName = "Rote Zwiebeln",
        acquirePrice = 1.54, unit = ProductUnit.KG, category = ProductCategory.GEMUESE,
        searchTerms = "zwiebel,zwiebeln,rot,rote zwiebel,gemüse",
        detailInfo = "Rote Bio-Zwiebeln aus Italien. Mild-würzig, gut lagerfähig. Gebinde 6 kg.",
        weightPerPiece = 0.110
    ),
    offerArticle(
        productId = "124270", productName = "Gemüsezwiebeln 80+",
        acquirePrice = 1.34, unit = ProductUnit.KG, category = ProductCategory.GEMUESE,
        searchTerms = "zwiebel,zwiebeln,gemüsezwiebel,gemüse",
        detailInfo = "Bio-Gemüsezwiebeln 80+ mm aus Spanien. Groß und mild. Gebinde 10 kg.",
        weightPerPiece = 0.300
    ),
    offerArticle(
        productId = "124134", productName = "Schalotten",
        acquirePrice = 3.68, unit = ProductUnit.KG, category = ProductCategory.GEMUESE,
        searchTerms = "schalotte,schalotten,zwiebel,gemüse",
        detailInfo = "Bio-Schalotten aus Deutschland. Fein-aromatisch, sehr gut lagerfähig. " +
            "Gebinde 3 kg.",
        weightPerPiece = 0.030
    ),
    offerArticle(
        productId = "124410", productName = "Knoblauch getrocknet",
        acquirePrice = 5.95, unit = ProductUnit.KG, category = ProductCategory.GEMUESE,
        searchTerms = "knoblauch,knofi,gemüse",
        detailInfo = "Getrockneter Bio-Knoblauch aus Spanien. Trocken gelagert monatelang " +
            "haltbar. Gebinde 3 kg.",
        weightPerPiece = 0.060
    ),
    offerArticle(
        productId = "122790", productName = "Frühkartoffel Adretta, mehligkochend",
        acquirePrice = 1.79, unit = ProductUnit.KG, category = ProductCategory.KARTOFFELN,
        searchTerms = "kartoffel,kartoffeln,adretta,mehligkochend",
        detailInfo = "Regionale Bio-Frühkartoffel Adretta, mehligkochend. " +
            "Ideal für Püree und Suppen. Gebinde 12,5 kg.",
        weightPerPiece = 0.080
    ),
    offerArticle(
        productId = "122735", productName = "Speisekartoffel Annabelle, 1,5 kg Netz",
        acquirePrice = 2.64, unit = ProductUnit.BEUTEL, category = ProductCategory.KARTOFFELN,
        searchTerms = "kartoffel,kartoffeln,annabelle,festkochend,netz",
        detailInfo = "Bio-Speisekartoffel Annabelle, festkochend, im 1,5-kg-Netz aus Deutschland.",
        weightPerPiece = 1.500
    ),
    offerArticle(
        productId = "122654", productName = "Süßkartoffel",
        acquirePrice = 2.68, unit = ProductUnit.KG, category = ProductCategory.KARTOFFELN,
        searchTerms = "süßkartoffel,süßkartoffeln,batate,gemüse",
        detailInfo = "Bio-Süßkartoffeln aus Spanien, mittlere Sortierung (200-500 g). " +
            "Gebinde 6 kg.",
        weightPerPiece = 0.350
    ),
    offerArticle(
        productId = "122408", productName = "Rote Bete, samenfest",
        acquirePrice = 1.94, unit = ProductUnit.KG, category = ProductCategory.GEMUESE,
        searchTerms = "rote bete,rote beete,randen,gemüse,samenfest",
        detailInfo = "Regionale Bio-Rote-Bete aus samenfestem Saatgut. Sehr gut lagerfähig. " +
            "Gebinde 5 kg.",
        weightPerPiece = 0.200
    ),
    offerArticle(
        productId = "122509", productName = "Knollensellerie mit Grün",
        acquirePrice = 1.88, unit = ProductUnit.STUECK, category = ProductCategory.GEMUESE,
        searchTerms = "sellerie,knollensellerie,gemüse,suppengrün",
        detailInfo = "Bio-Knollensellerie mit Grün aus Deutschland. Gebinde 10 Stück.",
        weightPerPiece = 0.600
    ),
    offerArticle(
        productId = "121700", productName = "Wurzelpetersilie, samenfest",
        acquirePrice = 4.12, unit = ProductUnit.KG, category = ProductCategory.GEMUESE,
        searchTerms = "wurzelpetersilie,petersilienwurzel,gemüse,suppengrün",
        detailInfo = "Bio-Wurzelpetersilie aus samenfestem Saatgut, Deutschland. " +
            "Würzige Suppenwurzel. Gebinde 3 kg.",
        weightPerPiece = 0.120
    ),
    offerArticle(
        productId = "121110", productName = "Rettich weiß",
        acquirePrice = 3.23, unit = ProductUnit.KG, category = ProductCategory.GEMUESE,
        searchTerms = "rettich,weiß,gemüse",
        detailInfo = "Weißer Bio-Rettich aus Deutschland. Kühl gelagert wochenlang knackig. " +
            "Gebinde 3 kg.",
        weightPerPiece = 0.350
    ),
    offerArticle(
        productId = "127466", productName = "Kürbis Hokkaido orange",
        acquirePrice = 2.14, unit = ProductUnit.KG, category = ProductCategory.GEMUESE,
        searchTerms = "kürbis,hokkaido,gemüse",
        detailInfo = "Bio-Hokkaido-Kürbis aus Deutschland, 8-12 Stück je Gebinde. " +
            "Schale mitessbar, monatelang lagerfähig. Gebinde 10 kg.",
        weightPerPiece = 1.000
    ),
    offerArticle(
        productId = "126044", productName = "Weißkohl",
        acquirePrice = 1.89, unit = ProductUnit.KG, category = ProductCategory.GEMUESE,
        searchTerms = "weißkohl,kohl,kraut,gemüse",
        detailInfo = "Regionaler Bio-Weißkohl, 4-6 Köpfe je Gebinde. Gebinde 6 kg.",
        weightPerPiece = 1.200
    ),
    offerArticle(
        productId = "125904", productName = "Rotkohl",
        acquirePrice = 2.15, unit = ProductUnit.KG, category = ProductCategory.GEMUESE,
        searchTerms = "rotkohl,blaukraut,kohl,gemüse",
        detailInfo = "Bio-Rotkohl aus Deutschland, 5-8 Köpfe je Gebinde. Gebinde 5 kg.",
        weightPerPiece = 0.900
    ),
    offerArticle(
        productId = "126210", productName = "Wirsing",
        acquirePrice = 2.75, unit = ProductUnit.KG, category = ProductCategory.GEMUESE,
        searchTerms = "wirsing,kohl,gemüse",
        detailInfo = "Regionaler Bio-Wirsing, 5-6 Köpfe je Gebinde. Gebinde 8 kg.",
        weightPerPiece = 1.300
    ),
    offerArticle(
        productId = "1180613", productName = "Ingwer",
        acquirePrice = 5.49, unit = ProductUnit.KG, category = ProductCategory.GEMUESE,
        searchTerms = "ingwer,ginger,wurzel,gemüse",
        detailInfo = "Bio-Ingwer aus Thailand. Kühl und trocken wochenlang haltbar. " +
            "Gebinde 3 kg.",
        weightPerPiece = 0.080
    )
)
