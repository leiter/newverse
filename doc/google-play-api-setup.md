# Google Play API Setup (for Fastlane Supply)

## 1. Create a Google Play API Service Account

1. Go to **Google Play Console** → Setup → API access
2. Click **"Link to a Google Cloud Project"** (or create a new one)
3. In **Google Cloud Console** (you'll be redirected):
   - Go to **IAM & Admin → Service Accounts**
   - Click **"Create Service Account"**
   - Name it (e.g. `fastlane-supply`)
   - Click **Create and Continue** → skip role → Done
4. Click the new service account → **Keys** tab → **Add Key → JSON**
   - Download the `.json` key file — keep it safe, treat it like a password

## 2. Grant Access in Play Console

1. Back in **Play Console → Setup → API access**
2. Find your new service account in the list → click **"Grant access"**
3. Set permissions:
   - **App permissions** → select your app
   - Role: **"Release manager"** (or "Admin" if you also need financial data)
4. **Invite user** → confirm

## 3. Configure Fastlane

Update `androidApp/fastlane/Appfile`:

```ruby
json_key_file("path/to/google-play-api-key.json")
package_name("com.together.buy")
```

Store the key file at `androidApp/fastlane/google-play-api-key.json` and add it to `.gitignore` immediately:

```
androidApp/fastlane/google-play-api-key.json
```

## 4. Test the Connection

```bash
cd androidApp
fastlane run validate_play_store_json_key json_key:"fastlane/google-play-api-key.json"
```

Or fetch existing metadata as a dry run:

```bash
fastlane supply init --json_key fastlane/google-play-api-key.json
```

## 5. CI/CD (optional)

Store the JSON content as a secret (e.g. `GOOGLE_PLAY_JSON_KEY`) and write it to a temp file in the workflow:

```yaml
- run: echo '${{ secrets.GOOGLE_PLAY_JSON_KEY }}' > /tmp/gp-key.json
- run: fastlane deploy_buy json_key:/tmp/gp-key.json
```

## Notes

- The service account takes ~24h to propagate after being granted access in Play Console before API calls work reliably.
- Never commit the `.json` key file to version control.
