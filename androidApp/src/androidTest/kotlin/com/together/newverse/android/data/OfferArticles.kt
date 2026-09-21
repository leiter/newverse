package com.together.newverse.android.data

import com.together.newverse.data.parser.ProductDescriptionBuilder
import com.together.newverse.domain.model.Article
import com.together.newverse.domain.model.ProductCategory
import com.together.newverse.domain.model.ProductUnit
import com.together.newverse.domain.model.SellerArticle
import com.together.newverse.domain.model.SellerArticleData
import com.together.newverse.domain.model.TaxRate

/**
 * The 30 long-storable fruit & vegetable articles from `offer.md`
 * (Terra Naturkost Handels KG, BNN price list "Obst & Gemüse KW 31/26",
 * valid 27.07.-02.08.2026, source file `tmp/plf.bnn`).
 *
 * `acquirePrice` is the supplier's net list price per base unit as printed in the BNN file.
 * It is seller-only: it goes into [SellerArticleData] together with the BNN codes, while
 * the public [Article] carries what buyers see.
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

private val descriptionBuilder = ProductDescriptionBuilder()

/**
 * Builds a [SellerArticle] with a sell price derived from the supplier price, so the offer data
 * cannot drift out of sync with the markup.
 *
 * [Article.detailInfo] is not written by hand either. Origin, certification and producer are
 * generated from the BNN codes by the same [ProductDescriptionBuilder] the BNN file import
 * uses, so an article uploaded here is described exactly like one the seller imports. Only
 * [note] is written by hand, for the things the BNN row does not carry — taste, storage,
 * sorting. Trade details (Gebinde, Handelsklasse, supplier prices) belong in neither.
 *
 * @param originCode BNN field 12: country code, or `REG` for Terra's own region.
 * @param certificationCode BNN field 13: identification code (IK).
 * @param producerCode BNN field 10: producer / brand code. Omitted from the text when
 *   Terra publishes no name for it.
 */
fun offerArticle(
    productId: String,
    productName: String,
    acquirePrice: Double,
    unit: ProductUnit,
    category: ProductCategory,
    searchTerms: String,
    note: String,
    originCode: String,
    certificationCode: String,
    producerCode: String,
    weightPerPiece: Double,
    markupFactor: Double = OFFER_MARKUP_FACTOR,
    taxRate: TaxRate = TaxRate.REDUCED
): SellerArticle {
    val detailInfo = descriptionBuilder.build(
        bnnDetail = note,
        originCode = originCode,
        certificationCode = certificationCode,
        producerCode = producerCode
    )
    val gross = acquirePrice * markupFactor * (1.0 + taxRate.rate)
    val price = (gross * 100).toLong() / 100.0
    val article = Article(
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
        taxRate = taxRate.rate
    )
    return SellerArticle(
        article = article,
        sellerData = SellerArticleData(
            acquirePrice = acquirePrice,
            markupFactor = markupFactor,
            supplier = producerCode,
            origin = originCode,
            certification = certificationCode
        )
    )
}

/** Expected size of [offerArticles] — kept as a constant so the test fails loudly on edits. */
const val OFFER_ARTICLE_COUNT = 30

/**
 * Article 1-30 of `offer.md`, in the same order: 12 Obst & Nüsse, then 18 Gemüse & Wurzeln.
 */
val offerArticles: List<SellerArticle> = listOf(

    // ---------------------------------------------------------------- Obst & Nüsse (12)

    offerArticle(
        productId = "112108", productName = "Apfel Topaz",
        acquirePrice = 1.96, unit = ProductUnit.KG, category = ProductCategory.OBST,
        searchTerms = "apfel,äpfel,topaz,obst,lagerapfel",
        note = "Robuste Lagersorte, säuerlich-aromatisch.",
        originCode = "DE", certificationCode = "DB", producerCode = "BOA",
        weightPerPiece = 0.170
    ),
    offerArticle(
        productId = "112082", productName = "Apfel Milwa",
        acquirePrice = 1.88, unit = ProductUnit.KG, category = ProductCategory.OBST,
        searchTerms = "apfel,äpfel,milwa,diwa,obst",
        note = "Süß-aromatisch und sehr saftig.",
        originCode = "DE", certificationCode = "UW", producerCode = "BOA",
        weightPerPiece = 0.170
    ),
    offerArticle(
        productId = "112112", productName = "Apfel Red Jonaprince",
        acquirePrice = 1.96, unit = ProductUnit.KG, category = ProductCategory.OBST,
        searchTerms = "apfel,äpfel,jonaprince,jonagold,obst",
        note = "Kräftig rot, süß-säuerlich.",
        originCode = "DE", certificationCode = "DD", producerCode = "BOA",
        weightPerPiece = 0.190
    ),
    offerArticle(
        productId = "112099", productName = "Apfel Natyra",
        acquirePrice = 2.33, unit = ProductUnit.KG, category = ProductCategory.OBST,
        searchTerms = "apfel,äpfel,natyra,obst,lagerapfel",
        note = "Schorfresistent und besonders lange lagerfähig.",
        originCode = "DE", certificationCode = "DD", producerCode = "BOA",
        weightPerPiece = 0.175
    ),
    offerArticle(
        productId = "111116", productName = "Zitronen gelb Eureka",
        acquirePrice = 3.99, unit = ProductUnit.KG, category = ProductCategory.OBST,
        searchTerms = "zitrone,zitronen,eureka,zitrusfrucht,obst",
        note = "Kaliber 2-3, mit unbehandelter Schale.",
        originCode = "ZA", certificationCode = "EG", producerCode = "BTR",
        weightPerPiece = 0.120
    ),
    offerArticle(
        productId = "111512", productName = "Orange Midknight",
        acquirePrice = 2.34, unit = ProductUnit.KG, category = ProductCategory.OBST,
        searchTerms = "orange,orangen,midknight,zitrusfrucht,obst",
        note = "Kaliber 2-3, saftig und kernarm.",
        originCode = "ES", certificationCode = "EG", producerCode = "SCH",
        weightPerPiece = 0.220
    ),
    offerArticle(
        productId = "111133", productName = "Limetten",
        acquirePrice = 5.29, unit = ProductUnit.KG, category = ProductCategory.OBST,
        searchTerms = "limette,limetten,lime,zitrusfrucht,obst",
        note = "Aromatisch-herb.",
        originCode = "BR", certificationCode = "EG", producerCode = "ZAN",
        weightPerPiece = 0.070
    ),
    offerArticle(
        productId = "1162269", productName = "Kiwi Hayward",
        acquirePrice = 0.59, unit = ProductUnit.STUECK, category = ProductCategory.OBST,
        searchTerms = "kiwi,hayward,obst",
        note = "Kaliber 33, reift langsam nach.",
        originCode = "AR", certificationCode = "EG", producerCode = "BTR",
        weightPerPiece = 0.115
    ),
    offerArticle(
        // Listenpreis 19,20 EUR je Kiste mit 9-10 Stück (ca. 3,8 kg) -> ca. 2,02 EUR/Stück
        productId = "1180493", productName = "Granatapfel",
        acquirePrice = 2.02, unit = ProductUnit.STUECK, category = ProductCategory.OBST,
        searchTerms = "granatapfel,granatäpfel,obst",
        note = "Große Früchte von etwa 400 g.",
        originCode = "PE", certificationCode = "EG", producerCode = "EOS",
        weightPerPiece = 0.400
    ),
    offerArticle(
        productId = "114405", productName = "Walnüsse AOP Grenoble",
        acquirePrice = 5.95, unit = ProductUnit.KG, category = ProductCategory.SONSTIGES,
        searchTerms = "walnuss,walnüsse,nuss,nüsse,grenoble",
        note = "Kaliber 30-32, in der Schale und sehr lange lagerfähig.",
        originCode = "FR", certificationCode = "EG", producerCode = "PNA",
        weightPerPiece = 0.012
    ),
    offerArticle(
        productId = "118018", productName = "Medjuhl-Datteln",
        acquirePrice = 10.40, unit = ProductUnit.KG, category = ProductCategory.OBST,
        searchTerms = "dattel,datteln,medjool,medjuhl,trockenfrucht",
        note = "Groß, weich und karamellig.",
        originCode = "IL", certificationCode = "EG", producerCode = "BTR",
        weightPerPiece = 0.023
    ),
    offerArticle(
        productId = "118009", productName = "Datteln Deglet Nour, 400 g",
        acquirePrice = 3.08, unit = ProductUnit.BEUTEL, category = ProductCategory.OBST,
        searchTerms = "dattel,datteln,deglet nour,trockenfrucht,beutel",
        note = "Feinfruchtig und ungeschwefelt, im 400-g-Beutel.",
        originCode = "TN", certificationCode = "DD", producerCode = "TIL",
        weightPerPiece = 0.400
    ),

    // ------------------------------------------------------------ Gemüse & Wurzeln (18)

    offerArticle(
        productId = "120605", productName = "Möhren",
        acquirePrice = 1.71, unit = ProductUnit.KG, category = ProductCategory.GEMUESE,
        searchTerms = "möhre,möhren,karotte,karotten,gemüse",
        note = "Waschware, gut lagerfähig.",
        originCode = "REG", certificationCode = "DD", producerCode = "ÖBW",
        weightPerPiece = 0.090
    ),
    offerArticle(
        productId = "124231", productName = "Zwiebeln gelb",
        acquirePrice = 1.54, unit = ProductUnit.KG, category = ProductCategory.GEMUESE,
        searchTerms = "zwiebel,zwiebeln,gelb,gemüse",
        note = "Trocken gelagert monatelang haltbar.",
        originCode = "DE", certificationCode = "DB", producerCode = "AHR",
        weightPerPiece = 0.110
    ),
    offerArticle(
        productId = "124253", productName = "Rote Zwiebeln",
        acquirePrice = 1.54, unit = ProductUnit.KG, category = ProductCategory.GEMUESE,
        searchTerms = "zwiebel,zwiebeln,rot,rote zwiebel,gemüse",
        note = "Mild-würzig und gut lagerfähig.",
        originCode = "IT", certificationCode = "IA", producerCode = "BTR",
        weightPerPiece = 0.110
    ),
    offerArticle(
        productId = "124270", productName = "Gemüsezwiebeln 80+",
        acquirePrice = 1.34, unit = ProductUnit.KG, category = ProductCategory.GEMUESE,
        searchTerms = "zwiebel,zwiebeln,gemüsezwiebel,gemüse",
        note = "Große, milde Zwiebeln ab 80 mm.",
        originCode = "ES", certificationCode = "EG", producerCode = "SCH",
        weightPerPiece = 0.300
    ),
    offerArticle(
        productId = "124134", productName = "Schalotten",
        acquirePrice = 3.68, unit = ProductUnit.KG, category = ProductCategory.GEMUESE,
        searchTerms = "schalotte,schalotten,zwiebel,gemüse",
        note = "Fein-aromatisch und sehr gut lagerfähig.",
        originCode = "DE", certificationCode = "DB", producerCode = "AHR",
        weightPerPiece = 0.030
    ),
    offerArticle(
        productId = "124410", productName = "Knoblauch getrocknet",
        acquirePrice = 5.95, unit = ProductUnit.KG, category = ProductCategory.GEMUESE,
        searchTerms = "knoblauch,knofi,gemüse",
        note = "Trocken gelagert monatelang haltbar.",
        originCode = "ES", certificationCode = "EG", producerCode = "SCH",
        weightPerPiece = 0.060
    ),
    offerArticle(
        productId = "122790", productName = "Frühkartoffel Adretta, mehligkochend",
        acquirePrice = 1.79, unit = ProductUnit.KG, category = ProductCategory.KARTOFFELN,
        searchTerms = "kartoffel,kartoffeln,adretta,mehligkochend",
        note = "Mehligkochend, ideal für Püree und Suppen.",
        originCode = "REG", certificationCode = "DD", producerCode = "BFH",
        weightPerPiece = 0.080
    ),
    offerArticle(
        productId = "122735", productName = "Speisekartoffel Annabelle, 1,5 kg Netz",
        acquirePrice = 2.64, unit = ProductUnit.BEUTEL, category = ProductCategory.KARTOFFELN,
        searchTerms = "kartoffel,kartoffeln,annabelle,festkochend,netz",
        note = "Festkochend, im 1,5-kg-Netz.",
        originCode = "DE", certificationCode = "DB", producerCode = "AHR",
        weightPerPiece = 1.500
    ),
    offerArticle(
        productId = "122654", productName = "Süßkartoffel",
        acquirePrice = 2.68, unit = ProductUnit.KG, category = ProductCategory.KARTOFFELN,
        searchTerms = "süßkartoffel,süßkartoffeln,batate,gemüse",
        note = "Mittlere Sortierung von 200 bis 500 g je Stück.",
        originCode = "ES", certificationCode = "EG", producerCode = "SCH",
        weightPerPiece = 0.350
    ),
    offerArticle(
        productId = "122408", productName = "Rote Bete, samenfest",
        acquirePrice = 1.94, unit = ProductUnit.KG, category = ProductCategory.GEMUESE,
        searchTerms = "rote bete,rote beete,randen,gemüse,samenfest",
        note = "Aus samenfestem Saatgut, sehr gut lagerfähig.",
        originCode = "REG", certificationCode = "DB", producerCode = "wvg",
        weightPerPiece = 0.200
    ),
    offerArticle(
        productId = "122509", productName = "Knollensellerie mit Grün",
        acquirePrice = 1.88, unit = ProductUnit.STUECK, category = ProductCategory.GEMUESE,
        searchTerms = "sellerie,knollensellerie,gemüse,suppengrün",
        note = "Mit Grün, als Suppengemüse und zum Einlagern.",
        originCode = "DE", certificationCode = "DB", producerCode = "SJM",
        weightPerPiece = 0.600
    ),
    offerArticle(
        productId = "121700", productName = "Wurzelpetersilie, samenfest",
        acquirePrice = 4.12, unit = ProductUnit.KG, category = ProductCategory.GEMUESE,
        searchTerms = "wurzelpetersilie,petersilienwurzel,gemüse,suppengrün",
        note = "Aus samenfestem Saatgut, würzige Suppenwurzel.",
        originCode = "DE", certificationCode = "DD", producerCode = "NKG",
        weightPerPiece = 0.120
    ),
    offerArticle(
        productId = "121110", productName = "Rettich weiß",
        acquirePrice = 3.23, unit = ProductUnit.KG, category = ProductCategory.GEMUESE,
        searchTerms = "rettich,weiß,gemüse",
        note = "Kühl gelagert wochenlang knackig.",
        originCode = "DE", certificationCode = "DB", producerCode = "RHO",
        weightPerPiece = 0.350
    ),
    offerArticle(
        productId = "127466", productName = "Kürbis Hokkaido orange",
        acquirePrice = 2.14, unit = ProductUnit.KG, category = ProductCategory.GEMUESE,
        searchTerms = "kürbis,hokkaido,gemüse",
        note = "Schale mitessbar, monatelang lagerfähig.",
        originCode = "DE", certificationCode = "DB", producerCode = "AHR",
        weightPerPiece = 1.000
    ),
    offerArticle(
        productId = "126044", productName = "Weißkohl",
        acquirePrice = 1.89, unit = ProductUnit.KG, category = ProductCategory.GEMUESE,
        searchTerms = "weißkohl,kohl,kraut,gemüse",
        note = "Feste Köpfe, gut zum Einlagern und Fermentieren.",
        originCode = "REG", certificationCode = "DB", producerCode = "wvg",
        weightPerPiece = 1.200
    ),
    offerArticle(
        productId = "125904", productName = "Rotkohl",
        acquirePrice = 2.15, unit = ProductUnit.KG, category = ProductCategory.GEMUESE,
        searchTerms = "rotkohl,blaukraut,kohl,gemüse",
        note = "Feste Köpfe, klassisch als Blaukraut.",
        originCode = "DE", certificationCode = "DB", producerCode = "RHO",
        weightPerPiece = 0.900
    ),
    offerArticle(
        productId = "126210", productName = "Wirsing",
        acquirePrice = 2.75, unit = ProductUnit.KG, category = ProductCategory.GEMUESE,
        searchTerms = "wirsing,kohl,gemüse",
        note = "Zarte, lockere Köpfe.",
        originCode = "REG", certificationCode = "EG", producerCode = "gde",
        weightPerPiece = 1.300
    ),
    offerArticle(
        productId = "1180613", productName = "Ingwer",
        acquirePrice = 5.49, unit = ProductUnit.KG, category = ProductCategory.GEMUESE,
        searchTerms = "ingwer,ginger,wurzel,gemüse",
        note = "Kühl und trocken wochenlang haltbar.",
        originCode = "TH", certificationCode = "EG", producerCode = "BTR",
        weightPerPiece = 0.080
    )
)
