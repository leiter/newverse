package com.together.newverse.ui.screens.buy

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Badge
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.SubcomposeAsyncImage
import com.together.newverse.domain.model.Article
import com.together.newverse.ui.state.MainScreenState
import com.together.newverse.ui.state.BuyAction
import com.together.newverse.ui.state.BuyMainScreenAction
import com.together.newverse.util.formatPrice
import newverse.shared.generated.resources.Res
import newverse.shared.generated.resources.favorites_discover_empty
import newverse.shared.generated.resources.favorites_empty
import newverse.shared.generated.resources.favorites_tab_discover
import newverse.shared.generated.resources.favorites_tab_favorites
import newverse.shared.generated.resources.place_holder_landscape
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * FavoritesScreen - Displays favorites and non-favorites in a tabbed layout
 *
 * Follows the project's screen pattern:
 * - Receives state and callbacks from NavGraph (no direct ViewModel injection)
 * - Uses stateless Content composable for the actual UI
 */
@Composable
fun FavoritesScreen(
    state: MainScreenState,
    onAction: (BuyAction) -> Unit,
    onNavigateBack: () -> Unit = {}
) {
    FavoritesContent(
        articles = state.articles.filter { it.available },
        favouriteIds = state.favouriteArticles,
        onToggleFavorite = { articleId ->
            onAction(BuyMainScreenAction.ToggleFavourite(articleId))
        }
    )
}

@Composable
private fun FavoritesContent(
    articles: List<Article>,
    favouriteIds: List<String>,
    onToggleFavorite: (String) -> Unit
) {
    var selectedTabIndex by remember { mutableIntStateOf(0) }
    val tabTitles = listOf(
        stringResource(Res.string.favorites_tab_favorites),
        stringResource(Res.string.favorites_tab_discover)
    )

    // Split articles into favorites and non-favorites
    val favoriteArticles = articles.filter { favouriteIds.contains(it.id) }
    val nonFavoriteArticles = articles.filter { !favouriteIds.contains(it.id) }

    Column(modifier = Modifier.fillMaxSize()) {
        // Tab Row
        TabRow(
            selectedTabIndex = selectedTabIndex,
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.primary
        ) {
            tabTitles.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTabIndex == index,
                    onClick = { selectedTabIndex = index },
                    text = {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(title)
                            // Show count badge
                            val count = if (index == 0) favoriteArticles.size else nonFavoriteArticles.size
                            Badge { Text(count.toString()) }
                        }
                    }
                )
            }
        }

        // Tab Content
        when (selectedTabIndex) {
            0 -> FavoritesTab(
                articles = favoriteArticles,
                onRemoveFavorite = onToggleFavorite
            )
            1 -> DiscoverTab(
                articles = nonFavoriteArticles,
                onAddFavorite = onToggleFavorite
            )
        }
    }
}

@Composable
private fun FavoritesTab(
    articles: List<Article>,
    onRemoveFavorite: (String) -> Unit
) {
    if (articles.isEmpty()) {
        EmptyState(message = stringResource(Res.string.favorites_empty))
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(articles, key = { it.id }) { article ->
                FavoriteProductCard(
                    article = article,
                    isFavorite = true,
                    onToggleFavorite = { onRemoveFavorite(article.id) }
                )
            }
        }
    }
}

@Composable
private fun DiscoverTab(
    articles: List<Article>,
    onAddFavorite: (String) -> Unit
) {
    if (articles.isEmpty()) {
        EmptyState(message = stringResource(Res.string.favorites_discover_empty))
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(articles, key = { it.id }) { article ->
                FavoriteProductCard(
                    article = article,
                    isFavorite = false,
                    onToggleFavorite = { onAddFavorite(article.id) }
                )
            }
        }
    }
}

@Composable
private fun FavoriteProductCard(
    article: Article,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Product Image
            SubcomposeAsyncImage(
                model = article.imageUrl,
                contentDescription = article.productName,
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(12.dp)),
                contentScale = ContentScale.Crop,
                loading = {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    }
                },
                error = {
                    Image(
                        painter = painterResource(Res.drawable.place_holder_landscape),
                        contentDescription = null,
                        contentScale = ContentScale.Crop
                    )
                }
            )

            Spacer(modifier = Modifier.width(12.dp))

            // Product Info
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = article.productName,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${article.price.formatPrice()}€ / ${article.unit}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            // Toggle Button
            IconButton(
                onClick = onToggleFavorite,
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    imageVector = if (isFavorite) Icons.Default.Remove else Icons.Default.Add,
                    contentDescription = if (isFavorite) "Remove from favorites" else "Add to favorites",
                    tint = if (isFavorite) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun EmptyState(message: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
