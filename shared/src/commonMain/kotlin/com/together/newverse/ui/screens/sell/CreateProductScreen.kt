package com.together.newverse.ui.screens.sell

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.together.newverse.domain.model.ProductCategory
import com.together.newverse.domain.model.ProductUnit
import com.together.newverse.ui.state.UnifiedAppAction
import com.together.newverse.ui.state.UnifiedUiAction
import com.together.newverse.util.ImagePickerResult
import com.together.newverse.util.LocalImagePicker
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateProductScreen(
    onNavigateBack: () -> Unit,
    onAction: (UnifiedAppAction) -> Unit = {},
    viewModel: CreateProductViewModel = koinViewModel()
) {
    // Get ImagePicker from CompositionLocal
    val imagePicker = LocalImagePicker.current
        ?: error("ImagePicker not provided. Make sure to wrap app with CompositionLocalProvider(LocalImagePicker provides imagePicker)")
    val uiState by viewModel.uiState.collectAsState()
    val productName by viewModel.productName.collectAsState()
    val productId by viewModel.productId.collectAsState()
    val searchTerms by viewModel.searchTerms.collectAsState()
    val price by viewModel.price.collectAsState()
    val unit by viewModel.unit.collectAsState()
    val category by viewModel.category.collectAsState()
    val weightPerPiece by viewModel.weightPerPiece.collectAsState()
    val detailInfo by viewModel.detailInfo.collectAsState()
    val available by viewModel.available.collectAsState()
    val imageData by viewModel.imageData.collectAsState()
    val uploadProgress by viewModel.uploadProgress.collectAsState()

    val scope = rememberCoroutineScope()

    // Helper function to show snackbar via app-level dispatch
    fun showSnackbar(message: String) {
        onAction(UnifiedUiAction.ShowSnackbar(message))
    }

    // Handle UI state changes
    LaunchedEffect(uiState) {
        when (uiState) {
            is CreateProductUiState.Success -> {
                showSnackbar("Produkt erfolgreich gespeichert!")
                viewModel.resetState()
                onNavigateBack()
            }
            is CreateProductUiState.Error -> {
                val error = (uiState as CreateProductUiState.Error).message
                showSnackbar(error)
            }
            else -> {}
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Image Section
        ImageSection(
            imageData = imageData,
            onPickImage = {
                scope.launch {
                    when (val result = imagePicker.pickImage()) {
                        is ImagePickerResult.Success -> {
                            viewModel.onImageSelected(result.imageData)
                        }
                        is ImagePickerResult.Error -> {
                            showSnackbar("Fehler: ${result.message}")
                        }
                        ImagePickerResult.Cancelled -> {}
                    }
                }
            },
            onTakePhoto = {
                scope.launch {
                    when (val result = imagePicker.takePhoto()) {
                        is ImagePickerResult.Success -> {
                            viewModel.onImageSelected(result.imageData)
                        }
                        is ImagePickerResult.Error -> {
                            showSnackbar("Fehler: ${result.message}")
                        }
                        ImagePickerResult.Cancelled -> {}
                    }
                }
            }
        )

            // Product Name (required)
            OutlinedTextField(
                value = productName,
                onValueChange = viewModel::onProductNameChange,
                label = { Text("Produktname *") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            // Search Terms (required)
            OutlinedTextField(
                value = searchTerms,
                onValueChange = viewModel::onSearchTermsChange,
                label = { Text("Suchbegriffe *") },
                supportingText = { Text("Durch Komma getrennt") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            // Product ID (optional)
            OutlinedTextField(
                value = productId,
                onValueChange = viewModel::onProductIdChange,
                label = { Text("Artikelnummer") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            // Price and Unit (required)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = price,
                    onValueChange = viewModel::onPriceChange,
                    label = { Text("Preis *") },
                    prefix = { Text("€") },
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )

                // Unit Selector
                UnitSelector(
                    selectedUnit = unit,
                    onUnitSelected = viewModel::onUnitChange,
                    modifier = Modifier.weight(1f)
                )
            }

            // Weight per piece (required for countable units)
            val unitEnum = ProductUnit.fromDisplayName(unit)
            if (unitEnum?.isCountable == true) {
                OutlinedTextField(
                    value = weightPerPiece,
                    onValueChange = viewModel::onWeightPerPieceChange,
                    label = { Text("Gewicht pro Stück (kg) *") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }

            // Category Selector (required)
            CategorySelector(
                selectedCategory = category,
                onCategorySelected = viewModel::onCategoryChange,
                modifier = Modifier.fillMaxWidth()
            )

            // Detail Info (optional)
            OutlinedTextField(
                value = detailInfo,
                onValueChange = viewModel::onDetailInfoChange,
                label = { Text("Zusatzinformationen") },
                supportingText = { Text("Herkunft, Qualität, Bio-Siegel, etc.") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
                maxLines = 5
            )

            // Availability Switch
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Verfügbar")
                Switch(
                    checked = available,
                    onCheckedChange = viewModel::onAvailableChange
                )
            }

            // Upload Progress
            if (uiState is CreateProductUiState.Saving && uploadProgress > 0f) {
                LinearProgressIndicator(
                    progress = { uploadProgress },
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = "Uploading: ${(uploadProgress * 100).toInt()}%",
                    style = MaterialTheme.typography.bodySmall
                )
            }

            // Action Buttons
            Button(
                onClick = { viewModel.saveProduct() },
                modifier = Modifier.fillMaxWidth(),
                enabled = uiState !is CreateProductUiState.Saving
            ) {
                if (uiState is CreateProductUiState.Saving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Text("Speichern")
            }

            OutlinedButton(
                onClick = onNavigateBack,
                modifier = Modifier.fillMaxWidth(),
                enabled = uiState !is CreateProductUiState.Saving
            ) {
                Text("Abbrechen")
            }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun ImageSection(
    imageData: ByteArray?,
    onPickImage: () -> Unit,
    onTakePhoto: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp)
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            if (imageData != null) {
                // Display image from ByteArray using Coil3
                AsyncImage(
                    model = imageData,
                    contentDescription = "Produktbild",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
                // Overlay buttons to change image
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    IconButton(onClick = onPickImage) {
                        Icon(Icons.Default.Edit, "Foto ändern")
                    }
                    IconButton(onClick = onTakePhoto) {
                        Icon(Icons.Default.AddCircle, "Neues Foto")
                    }
                }
            } else {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        "Produktfoto hinzufügen *",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        IconButton(onClick = onPickImage) {
                            Icon(Icons.Default.Edit, "Foto auswählen")
                        }
                        IconButton(onClick = onTakePhoto) {
                            Icon(Icons.Default.AddCircle, "Foto aufnehmen")
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CategorySelector(
    selectedCategory: String,
    onCategorySelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val categories = ProductCategory.getAllDisplayNames()

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier
    ) {
        OutlinedTextField(
            value = selectedCategory,
            onValueChange = {},
            readOnly = true,
            label = { Text("Kategorie *") },
            trailingIcon = {
                Icon(Icons.Default.ArrowDropDown, "Kategorie auswählen")
            },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor()
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            categories.forEach { cat ->
                DropdownMenuItem(
                    text = { Text(cat) },
                    onClick = {
                        onCategorySelected(cat)
                        expanded = false
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UnitSelector(
    selectedUnit: String,
    onUnitSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val units = ProductUnit.getAllDisplayNames()

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier
    ) {
        OutlinedTextField(
            value = selectedUnit,
            onValueChange = {},
            readOnly = true,
            label = { Text("Einheit *") },
            trailingIcon = {
                Icon(Icons.Default.ArrowDropDown, "Einheit auswählen")
            },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor()
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            units.forEach { u ->
                DropdownMenuItem(
                    text = { Text(u) },
                    onClick = {
                        onUnitSelected(u)
                        expanded = false
                    }
                )
            }
        }
    }
}

