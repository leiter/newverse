# Test Credentials for Firebase Authentication

## Test Account for Development

For testing the login functionality, you can use these test credentials if they exist in Firebase:

### Buyer Account
- Email: `test@buyer.com`
- Password: `password123`

### Seller Account
- Email: `test@seller.com`
- Password: `password123`

## Creating New Test Account

If the above accounts don't exist in your Firebase project:

1. Navigate to the Login screen
2. Click "Sign Up" to create a new account
3. Use any valid email format (e.g., `yourtest@email.com`)
4. Password must be at least 6 characters

## Firebase Console

To manage users directly:
1. Go to [Firebase Console](https://console.firebase.google.com)
2. Select your project
3. Navigate to Authentication > Users
4. Add users manually or view existing users

## Testing the Login Flow

1. **Launch the app** - App starts in guest mode
2. **Navigate to Login** - Use the navigation drawer or profile icon
3. **Enter credentials** - Use test account or create new
4. **Submit** - Should see loading state, then success/error
5. **On success** - Redirects to home with user authenticated

## Common Error Messages

- "No account found with this email address" - Email not registered
- "Incorrect password. Please try again" - Wrong password
- "Invalid email format" - Check email syntax
- "Network error" - Check internet connection
- "Too many failed attempts" - Wait before retrying