# Development Order Date Offset

## Purpose
For development and testing purposes, all newly placed orders have their **pickup date** set to **9 days in the future** (beyond tomorrow). This allows testing orders with future pickup dates without having to wait, making it easier to test order scheduling, date validation, and pickup date logic.

## How It Works

### Configuration
The offset is configured in `AppMetaState`:
```kotlin
data class AppMetaState(
    val environment: Environment = Environment.DEVELOPMENT,
    val devOrderDateOffsetDays: Int = 9 // Offset pickup date by 9 days
)
```

### Implementation
When an order is placed in `BasketViewModel.checkout()`:

1. **Calculate offset**: 9 days = 9 * 24 * 60 * 60 * 1000 milliseconds
2. **Use current time for createdDate**: `Clock.System.now().toEpochMilliseconds()`
3. **Offset pickUpDate**: `tomorrow + devOffsetMillis` (tomorrow + 9 days = 10 days from now)
4. **Save order**: Order stored with today's createdDate and future pickUpDate

### Example

**Without offset** (production):
- Order created: November 12, 2025 (today)
- Pickup date: November 13, 2025 (tomorrow)
- Days until pickup: 1 day

**With offset** (development):
- Order created: November 12, 2025 (today)
- Pickup date: November 22, 2025 (tomorrow + 9 days = 10 days from now)
- Days until pickup: 10 days

## Why 9 Days?

- **Test future pickup dates**: Test orders with pickup dates well into the future
- **Test pickup date logic**: Verify date calculations work correctly with extended timeframes
- **Test order editing**: Orders are editable until 3 days before pickup, so 10 days gives a good testing window
- **Test ordering restrictions**: Can verify date-based validation logic
- **Test UI date display**: Verify proper formatting of future dates

## Logging

The system logs offset calculations:
```
🛒 BasketViewModel.checkout: DEVELOPMENT MODE - 9 day pickup offset
🛒   createdDate: 1731427200000 (today)
🛒   Original pickUpDate: 1731513600000 (tomorrow)
🛒   Adjusted pickUpDate: 1732291200000 (tomorrow + 9 days)
```

## Files Modified

1. **UnifiedAppState.kt**
   - Added `devOrderDateOffsetDays: Int = 9` to AppMetaState
   - Changed environment to `Environment.DEVELOPMENT`

2. **BasketViewModel.kt** (commonMain)
   - Added 9-day offset calculation in `checkout()` (lines 258-267)
   - Kept `createdDate` as current time (today)
   - Adjusted `pickUpDate` to tomorrow + 9 days (10 days from now)
   - Added development mode logging

3. **FirebaseOrderRepository.kt** (androidMain)
   - Receives pre-adjusted order from BasketViewModel
   - No offset calculation needed here

## Disabling for Production

To disable the offset for production:

1. **Change environment**:
   ```kotlin
   val environment: Environment = Environment.PRODUCTION
   ```

2. **Set offset to 0**:
   ```kotlin
   val devOrderDateOffsetDays: Int = 0
   ```

3. **Or remove the offset code** from `BasketViewModel.checkout()` (lines 258-267)

## Testing Scenarios

With the 9-day pickup offset, you can test:

✅ **Future Pickup Dates**: Test orders scheduled well into the future
✅ **Date Formatting**: Verify future dates display correctly
✅ **Order Editing**: Test editing orders (editable until 3 days before pickup)
✅ **Sorting**: Orders sort by pickup date correctly
✅ **Date Validation**: Test validation logic for future dates
✅ **Pickup Date Display**: Verify proper formatting of dates 10+ days out
✅ **Order Restrictions**: Test date-based ordering restrictions

## Future Enhancements

Could be extended to:
- Make offset configurable per environment
- Add UI toggle for dev mode offset
- Support different offsets for different test scenarios
- Add offset to other time-based features

## Build Status
✅ **BUILD SUCCESSFUL**

## Notes

- This is a **development-only** feature
- **Remove or disable** before production release
- Currently hardcoded to **9 days**
- Applies to **all new orders** when active
