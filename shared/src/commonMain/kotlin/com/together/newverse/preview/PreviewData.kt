package com.together.newverse.preview

import com.together.newverse.domain.model.*

/**
 * Simple basket item for preview purposes
 */
data class BasketItem(
    val article: Article,
    val quantity: Double
)

/**
 * Centralized preview/test data for Compose previews and testing
 * Based on test data from Universe project
 */
object PreviewData {

    val sampleArticles = listOf(
        Article(
            id = "0",
            productName = "Feigenbananen",
            available = true,
            imageUrl = "https://firebasestorage.googleapis.com/v0/b/fire-one-58ddc.appspot.com/o/images%2Ftmp6852941846258768194.tmp?alt=media&token=f4b2a6a2-a8fa-495b-a093-04c269e97abe",
            price = 2.3,
            unit = "kg",
            category = "Bananen",
            detailInfo = "Demeter Biobananen. Die Bananen stammen aus Peru und sind nach Vorgaben der Demeter angebaut.",
            searchTerms = "Banane,",
            weightPerPiece = 0.130
        ),
        Article(
            id = "1",
            productName = "Granny Smith",
            available = true,
            imageUrl = "https://firebasestorage.googleapis.com/v0/b/fire-one-58ddc.appspot.com/o/images%2F1609270827791_tmp1052065387795357072.tmp?alt=media&token=3ae17db0-a140-4a3d-bd6d-2843cfd5b36e",
            price = 2.3,
            unit = "kg",
            category = "Apfel",
            detailInfo = "Leicht säuerlicher Apfel. Die Äpfel wurden in Stralsund geerntet und sind mit dem europäischen Biosiegel versehen. Die Äpfel wurden nicht gespritzt und auch nicht gewachst.",
            searchTerms = "Apfel,Äpfel",
            weightPerPiece = 0.092
        ),
        Article(
            id = "2",
            productName = "Linda Kartoffeln",
            available = true,
            imageUrl = "https://firebasestorage.googleapis.com/v0/b/fire-one-58ddc.appspot.com/o/images%2Ftmp1576373532957500855.tmp?alt=media&token=81892fcd-c346-479a-8b56-4b56d7ce8381",
            price = 2.3,
            unit = "kg",
            category = "Kartoffel",
            detailInfo = "Festkochende Kartoffel vom Hof Apfeltraum. Die Kartoffeln wurden in dem Biohof Wizenau bei Buxdehude nach den Anforderungen des europäischen Biosiegels angebaut.",
            searchTerms = "Kartoffel,Kartoffeln",
            weightPerPiece = 0.060
        ),
        Article(
            id = "3",
            productName = "Stangensellerie",
            available = true,
            imageUrl = "https://firebasestorage.googleapis.com/v0/b/fire-one-58ddc.appspot.com/o/images%2Ftmp274159401886863829.tmp?alt=media&token=e725f46f-5ab3-440c-9586-c04c6e1b7392",
            price = 2.9,
            unit = "Stück",
            category = "Stangensellerie",
            detailInfo = "Aus der Umgebung. Die Kartoffeln wurden in dem Biohof Wizenau bei Buxdehude nach den Anforderungen des europäischen Biosiegels angebaut.",
            weightPerPiece = 1.0
        ),
        Article(
            id = "4",
            productName = "Atomic Red",
            available = true,
            imageUrl = "https://firebasestorage.googleapis.com/v0/b/fire-one-58ddc.appspot.com/o/images%2Ftmp7534516650759375907.tmp?alt=media&token=d474b967-46e9-45b2-8931-336f9c780ee3",
            price = 3.69,
            unit = "Bund",
            category = "Karotten",
            detailInfo = "Vom Biohof Waldheide. Die Karotten wurden in dem Biohof Wizenau bei Buxdehude nach den Anforderungen des europäischen Biosiegels angebaut.",
            weightPerPiece = 1.0,
            searchTerms = "Möhren,Möhre,Karotte,Karotten,"
        ),
        Article(
            id = "5",
            productName = "Erdbeeren",
            available = true,
            imageUrl = "https://firebasestorage.googleapis.com/v0/b/fire-one-58ddc.appspot.com/o/images%2Ftmp1145260240680560593.tmp?alt=media&token=0670c0da-e260-4d41-b5d8-1a119ea24a64",
            price = 5.69,
            unit = "Schale",
            category = "Erdbeeren",
            detailInfo = "Vom Biohof Waldheide. Die Erdbeeren stammen aus Friedenau vom Hof Bio Müller. Bei der Aufzucht wurden keinerlei Insektiziede verwendet.",
            weightPerPiece = 1.0,
            searchTerms = "Erdbeere"
        ),
        Article(
            id = "6",
            productName = "Knoblauch",
            available = true,
            imageUrl = "https://firebasestorage.googleapis.com/v0/b/fire-one-58ddc.appspot.com/o/images%2F1609405653076_tmp824873246873729560.tmp?alt=media&token=3cd22727-cec8-4d86-ba92-140b24c6675b",
            price = 10.69,
            unit = "kg",
            category = "Knoblauch",
            detailInfo = "Vom Biohof Waldheide. Der Knoblauch wurde nach den Produktionsvorgaben von Demeter erzeugt.",
            weightPerPiece = 0.030
        ),
        Article(
            id = "7",
            productName = "Siglinde Kartoffeln",
            available = true,
            imageUrl = "https://firebasestorage.googleapis.com/v0/b/fire-one-58ddc.appspot.com/o/images%2Ftmp1576373532957500855.tmp?alt=media&token=81892fcd-c346-479a-8b56-4b56d7ce8381",
            price = 2.9,
            unit = "kg",
            category = "Kartoffeln",
            detailInfo = "Festkochende Kartoffel vom Hof Apfeltraum. Die Kartoffeln wurden in dem Biohof Wizenau bei Buxdehude nach den Anforderungen des europäischen Biosiegels angebaut.",
            weightPerPiece = 0.090,
            searchTerms = "Kartoffel,"
        ),
        Article(
            id = "8",
            productName = "Boskop",
            available = true,
            imageUrl = "https://firebasestorage.googleapis.com/v0/b/fire-one-58ddc.appspot.com/o/images%2F1609270827791_tmp1052065387795357072.tmp?alt=media&token=3ae17db0-a140-4a3d-bd6d-2843cfd5b36e",
            price = 2.3,
            unit = "kg",
            category = "Apfel",
            detailInfo = "Saftig und sauer. Die Äpfel wurden in Stralsund geerntet und sind mit dem europäischen Biosiegel versehen. Die Äpfel wurden nicht gespritzt und auch nicht gewachst.",
            weightPerPiece = 0.115,
            searchTerms = "Äpfel,"
        ),
        Article(
            id = "9",
            productName = "Elstar",
            available = true,
            imageUrl = "https://firebasestorage.googleapis.com/v0/b/fire-one-58ddc.appspot.com/o/images%2F1609270827791_tmp1052065387795357072.tmp?alt=media&token=3ae17db0-a140-4a3d-bd6d-2843cfd5b36e",
            price = 2.3,
            unit = "kg",
            category = "Apfel",
            detailInfo = "Süß-sauerer Apfel. Die Äpfel wurden in Stralsund geerntet und sind mit dem europäischen Biosiegel versehen. Die Äpfel wurden nicht gespritzt und auch nicht gewachst.",
            weightPerPiece = 0.098,
            searchTerms = "Äpfel"
        )
    )

    val sampleBuyerProfiles = listOf(
        BuyerProfile(
            id = "buyer1",
            displayName = "Marco",
            emailAddress = "mmaleiter@gmail.com",
            telephoneNumber = "0172-1234567"
        ),
        BuyerProfile(
            id = "buyer2",
            displayName = "Mandy Leiter",
            emailAddress = "marcoleiter@arcor.de",
            telephoneNumber = "0172-7654321"
        )
    )

    val sampleMarkets = listOf(
        Market(
            id = "market1",
            name = "Wochenmarkt Onkel Toms Hütte",
            street = "Onkel-Tom-Straße",
            houseNumber = "99",
            zipCode = "14169",
            city = "Berlin",
            begin = "12:00",
            end = "18:30",
            dayOfWeek = "Donnerstag"
        ),
        Market(
            id = "market2",
            name = "Ökomarkt im Hansaviertel",
            street = "Altonaer Str.",
            houseNumber = "18",
            zipCode = "10557",
            city = "Berlin",
            begin = "12:00",
            end = "18:30",
            dayOfWeek = "Freitag"
        )
    )

    val sampleSellerProfile = SellerProfile(
        id = "seller1",
        displayName = "BodenSchätze",
        firstName = "Eric",
        lastName = "Dehn",
        street = "Frankfurther Allee",
        houseNumber = "27",
        city = "Berlin",
        zipCode = "14195",
        telephoneNumber = "01724623741",
        markets = sampleMarkets
    )

    val sampleOrderedProducts = listOf(
        OrderedProduct(
            id = "op1",
            productId = "0",
            productName = "Feigenbananen",
            unit = "kg",
            price = 2.3,
            amount = "2.0",
            amountCount = 2.0,
            piecesCount = 2
        ),
        OrderedProduct(
            id = "op2",
            productId = "1",
            productName = "Granny Smith",
            unit = "kg",
            price = 2.3,
            amount = "1.5",
            amountCount = 1.5,
            piecesCount = 2
        ),
        OrderedProduct(
            id = "op3",
            productId = "5",
            productName = "Erdbeeren",
            unit = "Schale",
            price = 5.69,
            amount = "1.0",
            amountCount = 1.0,
            piecesCount = 1
        )
    )

    val sampleOrders = listOf(
        Order(
            id = "order1",
            buyerProfile = sampleBuyerProfiles[0],
            articles = sampleOrderedProducts.take(2),
            createdDate = 1699027200000L, // Nov 3, 2023
            sellerId = "seller1",
            marketId = "market1",
            pickUpDate = 1699113600000L, // Nov 4, 2023 (+1 day)
            message = "Bitte ohne Plastik verpacken",
            notFavourite = false
        ),
        Order(
            id = "order2",
            buyerProfile = sampleBuyerProfiles[1],
            articles = listOf(sampleOrderedProducts[2]),
            createdDate = 1699027200000L, // Nov 3, 2023
            sellerId = "seller1",
            marketId = "market2",
            pickUpDate = 1699200000000L, // Nov 5, 2023 (+2 days)
            message = "",
            notFavourite = true
        )
    )

    val sampleBasketItems = listOf(
        BasketItem(
            article = sampleArticles[0],
            quantity = 2.0
        ),
        BasketItem(
            article = sampleArticles[1],
            quantity = 1.5
        ),
        BasketItem(
            article = sampleArticles[5],
            quantity = 1.0
        )
    )

    // Mock MainScreenState for previews
    val sampleMainScreenState = com.together.newverse.ui.MainScreenState(
        isLoading = false,
        articles = sampleArticles,
        selectedArticle = sampleArticles.firstOrNull(),
        selectedQuantity = 2.0,
        cartItemCount = 3,
        basketItems = sampleOrderedProducts,
        favouriteArticles = listOf("0", "1", "5"), // IDs of favourite articles
        error = null
    )

    val sampleMainScreenStateEmpty = com.together.newverse.ui.MainScreenState(
        isLoading = false,
        articles = emptyList(),
        selectedArticle = null,
        selectedQuantity = 0.0,
        cartItemCount = 0,
        basketItems = emptyList(),
        favouriteArticles = emptyList(),
        error = null
    )

    val sampleMainScreenStateLoading = com.together.newverse.ui.MainScreenState(
        isLoading = true,
        articles = emptyList(),
        selectedArticle = null,
        selectedQuantity = 0.0,
        cartItemCount = 0,
        basketItems = emptyList(),
        favouriteArticles = emptyList(),
        error = null
    )

    // Mock CustomerProfileScreenState for previews
    val sampleCustomerProfileState = com.together.newverse.ui.state.CustomerProfileScreenState(
        isLoading = false,
        error = null,
        profile = sampleBuyerProfiles[0],
        photoUrl = "https://i.pravatar.cc/300"
    )

    val sampleCustomerProfileStateLoading = com.together.newverse.ui.state.CustomerProfileScreenState(
        isLoading = true,
        error = null,
        profile = null,
        photoUrl = null
    )

    val sampleCustomerProfileStateEmpty = com.together.newverse.ui.state.CustomerProfileScreenState(
        isLoading = false,
        error = null,
        profile = null,
        photoUrl = null
    )
}
