package com.together.newverse.android.data

/**
 * A small subset of [offerArticles], picked to prove the image-upload path before wiring all
 * 317 Terra articles: [StorageRepository.uploadImage] once per article, then
 * [SellerArticleRepository.saveSellerArticle] with the returned URL.
 *
 * Images are supplier data like the BNN file and stay out of git. Copy the matching
 * `tmp/terra-images/<id>.webp` files to `tmp/androidTest-assets/terra-images/<id>.webp`
 * (same layout the asset path below expects); the androidTest source set packs that folder
 * as assets, see `androidApp/build.gradle.kts`.
 */
val IMAGE_UPLOAD_ARTICLE_IDS = listOf("111116", "112108", "120605", "122790", "124231")

/** Asset path for one article's Terra product image. */
fun imageAssetPath(productId: String) = "terra-images/$productId.webp"
