package com.together.newverse.test

import com.together.newverse.domain.model.Article
import com.together.newverse.domain.model.BuyerProfile
import com.together.newverse.domain.model.Market
import com.together.newverse.domain.model.Order
import com.together.newverse.domain.model.OrderStatus
import com.together.newverse.domain.model.OrderedProduct
import com.together.newverse.domain.model.SellerProfile

/**
 * Test data for unit tests.
 * Based on PreviewData but simplified for testing.
 */
object TestData {

    val sampleArticles = listOf(
        Article(
            id = "article_1",
            productName = "Feigenbananen",
            available = true,
            imageUrl = "https://example.com/bananen.jpg",
            price = 2.3,
            unit = "kg",
            category = "Bananen",
            detailInfo = "Bio-Bananen aus Peru",
            searchTerms = "Banane,obst",
            weightPerPiece = 0.130
        ),
        Article(
            id = "article_2",
            productName = "Granny Smith",
            available = true,
            imageUrl = "https://example.com/apfel.jpg",
            price = 2.3,
            unit = "kg",
            category = "Apfel",
            detailInfo = "Leicht sauerlicher Apfel",
            searchTerms = "Apfel,obst",
            weightPerPiece = 0.092
        ),
        Article(
            id = "article_3",
            productName = "Linda Kartoffeln",
            available = false,
            imageUrl = "https://example.com/kartoffeln.jpg",
            price = 2.3,
            unit = "kg",
            category = "Kartoffel",
            detailInfo = "Festkochende Kartoffel",
            searchTerms = "Kartoffel,gemuse",
            weightPerPiece = 0.060
        ),
        Article(
            id = "article_4",
            productName = "Erdbeeren",
            available = true,
            imageUrl = "https://example.com/erdbeeren.jpg",
            price = 5.69,
            unit = "Schale",
            category = "Erdbeeren",
            detailInfo = "Frische Erdbeeren",
            searchTerms = "Erdbeere,obst",
            weightPerPiece = 1.0
        )
    )

    val sampleMarkets = listOf(
        Market(
            id = "market_1",
            name = "Wochenmarkt Onkel Toms Hutte",
            street = "Onkel-Tom-Strasse",
            houseNumber = "99",
            zipCode = "14169",
            city = "Berlin",
            begin = "12:00",
            end = "18:30",
            dayOfWeek = "Donnerstag"
        ),
        Market(
            id = "market_2",
            name = "Okomarkt im Hansaviertel",
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
        id = "seller_1",
        displayName = "BodenSchatze",
        firstName = "Eric",
        lastName = "Dehn",
        street = "Frankfurther Allee",
        houseNumber = "27",
        city = "Berlin",
        zipCode = "14195",
        telephoneNumber = "01724623741",
        markets = sampleMarkets
    )

    val sampleBuyerProfiles = listOf(
        BuyerProfile(
            id = "buyer_1",
            displayName = "Marco",
            emailAddress = "marco@example.com",
            telephoneNumber = "0172-1234567"
        ),
        BuyerProfile(
            id = "buyer_2",
            displayName = "Mandy Leiter",
            emailAddress = "mandy@example.com",
            telephoneNumber = "0172-7654321"
        )
    )

    val sampleOrderedProducts = listOf(
        OrderedProduct(
            id = "op_1",
            productId = "article_1",
            productName = "Feigenbananen",
            unit = "kg",
            price = 2.3,
            amount = "2.0",
            amountCount = 2.0,
            piecesCount = 2
        ),
        OrderedProduct(
            id = "op_2",
            productId = "article_2",
            productName = "Granny Smith",
            unit = "kg",
            price = 2.3,
            amount = "1.5",
            amountCount = 1.5,
            piecesCount = 2
        ),
        OrderedProduct(
            id = "op_3",
            productId = "article_4",
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
            id = "order_1",
            buyerProfile = sampleBuyerProfiles[0],
            articles = sampleOrderedProducts.take(2),
            createdDate = 1699027200000L,
            sellerId = "seller_1",
            marketId = "market_1",
            pickUpDate = 1699113600000L,
            message = "Bitte ohne Plastik verpacken",
            notFavourite = false,
            status = OrderStatus.PLACED
        ),
        Order(
            id = "order_2",
            buyerProfile = sampleBuyerProfiles[1],
            articles = listOf(sampleOrderedProducts[2]),
            createdDate = 1699027200000L,
            sellerId = "seller_1",
            marketId = "market_2",
            pickUpDate = 1699200000000L,
            message = "",
            notFavourite = true,
            status = OrderStatus.COMPLETED
        ),
        Order(
            id = "order_3",
            buyerProfile = sampleBuyerProfiles[0],
            articles = sampleOrderedProducts,
            createdDate = 1699027200000L,
            sellerId = "seller_1",
            marketId = "market_1",
            pickUpDate = 1699286400000L,
            message = "",
            notFavourite = false,
            status = OrderStatus.CANCELLED
        )
    )
}
