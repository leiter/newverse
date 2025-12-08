# Seller Migration Status

**Status:** ~32% Complete

## High Priority

### 1. Seller Profile Management
- [ ] Implement `FirebaseProfileRepository.saveSeller()`
- [ ] Implement `FirebaseProfileRepository.loadSellerProfile()`
- [ ] Create profile edit UI in SellerProfileScreen
- [ ] Add address fields (street, house number, city, zip)
- [ ] Add company name field

### 2. Market Location CRUD
- [ ] Create MarketDialog composable
- [ ] Add market form fields (name, address, day, times)
- [ ] Implement time pickers (begin/end)
- [ ] Implement add/edit/delete market
- [ ] Save markets to Firebase profile

### 3. Orders Real-time Loading
- [ ] Implement OrderRepository Firebase methods
- [ ] Add real-time listener for orders
- [ ] Filter orders by market dates
- [ ] Connect OrdersViewModel to repository

### 4. Product Edit/Delete
- [ ] Add edit mode to CreateProductScreen
- [ ] Load product data when editing
- [ ] Implement delete product (Database + Storage)
- [ ] Add delete confirmation dialog

## Medium Priority

### 5. Product List & Search
- [ ] Create ProductListScreen
- [ ] Add search and category filter
- [ ] Add product selection for edit/delete

### 6. Overview Dashboard Data
- [ ] Load real product/order statistics
- [ ] Calculate revenue metrics

### 7. Login Flow Enhancement
- [ ] Add seller profile check after login
- [ ] Navigate to profile creation if new

## Complete

- [x] Product Creation (CreateProductScreen, CreateProductViewModel)
- [x] Image upload to Firebase Storage
- [x] Category and Unit dropdowns
- [x] Form validation (German)
- [x] Overview screen with real Firebase data

## Key Files

**New Project:**
- Screens: `shared/src/commonMain/.../ui/screens/sell/`
- Repositories: `shared/src/commonMain/.../data/repository/`
- Models: `shared/src/commonMain/.../domain/model/`
