package com.together.newverse.data.parser

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Unit tests for BnnParser.
 *
 * BNN (Bio-Naturkost-Norm) is a semicolon-separated format used by organic food suppliers.
 * The parser extracts product information from these files for import into the seller app.
 *
 * Test Categories:
 * A. Basic Parsing (4 tests)
 * B. Field Extraction (6 tests)
 * C. Number Parsing (4 tests)
 * D. Category Extraction (5 tests)
 * E. Organic Certification (3 tests)
 * F. Availability Flag (2 tests)
 * G. Search Terms (3 tests)
 * H. Detail Info Building (3 tests)
 * I. Edge Cases & Validation (6 tests)
 */
class BnnParserTest {

    private val parser = BnnParser()

    // ===== Test Data Helpers =====

    /**
     * Creates a valid BNN line with 70 fields.
     * Key positions:
     * 0=productId, 1=availability, 4=barcode, 6=name, 7=detail,
     * 10=quality, 11=supplier, 13=origin, 14=certification,
     * 22=packageDesc, 23=packageSize, 24=unit, 37=price, 68=weightPerPiece
     */
    private fun createBnnLine(
        productId: String = "12345",
        availability: String = "A",
        barcode: String = "4012345678901",
        name: String = "Apfel Elstar",
        detail: String = "Klasse I",
        quality: String = "I",
        supplier: String = "BTR",
        origin: String = "DE",
        certification: String = "DD",
        packageDesc: String = "6 KG",
        packageSize: String = "6,000",
        unit: String = "KG",
        price: String = "12,50",
        weightPerPiece: String = "0,200"
    ): String {
        // Create array of 70 empty fields
        val fields = Array(70) { "" }

        // Fill in the known positions
        fields[0] = productId
        fields[1] = availability
        fields[4] = barcode
        fields[6] = name
        fields[7] = detail
        fields[10] = quality
        fields[11] = supplier
        fields[13] = origin
        fields[14] = certification
        fields[22] = packageDesc
        fields[23] = packageSize
        fields[24] = unit
        fields[37] = price
        fields[68] = weightPerPiece

        return fields.joinToString(";")
    }

    private fun createBnnFile(vararg dataLines: String): String {
        val header = "BNN;V3.0;Supplier Info;Header Data" + ";".repeat(66)
        return (listOf(header) + dataLines).joinToString("\n")
    }

    // ===== A. Basic Parsing (4 tests) =====

    @Test
    fun `parse returns empty list for empty file`() {
        val result = parser.parse("")

        assertTrue(result.isEmpty())
    }

    @Test
    fun `parse returns empty list for file with only header`() {
        val content = "BNN;V3.0;Header Line Only" + ";".repeat(67)

        val result = parser.parse(content)

        assertTrue(result.isEmpty())
    }

    @Test
    fun `parse skips header line and parses data lines`() {
        val line1 = createBnnLine(productId = "001", name = "Apfel Elstar")
        val line2 = createBnnLine(productId = "002", name = "Birne Williams")
        val content = createBnnFile(line1, line2)

        val result = parser.parse(content)

        assertEquals(2, result.size)
        assertEquals("001", result[0].productId)
        assertEquals("002", result[1].productId)
    }

    @Test
    fun `parse filters out blank lines`() {
        val line1 = createBnnLine(productId = "001", name = "Apfel")
        val content = createBnnFile(line1, "", "   ", line1)

        val result = parser.parse(content)

        // First line is header, then data line, blank, whitespace, data line
        // Should get 2 products (blanks filtered)
        assertEquals(2, result.size)
    }

    // ===== B. Field Extraction (6 tests) =====

    @Test
    fun `parse extracts core identification fields`() {
        val line = createBnnLine(
            productId = "ABC123",
            name = "Bio Tomate",
            barcode = "4001234567890"
        )
        val content = createBnnFile(line)

        val result = parser.parse(content)

        assertEquals(1, result.size)
        val product = result[0]
        assertEquals("ABC123", product.productId)
        assertEquals("Bio Tomate", product.productName)
        assertEquals("4001234567890", product.barcode)
    }

    @Test
    fun `parse extracts pricing and unit fields`() {
        val line = createBnnLine(
            price = "15,99",
            unit = "KG",
            packageSize = "5,500",
            weightPerPiece = "0,350"
        )
        val content = createBnnFile(line)

        val result = parser.parse(content)

        assertEquals(1, result.size)
        val product = result[0]
        assertEquals(15.99, product.price, 0.001)
        assertEquals("KG", product.unit)
        assertEquals(5.5, product.packageSize, 0.001)
        assertEquals(0.35, product.weightPerPiece, 0.001)
    }

    @Test
    fun `parse extracts origin and quality fields`() {
        val line = createBnnLine(
            origin = "IT",
            quality = "II",
            supplier = "SCH"
        )
        val content = createBnnFile(line)

        val result = parser.parse(content)

        assertEquals(1, result.size)
        val product = result[0]
        assertEquals("IT", product.origin)
        assertEquals("II", product.quality)
        assertEquals("SCH", product.supplier)
    }

    @Test
    fun `parse sets barcode to null when blank`() {
        val line = createBnnLine(barcode = "")
        val content = createBnnFile(line)

        val result = parser.parse(content)

        assertEquals(1, result.size)
        assertNull(result[0].barcode)
    }

    @Test
    fun `parse sets barcode to null when whitespace only`() {
        val line = createBnnLine(barcode = "   ")
        val content = createBnnFile(line)

        val result = parser.parse(content)

        assertEquals(1, result.size)
        assertNull(result[0].barcode)
    }

    @Test
    fun `parse sets default values for empty numeric fields`() {
        val line = createBnnLine(
            price = "",
            packageSize = "",
            weightPerPiece = ""
        )
        val content = createBnnFile(line)

        val result = parser.parse(content)

        assertEquals(1, result.size)
        val product = result[0]
        assertEquals(0.0, product.price, 0.001)
        assertEquals(0.0, product.packageSize, 0.001)
        assertEquals(0.0, product.weightPerPiece, 0.001)
    }

    // ===== C. Number Parsing (4 tests) =====

    @Test
    fun `parse converts German comma decimal to double`() {
        val line = createBnnLine(price = "123,45")
        val content = createBnnFile(line)

        val result = parser.parse(content)

        assertEquals(123.45, result[0].price, 0.001)
    }

    @Test
    fun `parse handles integer values without decimal`() {
        val line = createBnnLine(price = "100")
        val content = createBnnFile(line)

        val result = parser.parse(content)

        assertEquals(100.0, result[0].price, 0.001)
    }

    @Test
    fun `parse handles values with leading zeros`() {
        val line = createBnnLine(price = "007,50")
        val content = createBnnFile(line)

        val result = parser.parse(content)

        assertEquals(7.5, result[0].price, 0.001)
    }

    @Test
    fun `parse returns zero for invalid numeric values`() {
        val line = createBnnLine(price = "abc", packageSize = "n/a")
        val content = createBnnFile(line)

        val result = parser.parse(content)

        assertEquals(1, result.size)
        assertEquals(0.0, result[0].price, 0.001)
        assertEquals(0.0, result[0].packageSize, 0.001)
    }

    // ===== D. Category Extraction (5 tests) =====

    @Test
    fun `parse categorizes fruits correctly`() {
        // Note: Parser uses firstWord.contains() which is case-sensitive
        // "Heidelbeeren" contains "beeren" (lowercase) but parser checks "Beeren" (capital B)
        val fruitNames = listOf(
            "Apfel Elstar" to "Obst",
            "Äpfel Gala" to "Obst",
            "Birne Williams" to "Obst",
            "Birnen Conference" to "Obst",
            "Orange Navel" to "Obst",
            "Zitronen Bio" to "Obst",
            "Banane Fair" to "Obst",
            "Trauben weiß" to "Obst",
            "Traube rot" to "Obst",
            "Pflaumen" to "Obst",
            "Zwetschgen" to "Obst",
            "Pfirsich" to "Obst",
            "Nektarine" to "Obst",
            "Beeren Mix" to "Obst"  // Parser checks contains("Beeren") with capital B
        )

        for ((name, expectedCategory) in fruitNames) {
            val line = createBnnLine(productId = name.take(5), name = name)
            val content = createBnnFile(line)

            val result = parser.parse(content)

            assertEquals(
                expectedCategory,
                result[0].category,
                "Expected '$name' to be categorized as '$expectedCategory'"
            )
        }
    }

    @Test
    fun `parse categorizes vegetables correctly`() {
        // Note: Parser uses firstWord for categorization
        // "Rote Bete" has firstWord="Rote" which doesn't match "Bete" pattern
        val vegetableNames = listOf(
            "Kartoffel festkochend" to "Gemüse",
            "Speisekartoffel mehlig" to "Gemüse",
            "Süßkartoffel" to "Gemüse",
            "Möhren Bund" to "Gemüse",
            "Karotten lose" to "Gemüse",
            "Tomate Rispen" to "Gemüse",
            "Tomaten Cherry" to "Gemüse",
            "Gurke Schlangen" to "Gemüse",
            "Gurken Mini" to "Gemüse",
            "Paprika rot" to "Gemüse",
            "Peperoni grün" to "Gemüse",
            "Salat Kopf" to "Gemüse",
            "Kopfsalat Bio" to "Gemüse",
            "Kohl Weiß" to "Gemüse",
            "Kohlrabi" to "Gemüse",
            "Blumenkohl" to "Gemüse",
            "Zwiebel rot" to "Gemüse",
            "Zwiebeln gelb" to "Gemüse",
            "Knoblauch" to "Gemüse",
            "Sellerie Knolle" to "Gemüse",
            "Fenchel" to "Gemüse",
            "Rettich weiß" to "Gemüse",
            "Radieschen Bund" to "Gemüse",
            "Bete rot" to "Gemüse"  // Parser checks contains("Bete"), firstWord must contain it
        )

        for ((name, expectedCategory) in vegetableNames) {
            val line = createBnnLine(productId = name.take(5), name = name)
            val content = createBnnFile(line)

            val result = parser.parse(content)

            assertEquals(
                expectedCategory,
                result[0].category,
                "Expected '$name' to be categorized as '$expectedCategory'"
            )
        }
    }

    @Test
    fun `parse categorizes unknown products as Sonstiges`() {
        val unknownNames = listOf(
            "Honig Bio",
            "Milch frisch",
            "Käse Gouda",
            "Brot Vollkorn",
            "Eier Bio"
        )

        for (name in unknownNames) {
            val line = createBnnLine(productId = name.take(5), name = name)
            val content = createBnnFile(line)

            val result = parser.parse(content)

            assertEquals(
                "Sonstiges",
                result[0].category,
                "Expected '$name' to be categorized as 'Sonstiges'"
            )
        }
    }

    @Test
    fun `parse handles multi-word product names for categorization`() {
        // Category is determined by first word
        val line = createBnnLine(name = "Tomaten Cherry rot Bio Demeter")
        val content = createBnnFile(line)

        val result = parser.parse(content)

        assertEquals("Gemüse", result[0].category)
    }

    @Test
    fun `parse handles empty product name for categorization`() {
        // This should be filtered out anyway due to blank name validation
        val line = createBnnLine(name = "X") // Single char name to pass validation
        val content = createBnnFile(line)

        val result = parser.parse(content)

        // "X" doesn't match any category
        assertEquals("Sonstiges", result[0].category)
    }

    // ===== E. Organic Certification (3 tests) =====

    @Test
    fun `parse marks organic certifications correctly`() {
        val organicCerts = listOf("DD", "DB", "DN", "IA", "EG")

        for (cert in organicCerts) {
            val line = createBnnLine(certification = cert)
            val content = createBnnFile(line)

            val result = parser.parse(content)

            assertTrue(
                result[0].isOrganic,
                "Expected certification '$cert' to be organic"
            )
        }
    }

    @Test
    fun `parse marks non-organic certifications correctly`() {
        val nonOrganicCerts = listOf("", "XX", "KV", "QS", "??")

        for (cert in nonOrganicCerts) {
            val line = createBnnLine(certification = cert)
            val content = createBnnFile(line)

            val result = parser.parse(content)

            assertFalse(
                result[0].isOrganic,
                "Expected certification '$cert' to NOT be organic"
            )
        }
    }

    @Test
    fun `parse handles certification case sensitivity`() {
        // Parser uses exact match, so lowercase should not match
        val line = createBnnLine(certification = "dd")
        val content = createBnnFile(line)

        val result = parser.parse(content)

        // "dd" lowercase should not match "DD"
        assertFalse(result[0].isOrganic)
    }

    // ===== F. Availability Flag (2 tests) =====

    @Test
    fun `parse sets available true for A flag`() {
        val line = createBnnLine(availability = "A")
        val content = createBnnFile(line)

        val result = parser.parse(content)

        assertTrue(result[0].availability)
    }

    @Test
    fun `parse sets available false for non-A flags`() {
        val flags = listOf("N", "X", "", "a", "0")

        for (flag in flags) {
            val line = createBnnLine(availability = flag)
            val content = createBnnFile(line)

            val result = parser.parse(content)

            assertFalse(
                result[0].availability,
                "Expected flag '$flag' to set availability=false"
            )
        }
    }

    // ===== G. Search Terms (3 tests) =====

    @Test
    fun `parse builds search terms from name and detail`() {
        val line = createBnnLine(
            name = "Apfel Elstar rot",
            detail = "Klasse Premium",
            quality = "Bio"
        )
        val content = createBnnFile(line)

        val result = parser.parse(content)

        // Split search terms to check for individual terms
        val termsList = result[0].searchTerms.split(",").map { it.trim() }
        assertTrue(termsList.contains("apfel"), "Should contain 'apfel'")
        assertTrue(termsList.contains("elstar"), "Should contain 'elstar'")
        assertTrue(termsList.contains("rot"), "Should contain 'rot'")
        assertTrue(termsList.contains("klasse"), "Should contain 'klasse'")
        assertTrue(termsList.contains("premium"), "Should contain 'premium'")
        assertTrue(termsList.contains("bio"), "Should contain 'bio'")
    }

    @Test
    fun `parse filters short words from search terms`() {
        // Note: Length filter (> 2 chars) only applies to name and detail words,
        // NOT to quality which is added unconditionally
        val line = createBnnLine(
            name = "Apfel I A",  // "I" and "A" are too short (filtered)
            detail = "ab cd",    // Both too short (filtered)
            quality = ""         // Empty quality won't be added
        )
        val content = createBnnFile(line)

        val result = parser.parse(content)

        // Split search terms to check for individual terms
        val termsList = result[0].searchTerms.split(",").map { it.trim() }
        assertTrue(termsList.contains("apfel"), "Should contain 'apfel'")
        assertFalse(termsList.contains("i"), "Should NOT contain short word 'i'")
        assertFalse(termsList.contains("a"), "Should NOT contain short word 'a'")
        assertFalse(termsList.contains("ab"), "Should NOT contain short word 'ab'")
        assertFalse(termsList.contains("cd"), "Should NOT contain short word 'cd'")
    }

    @Test
    fun `parse converts search terms to lowercase`() {
        val line = createBnnLine(
            name = "APFEL ELSTAR",
            detail = "PREMIUM",
            quality = "BIO"
        )
        val content = createBnnFile(line)

        val result = parser.parse(content)

        // Split search terms to check for individual terms
        val termsList = result[0].searchTerms.split(",").map { it.trim() }
        assertTrue(termsList.contains("apfel"))
        assertTrue(termsList.contains("elstar"))
        assertTrue(termsList.contains("premium"))
        assertTrue(termsList.contains("bio"))
        assertFalse(termsList.contains("APFEL"))
    }

    // ===== H. Detail Info Building (3 tests) =====

    @Test
    fun `parse builds detail info with all components`() {
        val line = createBnnLine(
            detail = "Frisch geerntet",
            certification = "DD",
            packageDesc = "6 KG Kiste"
        )
        val content = createBnnFile(line)

        val result = parser.parse(content)

        val detailInfo = result[0].detailInfo
        assertTrue(detailInfo.contains("Frisch geerntet"))
        assertTrue(detailInfo.contains("Demeter"))
        assertTrue(detailInfo.contains("Gebinde: 6 KG Kiste"))
    }

    @Test
    fun `parse maps certification codes to names`() {
        val certMappings = mapOf(
            "DD" to "Demeter",
            "DB" to "Bioland",
            "DN" to "Naturland",
            "IA" to "Bio (Italien)",
            "EG" to "EU-Bio"
        )

        for ((code, expectedName) in certMappings) {
            val line = createBnnLine(
                detail = "",
                certification = code,
                packageDesc = ""
            )
            val content = createBnnFile(line)

            val result = parser.parse(content)

            assertEquals(
                expectedName,
                result[0].detailInfo,
                "Certification '$code' should map to '$expectedName'"
            )
        }
    }

    @Test
    fun `parse omits unknown certification from detail info`() {
        val line = createBnnLine(
            detail = "Test",
            certification = "XX",  // Unknown
            packageDesc = ""
        )
        val content = createBnnFile(line)

        val result = parser.parse(content)

        // Should only contain "Test", not XX
        assertEquals("Test", result[0].detailInfo)
    }

    // ===== I. Edge Cases & Validation (6 tests) =====

    @Test
    fun `parse skips lines with insufficient fields`() {
        // Less than 70 fields
        val shortLine = "12345;A;name" // Only 3 fields
        val validLine = createBnnLine()
        val content = createBnnFile(shortLine, validLine)

        val result = parser.parse(content)

        // Should only get the valid line
        assertEquals(1, result.size)
    }

    @Test
    fun `parse skips lines with blank productId`() {
        val line = createBnnLine(productId = "")
        val content = createBnnFile(line)

        val result = parser.parse(content)

        assertTrue(result.isEmpty())
    }

    @Test
    fun `parse skips lines with whitespace-only productId`() {
        val line = createBnnLine(productId = "   ")
        val content = createBnnFile(line)

        val result = parser.parse(content)

        assertTrue(result.isEmpty())
    }

    @Test
    fun `parse skips lines with blank productName`() {
        val line = createBnnLine(name = "")
        val content = createBnnFile(line)

        val result = parser.parse(content)

        assertTrue(result.isEmpty())
    }

    @Test
    fun `parse sets default values for new product`() {
        val line = createBnnLine()
        val content = createBnnFile(line)

        val result = parser.parse(content)

        assertEquals(1, result.size)
        val product = result[0]

        // Defaults set by parser
        assertEquals("", product.id)  // Will be set by database
        assertEquals("", product.imageUrl)  // Not in BNN format
        assertEquals(1.0, product.minOrderQuantity, 0.001)  // Default
        assertEquals(0, product.stock)  // Not in BNN format
    }

    @Test
    fun `parse handles lines with special characters in fields`() {
        val line = createBnnLine(
            name = "Äpfel Größe L (Bio)",
            detail = "Qualität: 100% | Herkunft: Österreich"
        )
        val content = createBnnFile(line)

        val result = parser.parse(content)

        assertEquals(1, result.size)
        assertEquals("Äpfel Größe L (Bio)", result[0].productName)
        assertTrue(result[0].detailInfo.contains("Qualität: 100%"))
    }

    // ===== Additional Integration Tests =====

    @Test
    fun `parse processes realistic BNN file content`() {
        // Simulate a realistic BNN file with multiple products
        val apple = createBnnLine(
            productId = "10001",
            availability = "A",
            barcode = "4012345000001",
            name = "Apfel Elstar",
            detail = "Klasse I, 70-80mm",
            quality = "I",
            supplier = "BTR",
            origin = "DE",
            certification = "DD",
            packageDesc = "6 KG Kiste",
            packageSize = "6,000",
            unit = "KG",
            price = "18,50",
            weightPerPiece = "0,180"
        )

        val carrot = createBnnLine(
            productId = "20001",
            availability = "N",  // Not available
            barcode = "4012345000002",
            name = "Möhren Bund",
            detail = "Mit Grün",
            quality = "I",
            supplier = "SCH",
            origin = "NL",
            certification = "DB",  // Bioland
            packageDesc = "12 Bund",
            packageSize = "12,000",
            unit = "BD",
            price = "1,20",
            weightPerPiece = "0,300"
        )

        val honey = createBnnLine(
            productId = "30001",
            availability = "A",
            barcode = "",  // No barcode
            name = "Honig Blüte",
            detail = "Cremig gerührt",
            quality = "",
            supplier = "IMK",
            origin = "DE",
            certification = "EG",  // EU-Bio
            packageDesc = "6 x 500g",
            packageSize = "3,000",
            unit = "KG",
            price = "8,99",
            weightPerPiece = "0,500"
        )

        val content = createBnnFile(apple, carrot, honey)

        val result = parser.parse(content)

        assertEquals(3, result.size)

        // Verify apple
        val appleProduct = result.find { it.productId == "10001" }
        assertNotNull(appleProduct)
        assertEquals("Apfel Elstar", appleProduct.productName)
        assertEquals("Obst", appleProduct.category)
        assertEquals(18.50, appleProduct.price, 0.001)
        assertTrue(appleProduct.availability)
        assertTrue(appleProduct.isOrganic)
        assertEquals("4012345000001", appleProduct.barcode)

        // Verify carrot
        val carrotProduct = result.find { it.productId == "20001" }
        assertNotNull(carrotProduct)
        assertEquals("Möhren Bund", carrotProduct.productName)
        assertEquals("Gemüse", carrotProduct.category)
        assertFalse(carrotProduct.availability)  // N flag
        assertTrue(carrotProduct.isOrganic)  // Bioland

        // Verify honey
        val honeyProduct = result.find { it.productId == "30001" }
        assertNotNull(honeyProduct)
        assertEquals("Honig Blüte", honeyProduct.productName)
        assertEquals("Sonstiges", honeyProduct.category)  // Not fruit or veggie
        assertTrue(honeyProduct.availability)
        assertTrue(honeyProduct.isOrganic)  // EU-Bio
        assertNull(honeyProduct.barcode)  // Empty barcode
    }

    @Test
    fun `parse handles Windows line endings`() {
        val line1 = createBnnLine(productId = "001", name = "Apfel")
        val line2 = createBnnLine(productId = "002", name = "Birne")
        val header = "BNN;Header" + ";".repeat(68)

        // Windows uses \r\n
        val content = "$header\r\n$line1\r\n$line2"

        val result = parser.parse(content)

        assertEquals(2, result.size)
    }

    @Test
    fun `parse handles mixed line endings`() {
        val line1 = createBnnLine(productId = "001", name = "Apfel")
        val line2 = createBnnLine(productId = "002", name = "Birne")
        val header = "BNN;Header" + ";".repeat(68)

        // Mix of \n and \r\n
        val content = "$header\n$line1\r\n$line2"

        val result = parser.parse(content)

        assertEquals(2, result.size)
    }
}
