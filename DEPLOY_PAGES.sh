#!/bin/bash

# Deploy GitHub Pages for Newverse
# This script commits and pushes the documentation pages to GitHub

set -e  # Exit on error

echo "🚀 Deploying GitHub Pages for Newverse"
echo ""

# Navigate to repository root
cd "$(dirname "$0")"

echo "📝 Checking git status..."
git status --short

echo ""
echo "📦 Adding files to git..."
git add docs/
git add .github/workflows/pages.yml
git add GITHUB_PAGES_SETUP.md
git add iosApp/APP_STORE_METADATA.md

echo ""
echo "💾 Committing changes..."
git commit -m "Add GitHub Pages with support, privacy, and terms pages

- Add landing page (docs/index.md)
- Add support page with FAQs (docs/support.md)
- Add GDPR/CCPA compliant privacy policy (docs/privacy.md)
- Add terms of service (docs/terms.md)
- Configure Jekyll with Cayman theme
- Add GitHub Actions workflow for automatic deployment
- Update App Store metadata with page URLs

Pages will be available at:
- https://leiter.github.io/newverse/
- https://leiter.github.io/newverse/support.html
- https://leiter.github.io/newverse/privacy.html
- https://leiter.github.io/newverse/terms.html"

echo ""
echo "🌐 Pushing to GitHub..."
git push origin main

echo ""
echo "✅ Done!"
echo ""
echo "Next steps:"
echo "1. Enable GitHub Pages in repository settings:"
echo "   https://github.com/leiter/newverse/settings/pages"
echo ""
echo "2. Under 'Source', select 'GitHub Actions'"
echo ""
echo "3. Wait 2-5 minutes for deployment"
echo ""
echo "4. Verify pages are live:"
echo "   - https://leiter.github.io/newverse/"
echo "   - https://leiter.github.io/newverse/support.html"
echo "   - https://leiter.github.io/newverse/privacy.html"
echo "   - https://leiter.github.io/newverse/terms.html"
echo ""
echo "5. Add URLs to App Store Connect"
echo ""
echo "See GITHUB_PAGES_SETUP.md for detailed instructions."
