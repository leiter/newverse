package com.together.newverse.data.catalog

import com.together.newverse.data.firebase.ArticleNodes
import com.together.newverse.data.parser.ProductDescriptionBuilder
import com.together.newverse.domain.model.Article
import com.together.newverse.domain.model.ArticleScarcity
import com.together.newverse.domain.model.ProductCategory
import com.together.newverse.domain.model.ProductPricing
import com.together.newverse.domain.model.ProductUnit
import com.together.newverse.domain.model.SellerArticle
import com.together.newverse.domain.model.SellerArticleData
import com.together.newverse.domain.model.TaxRate
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Writes the winter assortment as an RTDB update document, from a Terra BNN price list.
 *
 * This is a **generator**, not an assertion about the app: it produces
 * `firebase/fixtures/winter-catalog.json`, a path-keyed JSON file to merge into the
 * database (see the file's own header for the command). It is a test only because that
 * is the cheapest way to run the app's own writers — [ArticleNodes.saveUpdate],
 * [ProductPricing] and [ProductDescriptionBuilder] — against a real price list, so the
 * document is field-for-field what the seller app itself would have written. Nothing
 * here talks to Firebase.
 *
 * ### The two numbers per row
 *
 * `acquirePrice` is Terra's net list price, read from the file and never typed.
 * The **shelf price is the decision** and is typed ([Item.price]): it is set between
 * what a discounter's own-brand organic line charges and what Alnatura charges, and
 * rounded to an ending a market stall can shout. The markup factor is then derived
 * from the two with [ProductPricing.markupFactor] — the inverse of the seller form's
 * own formula — so the stored margin always matches the price actually charged. Doing
 * it the other way round (pick a factor, let it produce 2,53 €) would put arithmetic
 * in charge of the price tag.
 *
 * ### The unit is what the stall sells by, not what Terra invoices by
 *
 * Terra prices most rows per kilo, a few per piece or per bunch (field 24). Where the
 * two disagree the stall wins, and [Item.pieceWeightKg] carries the conversion: garlic
 * is bought at 5,75 €/kg and sold by the bulb, so the net price per bulb is the kilo
 * price times the bulb weight. A row whose unit matches Terra's leaves it null.
 *
 * ### Availability
 *
 * Every row here is published available, which for a stock-gated catalogue means
 * `sellerAvailable = true` **and** `stockGated = false`: `available` is derived
 * (`available = sellerAvailable && stockPermits`), so a row gated on a ledger nobody
 * has seeded would publish as unavailable however this file set the public flag. See
 * `doc/guides/stock-driven-availability-design.md`.
 *
 * Rerun after a new price list arrives: drop the new `plf.bnn` in `tmp/Terra/`, point
 * [BNN_FILE] at it, and check the prices the new net costs imply.
 */
class WinterCatalogJsonTest {

    /**
     * One row of the assortment.
     *
     * @param article Terra article number — also the article id, so a re-upload
     *   upserts rather than duplicating.
     * @param name What the buyer sees. Terra's own name carries trade detail
     *   ("4-6 St", "- 5 Kg -") that belongs in neither half of an article.
     * @param price The gross shelf price in [unit], the decision this file exists for.
     * @param unit What the stall sells by.
     * @param pieceWeightKg Kilos per piece or bunch, where [unit] is not Terra's unit;
     *   also published as [Article.weightPerPiece] for every countable row.
     * @param note Added in front of the generated origin sentence, for what the BNN
     *   row cannot say — storage, taste, what it is for.
     */
    private data class Item(
        val article: String,
        val name: String,
        val price: Double,
        val unit: ProductUnit,
        val category: ProductCategory,
        val searchTerms: String,
        val pieceWeightKg: Double? = null,
        val note: String = ""
    )

    private val items = listOf(
        // ---------------------------------------------------------- Kartoffeln (2)
        Item(
            "122803", "Speisekartoffel Leyla", 1.99, ProductUnit.KG,
            ProductCategory.KARTOFFELN, "kartoffel kartoffeln leyla festkochend salat",
            note = "Vorwiegend festkochend - für Salzkartoffeln, Salat und Auflauf"
        ),
        Item(
            "122776", "Speisekartoffel Karelia", 1.99, ProductUnit.KG,
            ProductCategory.KARTOFFELN, "kartoffel kartoffeln karelia mehlig püree",
            note = "Mehligkochend - für Püree, Suppe und Knödel"
        ),
        // ------------------------------------------------------- Zwiebeln & Co (3)
        Item(
            "124229", "Zwiebeln, gelb", 2.29, ProductUnit.KG,
            ProductCategory.GEMUESE, "zwiebel zwiebeln gelb kochen"
        ),
        Item(
            "124249", "Zwiebeln, rot", 2.79, ProductUnit.KG,
            ProductCategory.GEMUESE, "zwiebel zwiebeln rot salat"
        ),
        Item(
            "124410", "Knoblauch", 0.59, ProductUnit.STUECK,
            ProductCategory.GEMUESE, "knoblauch knolle", pieceWeightKg = 0.065,
            note = "Getrocknet, je Knolle"
        ),
        // --------------------------------------------------------- Wurzelgemüse (5)
        Item(
            "120631", "Möhren Robila", 2.49, ProductUnit.KG,
            ProductCategory.GEMUESE, "möhre möhren karotte karotten robila"
        ),
        Item(
            "122407", "Rote Bete Robuschka", 2.19, ProductUnit.KG,
            ProductCategory.GEMUESE, "rote bete beete rande robuschka"
        ),
        Item(
            "120900", "Pastinaken", 3.99, ProductUnit.KG,
            ProductCategory.GEMUESE, "pastinake pastinaken wurzel suppe",
            note = "Samenfest - süßlich, für Suppe, Püree und Ofengemüse"
        ),
        Item(
            "122504", "Knollensellerie", 2.99, ProductUnit.KG,
            ProductCategory.GEMUESE, "sellerie knollensellerie suppe"
        ),
        Item(
            "121110", "Rettich, weiß", 4.49, ProductUnit.KG,
            ProductCategory.GEMUESE, "rettich weiss scharf brotzeit"
        ),
        // ---------------------------------------------------------------- Kohl (4)
        Item(
            "126044", "Weißkohl", 2.69, ProductUnit.KG,
            ProductCategory.GEMUESE, "weisskohl weißkohl kohl kraut"
        ),
        Item(
            "125895", "Rotkohl", 2.99, ProductUnit.KG,
            ProductCategory.GEMUESE, "rotkohl kohl blaukraut"
        ),
        Item(
            "125750", "Grünkohl", 5.49, ProductUnit.KG,
            ProductCategory.GEMUESE, "grünkohl gruenkohl kohl winter",
            note = "Nach dem ersten Frost geerntet"
        ),
        Item(
            "125804", "Rosenkohl", 6.29, ProductUnit.KG,
            ProductCategory.GEMUESE, "rosenkohl kohl winter"
        ),
        Item(
            "122235", "Kohlrabi", 1.49, ProductUnit.STUECK,
            ProductCategory.GEMUESE, "kohlrabi", pieceWeightKg = 0.4
        ),
        // ------------------------------------------------------------- Kürbis (1)
        Item(
            "127465", "Kürbis Hokkaido", 1.99, ProductUnit.KG,
            ProductCategory.GEMUESE, "kürbis kuerbis hokkaido orange suppe",
            note = "Schale mitessbar"
        ),
        // -------------------------------------------------- Lauch, Blatt, Bund (5)
        Item(
            "124505", "Lauch / Porree", 3.99, ProductUnit.KG,
            ProductCategory.GEMUESE, "lauch porree"
        ),
        Item(
            "125615", "Spinat, lose", 5.99, ProductUnit.KG,
            ProductCategory.GEMUESE, "spinat blattspinat"
        ),
        Item(
            "120950", "Radieschen", 1.49, ProductUnit.BUND,
            ProductCategory.GEMUESE, "radieschen radies bund", pieceWeightKg = 0.15
        ),
        Item(
            "126702", "Suppengrün", 2.99, ProductUnit.BUND,
            ProductCategory.GEMUESE, "suppengrün suppengruen suppe bund brühe",
            pieceWeightKg = 0.4,
            note = "Bund aus Möhre, Lauch, Sellerie und Petersilie"
        ),
        Item(
            "126707", "Petersilie, glatt", 1.89, ProductUnit.BUND,
            ProductCategory.KRAEUTER, "petersilie glatt kraut bund", pieceWeightKg = 0.05
        ),
        // ------------------------------------------------- Fruchtgemüse, Süden (4)
        Item(
            "1273821", "Zucchini", 3.99, ProductUnit.KG,
            ProductCategory.GEMUESE, "zucchini"
        ),
        Item(
            "1271758", "Strauchtomaten", 5.49, ProductUnit.KG,
            ProductCategory.GEMUESE, "tomate tomaten strauchtomaten"
        ),
        Item(
            "127222", "Paprika, rot", 7.49, ProductUnit.KG,
            ProductCategory.GEMUESE, "paprika rot"
        ),
        Item(
            "127579", "Stangenbohnen", 10.99, ProductUnit.KG,
            ProductCategory.GEMUESE, "stangenbohnen bohnen grüne bohnen"
        ),
        // ---------------------------------------------------------------- Obst (6)
        Item(
            "1121841", "Apfel Roter Jonagold", 2.99, ProductUnit.KG,
            ProductCategory.OBST, "apfel äpfel jonagold tafelapfel",
            note = "Saftig-süß, zum Essen"
        ),
        Item(
            "112076", "Apfel Roter Boskoop", 3.29, ProductUnit.KG,
            ProductCategory.OBST, "apfel äpfel boskoop backapfel",
            note = "Säuerlich, zum Backen und für Mus"
        ),
        Item(
            "112228", "Birne Alexander Lucas", 3.69, ProductUnit.KG,
            ProductCategory.OBST, "birne birnen alexander lucas"
        ),
        Item(
            "117099", "Trauben, weiß kernlos", 6.49, ProductUnit.KG,
            ProductCategory.OBST, "trauben weintrauben weiss kernlos"
        ),
        Item(
            "111120", "Zitronen Fino", 3.49, ProductUnit.KG,
            ProductCategory.OBST, "zitrone zitronen fino"
        ),
        Item(
            "116404", "Bananen", 2.19, ProductUnit.KG,
            ProductCategory.OBST, "banane bananen"
        )
    )

    @Test
    fun `writes the winter catalogue as an RTDB update document`() {
        val root = repoRoot()
        val bnn = File(root, BNN_FILE)
        assertTrue(bnn.isFile, "price list not found: ${bnn.path}")

        val rows = bnnRows(bnn)
        val header = bnnHeader(bnn)
        val update = linkedMapOf<String, Any?>()
        val report = StringBuilder()

        for (item in items) {
            val row = rows[item.article] ?: error("article ${item.article} not in $BNN_FILE")

            // Terra's price is per its own unit; the stall may sell by another one.
            val listPrice = row.price
            val acquirePrice = item.pieceWeightKg
                ?.takeIf { row.unit != unitCode(item.unit) }
                ?.let { round2(listPrice * it) }
                ?: listPrice

            val taxRate = row.taxRate
            // The price is the decision, the factor follows from it.
            val markupFactor = ProductPricing.markupFactor(item.price, acquirePrice, taxRate.rate)

            val detailInfo = ProductDescriptionBuilder().build(
                bnnDetail = item.note.ifEmpty { row.detail },
                originCode = row.origin,
                certificationCode = row.certification,
                producerCode = row.supplier
            )

            val article = SellerArticle(
                article = Article(
                    id = item.article,
                    productId = item.article,
                    productName = item.name,
                    // Derived, and only ever true here because stockGated is false.
                    available = true,
                    unit = item.unit.displayName,
                    price = item.price,
                    weightPerPiece = item.pieceWeightKg ?: 0.0,
                    // Filled in by the image upload, which keys objects by article
                    // number too, so it lands on these rows without touching them.
                    imageUrl = "",
                    category = item.category.displayName,
                    searchTerms = item.searchTerms,
                    detailInfo = detailInfo,
                    taxRate = taxRate.rate,
                    scarcity = ArticleScarcity.UNKNOWN
                ),
                sellerData = SellerArticleData(
                    acquirePrice = acquirePrice,
                    markupFactor = markupFactor,
                    supplier = row.supplier,
                    origin = row.origin,
                    certification = row.certification,
                    quality = row.quality,
                    barcode = row.barcode,
                    packageSize = row.packageSize,
                    // Nobody is warned about a refill until the seller asks to be.
                    reorderLevel = 0.0,
                    sellerAvailable = true,
                    stockGated = false
                )
            )

            update += ArticleNodes.saveUpdate(SELLER_ID, item.article, article)

            val margin = (item.price / (1.0 + taxRate.rate) - acquirePrice) /
                (item.price / (1.0 + taxRate.rate)) * 100.0
            report.append(
                "%-26s %-7s netto %6.2f  VK %6.2f /%-6s Faktor %.4f  Marge %4.1f %%  %s\n"
                    .format(
                        item.name, item.article, acquirePrice, item.price,
                        item.unit.displayName, markupFactor, margin, row.origin
                    )
            )
        }

        val out = File(root, OUT_FILE)
        out.parentFile?.mkdirs()
        out.writeText(json(update, header))

        // What the document cannot carry: where the prices came from, and the margin
        // each one leaves. Written beside it so a price can be argued with later.
        val reportFile = File(root, REPORT_FILE)
        reportFile.writeText(
            buildString {
                append("Winterkatalog, erzeugt von WinterCatalogJsonTest\n")
                append("Preisliste: $header\n")
                append("Verkäufer:  $SELLER_ID\n")
                append("Artikel:    ${items.size}\n\n")
                append(report)
            }
        )

        println("\n$header\n")
        println(report)
        println("${items.size} articles -> ${out.path}")
        println("report           -> ${reportFile.path}")

        // Both halves of every article, and nothing else.
        assertEquals(items.size * (PUBLIC_FIELDS + PRIVATE_FIELDS), update.size)
        assertTrue(update.keys.all { it.startsWith("articles/") || it.startsWith("seller_articles/") })
    }

    // ---------------------------------------------------------------- BNN reading

    private data class Row(
        val id: String,
        val name: String,
        val detail: String,
        val quality: String,
        val supplier: String,
        val origin: String,
        val certification: String,
        val barcode: String,
        val unit: String,
        val packageSize: Double,
        val price: Double,
        val taxRate: TaxRate
    )

    /** Every priced row of the price list, by article number. */
    private fun bnnRows(file: File): Map<String, Row> =
        file.readLines(charset(BNN_CHARSET))
            .drop(1)
            .mapNotNull { line ->
                val f = line.split(';')
                if (f.size < 40) return@mapNotNull null
                val price = f[37].replace(',', '.').toDoubleOrNull() ?: return@mapNotNull null
                if (price <= 0.0) return@mapNotNull null
                Row(
                    id = f[0],
                    name = f[6].trim(),
                    detail = f[7].trim(),
                    quality = f[9],
                    supplier = f[10],
                    origin = f[12],
                    certification = f[13],
                    barcode = f[4].takeUnless { it.isBlank() || it.all { c -> c == '0' } } ?: "",
                    unit = f[23],
                    packageSize = f[22].replace(',', '.').toDoubleOrNull() ?: 0.0,
                    price = price,
                    // Field 34: 1 = reduced, 2 = standard.
                    taxRate = if (f[33] == "2") TaxRate.STANDARD else TaxRate.REDUCED
                )
            }
            .associateBy { it.id }

    /** The price list's own header line, carried into the document as provenance. */
    private fun bnnHeader(file: File): String {
        val f = file.readLines(charset(BNN_CHARSET)).first().split(';')
        return "${f.getOrNull(5)} (${f.getOrNull(3)}), gültig ${f.getOrNull(7)}-${f.getOrNull(8)}"
    }

    /** Terra's code for the unit a stall unit corresponds to. */
    private fun unitCode(unit: ProductUnit): String = when (unit) {
        ProductUnit.KG -> "KG"
        ProductUnit.STUECK -> "ST"
        ProductUnit.BUND -> "BD"
        ProductUnit.BEUTEL -> "BT"
        ProductUnit.SCHALE -> "SC"
        ProductUnit.GLAS -> "GL"
        else -> unit.displayName.uppercase()
    }

    // ------------------------------------------------------------ JSON, by hand

    /**
     * The update map as a path-keyed JSON document, nesting each article's fields
     * under its own path so the file reads like the database it describes.
     *
     * Hand-written rather than serialised through a library because the values are
     * only strings, numbers and booleans, and because the grouping is the point: one
     * block per node, in assortment order.
     */
    private fun json(update: Map<String, Any?>, header: String): String {
        val byNode = linkedMapOf<String, MutableMap<String, Any?>>()
        for ((path, value) in update) {
            val node = path.substringBeforeLast('/')
            val field = path.substringAfterLast('/')
            byNode.getOrPut(node) { linkedMapOf() }[field] = value
        }
        // No provenance keys in here, tempting as they are: a multi-path update is
        // atomic and the rules reject an unknown path at the root, so one "_source"
        // would fail the whole upload. Provenance goes in the report beside it.
        require(header.isNotEmpty()) { "price list header missing" }
        return buildString {
            append("{\n")
            byNode.entries.forEachIndexed { index, (node, fields) ->
                append("""  ${quote(node)}: {""").append('\n')
                fields.entries.forEachIndexed { i, (field, value) ->
                    append("""    ${quote(field)}: ${literal(value)}""")
                    append(if (i == fields.size - 1) "\n" else ",\n")
                }
                append("  }")
                append(if (index == byNode.size - 1) "\n" else ",\n")
            }
            append("}\n")
        }
    }

    private fun literal(value: Any?): String = when (value) {
        null -> "null"
        is String -> quote(value)
        is Boolean -> value.toString()
        // Whole numbers without a trailing ".0", so a price of 2 reads as 2.
        is Double -> if (value == value.toLong().toDouble()) {
            value.toLong().toString()
        } else {
            value.toString()
        }
        else -> quote(value.toString())
    }

    private fun quote(text: String): String = buildString {
        append('"')
        for (c in text) when (c) {
            '"' -> append("\\\"")
            '\\' -> append("\\\\")
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            '\t' -> append("\\t")
            else -> if (c < ' ') append("\\u%04x".format(c.code)) else append(c)
        }
        append('"')
    }

    private fun round2(value: Double): Double = kotlin.math.round(value * 100.0) / 100.0

    /** Walks up from the test's working directory until the repository root. */
    private fun repoRoot(): File {
        var dir: File? = File("").absoluteFile
        while (dir != null) {
            if (File(dir, "settings.gradle.kts").isFile || File(dir, "settings.gradle").isFile) {
                return dir
            }
            dir = dir.parentFile
        }
        error("repository root not found from ${File("").absolutePath}")
    }

    private companion object {
        /** The price list this run reads. Gitignored — see `tmp/Terra`. */
        const val BNN_FILE = "tmp/Terra/plf_kw47.bnn"
        const val OUT_FILE = "firebase/fixtures/winter-catalog.json"
        const val REPORT_FILE = "firebase/fixtures/winter-catalog.report.txt"

        /** BNN files from Terra are DOS-encoded, as in the seller's file import. */
        const val BNN_CHARSET = "IBM850"

        /** Whose catalogue this is. */
        const val SELLER_ID = "cPkcZSiF3LMXjWoqW6AqpA9paoO2"

        /** What [ArticleNodes] writes per article, as a guard on the row count. */
        const val PUBLIC_FIELDS = 12
        const val PRIVATE_FIELDS = 11
    }
}
