# BNN Parser Guide

## Overview

The BNN (Bio-Naturkost-Norm) parser converts product data files in BNN format into structured `Product` objects for use in the Newverse marketplace application.

## BNN Format

BNN is a standardized format used in the organic food industry in Germany for exchanging product catalog data between suppliers and retailers.

### File Structure

```
Line 1: Header (metadata)
Line 2+: Product data (semicolon-separated)
```

### Example Data Line

```
111116;A;00000000;0000;;;Zitronen, gelb Kal 5-6;;;II;BTR;;ZA;EG;;0;0302;0;500;;1;6 KG;6,000;KG;1;N;;999470;0;0;0;0;0;1;0;0,00;0,00;4,25;...
```

## Usage

### Basic Parsing

```kotlin
import com.together.newverse.data.parser.BnnParser
import com.together.newverse.domain.model.Product

// Read BNN file content
val fileContent = File("plf.bnn").readText()

// Parse to Product list
val parser = BnnParser()
val products: List<Product> = parser.parse(fileContent)

// Use products
products.forEach { product ->
    println("${product.productName}: €${product.price}/${product.unit}")
}
```

### Using Example Helper Functions

```kotlin
import com.together.newverse.data.parser.BnnParserExample

val fileContent = File("plf.bnn").readText()

// Get all products
val allProducts = BnnParserExample.parseFile(fileContent)

// Get only fruit (Obst)
val fruits = BnnParserExample.parseAndFilterByCategory(fileContent, "Obst")

// Get only organic products
val organicProducts = BnnParserExample.parseOrganicProducts(fileContent)

// Get products in price range
val affordableProducts = BnnParserExample.parseProductsInPriceRange(
    fileContent,
    minPrice = 0.0,
    maxPrice = 5.0
)

// Get only available products
val availableProducts = BnnParserExample.parseAvailableProducts(fileContent)

// Group by category
val productsByCategory = BnnParserExample.parseAndGroupByCategory(fileContent)
productsByCategory.forEach { (category, products) ->
    println("$category: ${products.size} products")
}

// Get statistics
val stats = BnnParserExample.getProductStatistics(fileContent)
println(stats)
```

## Field Mappings

The parser extracts the following information from BNN format:

| Product Field | BNN Position | Description | Example |
|--------------|--------------|-------------|---------|
| `productId` | 0 | Article number | "111116" |
| `productName` | 6 | Product name | "Zitronen, gelb Kal 5-6" |
| `price` | 37 | Price per unit (EUR) | 4.25 |
| `unit` | 24 | Unit of measurement | "KG", "ST", "BT" |
| `packageSize` | 23 | Package size | 6.0 |
| `origin` | 13 | Country code | "DE", "IT", "ES" |
| `quality` | 10 | Quality grade | "I", "II", "Bio" |
| `supplier` | 11 | Supplier code | "BTR", "SCH" |
| `barcode` | 4 | EAN barcode | "4060271000778" |
| `availability` | 1 | Available flag | "A" = available |
| `isOrganic` | 14 | Derived from certification | DD, DB, DN, EG |
| `category` | - | Derived from product name | "Obst", "Gemüse" |
| `detailInfo` | 7 | Product details | Combination of fields |
| `weightPerPiece` | 68 | Weight calculation | 1.0 |

## Certification Codes

The parser recognizes these organic certification codes:

- **DD** - Demeter
- **DB** - Bioland
- **DN** - Naturland
- **IA** - Bio (Italy)
- **EG** - EU-Bio

Products with any of these certifications will have `isOrganic = true`.

## Category Detection

The parser automatically categorizes products based on their names:

### Obst (Fruit)
- Äpfel/Apfel, Birnen, Orangen, Zitronen, Bananen
- Trauben, Pflaumen, Zwetschgen, Pfirsiche, Nektarinen
- Beeren (all berry types)

### Gemüse (Vegetables)
- Kartoffeln, Möhren, Tomaten, Gurken, Paprika
- Salat, Kohl/Kohlrabi, Zwiebeln, Knoblauch
- Sellerie, Fenchel, Rettich, Radieschen, Rote Bete

### Sonstiges (Other)
- Everything else

## Unit Codes

Common units found in BNN data:

- **KG** - Kilogram
- **ST** - Stück (piece)
- **BT** - Beutel (bag)
- **SC** - Schale (tray/container)
- **BD** - Bund (bunch)
- **KI** - Kiste (crate)
- **PA** - Packung (package)
- **NE** - Netz (net)

## Error Handling

The parser handles errors gracefully:

- **Skips invalid lines** - Lines with insufficient fields are skipped with a warning
- **Null safety** - Missing fields default to empty strings or 0.0
- **Returns non-null list** - Even on complete parsing failure, returns empty list (never null)

## Performance Considerations

For large BNN files (thousands of products):

```kotlin
// Consider parsing asynchronously
suspend fun parseAsync(fileContent: String): List<Product> =
    withContext(Dispatchers.Default) {
        BnnParser().parse(fileContent)
    }

// Or use Flow for streaming
fun parseAsFlow(fileContent: String): Flow<Product> = flow {
    val parser = BnnParser()
    fileContent.lines().drop(1).forEach { line ->
        parser.parseLine(line)?.let { emit(it) }
    }
}
```

## Example Output

When parsing the Terra Naturkost dataset (`plf.bnn`):

```
Product Statistics:
- Total Products: 312
- Organic Products: 298
- Available Products: 289
- Categories: Gemüse, Obst, Sonstiges
- Average Price: €2.87
- Price Range: €0.63 - €22.10
```

Sample parsed products:
```
Zitronen, gelb Kal 5-6: €4.25/KG
Apfel Gala: €2.02/KG
Bananen (18 kg - EPS Kiste): €1.59/KG
Demeter Heidelbeeren 8 x 250g: €3.62/SC
```

## Integration with Repository

To use the parser in your repository layer:

```kotlin
class ProductRepository {
    private val parser = BnnParser()

    suspend fun importFromBnn(filePath: String): Result<Int> {
        return try {
            val fileContent = File(filePath).readText()
            val products = parser.parse(fileContent)

            // Save to database
            products.forEach { product ->
                database.insertProduct(product)
            }

            Result.success(products.size)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
```

## Future Enhancements

Potential improvements to the parser:

1. **Image URL mapping** - Map product IDs to image URLs from separate source
2. **Stock integration** - Parse stock levels if available in BNN extensions
3. **Price history** - Track price changes over multiple BNN file imports
4. **Validation** - Add stricter validation rules for required fields
5. **BNN 2.0 support** - Support newer BNN format versions

## References

- [BNN Format Specification](https://www.bnn-online.de/)
- Product data class: `/shared/src/commonMain/kotlin/com/together/newverse/domain/model/Product.kt`
- Parser implementation: `/shared/src/commonMain/kotlin/com/together/newverse/data/parser/BnnParser.kt`
