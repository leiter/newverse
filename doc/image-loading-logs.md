# Image Loading Logs Guide

## Overview

Image loading now includes comprehensive logging to help debug and verify that images are being loaded correctly.

## Log Format

All image loading logs use the 🖼️ emoji prefix for easy filtering:

```
🖼️ [ComponentName]: Loading image for '[ProductName]' from URL: [URL]
🖼️ [ComponentName]: No image URL for '[ProductName]', showing placeholder
```

## Components with Logging

### 1. ModernProductCard (Home Screen Grid)
**Location**: `MainScreenModern.kt`
**Image Size**: 100dp tall

**Logs**:
```
🖼️ ModernProductCard: Loading image for 'Organic Carrots' from URL: https://example.com/carrots.jpg
🖼️ ModernProductCard: No image URL for 'Fresh Tomatoes', showing placeholder
```

### 2. ProductListItem (List View)
**Location**: `ProductListItem.kt`
**Image Size**: 56x56dp thumbnail

**Logs**:
```
🖼️ ProductListItem: Loading image for 'Organic Carrots' from URL: https://example.com/carrots.jpg
🖼️ ProductListItem: No image URL for 'Fresh Tomatoes', showing placeholder
```

### 3. ProductDetailCard (Detail View)
**Location**: `ProductDetailCard.kt`
**Image Size**: 200dp tall

**Logs**:
```
🖼️ ProductDetailCard: Loading image for 'Organic Carrots' from URL: https://example.com/carrots.jpg
🖼️ ProductDetailCard: No image URL for 'Fresh Tomatoes', showing placeholder
```

## How to View Logs

### Method 1: Filter by Emoji (Recommended)
```bash
adb logcat | grep "🖼️"
```

**Output**:
```
🖼️ ModernProductCard: Loading image for 'Organic Carrots' from URL: https://firebasestorage.googleapis.com/.../carrots.jpg
🖼️ ModernProductCard: No image URL for 'Fresh Tomatoes', showing placeholder
🖼️ ProductListItem: Loading image for 'Organic Carrots' from URL: https://firebasestorage.googleapis.com/.../carrots.jpg
```

### Method 2: Combined Filter with Firebase
```bash
adb logcat | grep -E "🖼️|🔥"
```

**Output** (shows both image loading and article loading):
```
🔥 FirebaseArticleRepository: Sending ADDED article 'Organic Carrots' (id=abc123)
🖼️ ModernProductCard: Loading image for 'Organic Carrots' from URL: https://example.com/carrots.jpg
🔥 FirebaseArticleRepository: Sending ADDED article 'Fresh Tomatoes' (id=def456)
🖼️ ModernProductCard: No image URL for 'Fresh Tomatoes', showing placeholder
```

### Method 3: Complete Debug Output
```bash
adb logcat | grep -E "🖼️|🔥|🔐|📦|🎬|Coil"
```

**Shows**:
- 🔐 Authentication events
- 🔥 Firebase article events
- 📦 UnifiedAppViewModel product loading
- 🎬 MainScreenViewModel article loading
- 🖼️ Image loading attempts
- Coil library logs

## Expected Log Flow

### App Startup with Images

```
1. 🔐 FirebaseAuthRepository: Auth state changed - userId=guest_123, isAnonymous=true
2. 🔥 FirebaseArticleRepository: Found first seller ID: seller_001
3. 🔥 FirebaseArticleRepository: Database reference obtained: articles/seller_001
4. 🔥 FirebaseArticleRepository: onChildAdded - key=article_001
5. 🔥 FirebaseArticleRepository: Sending ADDED article 'Organic Carrots' (id=article_001)
6. 📦 UnifiedAppViewModel.loadProducts: Received article event - mode=0, id=article_001, name=Organic Carrots
7. 🖼️ ModernProductCard: Loading image for 'Organic Carrots' from URL: https://example.com/carrots.jpg
8. D/Coil: Loading image: https://example.com/carrots.jpg
9. D/Coil: Successfully loaded image
```

### App Startup without Images

```
1. 🔐 FirebaseAuthRepository: Auth state changed - userId=guest_123, isAnonymous=true
2. 🔥 FirebaseArticleRepository: Found first seller ID: seller_001
3. 🔥 FirebaseArticleRepository: Database reference obtained: articles/seller_001
4. 🔥 FirebaseArticleRepository: onChildAdded - key=article_001
5. 🔥 FirebaseArticleRepository: Sending ADDED article 'Fresh Tomatoes' (id=article_001)
6. 📦 UnifiedAppViewModel.loadProducts: Received article event - mode=0, id=article_001, name=Fresh Tomatoes
7. 🖼️ ModernProductCard: No image URL for 'Fresh Tomatoes', showing placeholder
```

## Troubleshooting

### Issue 1: No Image Logs at All

**Symptoms**:
- No 🖼️ logs appear
- Articles are loading (🔥 logs present)

**Possible Causes**:
1. ModernProductCard not being rendered
2. Articles list is empty
3. UI not composing

**Check**:
```bash
adb logcat | grep -E "🔥|📦"
```
Look for article loading logs. If articles are loading but no image logs, the UI might not be composing.

### Issue 2: Always Shows "No image URL"

**Symptoms**:
```
🖼️ ModernProductCard: No image URL for 'Product Name', showing placeholder
```

**Possible Causes**:
1. `imageUrl` field is empty in Firebase
2. `imageUrl` field is missing from Article model mapping
3. ArticleDto not mapping imageUrl correctly

**Check Firebase Data**:
```bash
# Verify imageUrl exists in Firebase
adb logcat | grep "🔥.*ADDED"
```

**Verify ArticleDto mapping**:
Check that `ArticleDto` in `FirebaseArticleRepository` maps `imageUrl`:
```kotlin
data class ArticleDto(
    val imageUrl: String = "",  // Must be present
    ...
)
```

### Issue 3: Image URL Present but Not Loading

**Symptoms**:
```
🖼️ ModernProductCard: Loading image for 'Product' from URL: https://example.com/image.jpg
(No Coil logs follow)
```

**Possible Causes**:
1. Network issue
2. Invalid URL
3. CORS issue
4. Missing internet permission

**Check Coil Logs**:
```bash
adb logcat -s Coil
```

**Verify Internet Permission** (Android):
```xml
<!-- In androidApp/src/main/AndroidManifest.xml -->
<uses-permission android:name="android.permission.INTERNET" />
```

### Issue 4: Coil Errors

**Check for Coil errors**:
```bash
adb logcat | grep -i "coil.*error\|coil.*fail"
```

**Common errors**:
- `UnknownHostException`: Network issue, check connectivity
- `FileNotFoundException`: Invalid URL or file doesn't exist
- `SSLException`: SSL certificate issue

## Testing Checklist

### 1. Verify Logs Appear
```bash
adb logcat -c  # Clear logs
# Launch app
adb logcat | grep "🖼️"
```

**Expected**: See image loading logs for each product

### 2. Test with Image URL
Add test article with image:
```json
{
  "imageUrl": "https://picsum.photos/200/300",
  "productName": "Test Product"
}
```

**Expected log**:
```
🖼️ ModernProductCard: Loading image for 'Test Product' from URL: https://picsum.photos/200/300
```

### 3. Test without Image URL
Add test article without image:
```json
{
  "imageUrl": "",
  "productName": "Test Product 2"
}
```

**Expected log**:
```
🖼️ ModernProductCard: No image URL for 'Test Product 2', showing placeholder
```

### 4. Verify Coil Loading
```bash
adb logcat | grep -E "🖼️|Coil"
```

**Expected**:
```
🖼️ ModernProductCard: Loading image for 'Test Product' from URL: https://picsum.photos/200/300
D/Coil: Loading image: https://picsum.photos/200/300
D/Coil: Fetching from network
D/Coil: Successfully loaded image
```

## Summary

Image loading logs help you:
- ✅ Verify imageUrl is being passed from Article
- ✅ Confirm AsyncImage is being triggered
- ✅ Debug why images aren't loading
- ✅ Test with different image URLs
- ✅ Identify placeholder vs actual image scenarios

**Quick Debug Command**:
```bash
adb logcat -c && adb logcat | grep -E "🖼️|🔥.*article|Coil"
```

This shows the complete flow from Firebase article loading to image rendering.
