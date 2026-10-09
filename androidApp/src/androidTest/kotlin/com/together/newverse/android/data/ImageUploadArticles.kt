package com.together.newverse.android.data

/**
 * Where an article's Terra product image comes from, and where it goes.
 *
 * Images are supplier data like the BNN file and stay out of git. Copy the
 * `tmp/terra-images/<id>.webp` files to `tmp/androidTest-assets/terra-images/<id>.webp`
 * (the layout [imageAssetPath] expects); the androidTest source set packs that folder
 * as assets, see `androidApp/build.gradle.kts`. The whole price list is covered:
 * one image per BNN row.
 */

/** Asset path for one article's Terra product image. */
fun imageAssetPath(productId: String) = "terra-images/$productId.webp"

/**
 * Storage path for one article's Terra product image.
 *
 * Derived from the article number, so re-uploading overwrites the same object instead
 * of leaving the previous one behind as an orphan.
 */
fun terraImageStoragePath(productId: String) = "images/terra/$productId.webp"

/**
 * A small subset of the catalog, picked to prove the image-upload path:
 * [StorageRepository.uploadImage] once per article, then
 * [SellerArticleRepository.saveSellerArticles] with the returned URL.
 *
 * Superseded by `CatalogUploadTests`, which does the same for every row; kept as the
 * cheap check to run first when the upload path misbehaves.
 */
val IMAGE_UPLOAD_ARTICLE_IDS = listOf("111116", "112108", "120605", "122790", "124231")
