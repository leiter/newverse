# GitHub Pages Setup Guide

GitHub Pages has been configured for the Newverse project with support, privacy, and terms pages using pure HTML.

## What Was Created

### 📄 HTML Pages (in `docs/`)

1. **index.html** - Main landing page
   - Clean, modern design with gradient logo
   - App overview and features
   - Centered layout (600px max width)
   - Matches qreverywhere.github.io style

2. **support.html** - Support & FAQ page
   - Common questions for customers and sellers
   - Contact information
   - Account deletion instructions

3. **privacy.html** - Privacy Policy
   - Complete GDPR and CCPA compliant policy
   - Firebase data collection disclosure
   - User rights and data management

4. **terms.html** - Terms of Service
   - User agreement and responsibilities
   - Liability limitations
   - Dispute resolution

### ⚙️ Configuration Files

5. **.github/workflows/pages.yml** - GitHub Actions workflow
   - Simple HTML deployment (no Jekyll needed)
   - Automatic deployment on push to main
   - Triggers when `docs/` files change

### 🎨 Design Features

All pages feature:
- **Responsive Design**: Mobile-friendly, centered layout
- **Brand Colors**: Teal/Green gradient (#008577 to #0A6308)
- **Modern UI**: Card-based layout with shadows and rounded corners
- **Clean Typography**: System fonts for fast loading
- **Smooth Animations**: Hover effects on links and buttons
- **Consistent Navigation**: Links between all pages

---

## 🚀 Deploy to GitHub Pages

### Step 1: Push to GitHub

```bash
cd /Users/user289697/Documents/newverse
git add .
git commit -m "Add GitHub Pages with HTML support pages"
git push origin main
```

Or use the automated script:
```bash
./DEPLOY_PAGES.sh
```

### Step 2: Enable GitHub Pages

1. Go to https://github.com/leiter/newverse/settings/pages
2. Under **Source**, select **GitHub Actions**
3. The workflow will automatically deploy

### Step 3: Wait for Deployment

- First deployment takes 2-5 minutes
- Check **Actions** tab to monitor progress
- Green checkmark = successful deployment

### Step 4: Verify Your Pages

Once deployed, pages will be available at:

```
Main:        https://leiter.github.io/newverse/
Support:     https://leiter.github.io/newverse/support.html
Privacy:     https://leiter.github.io/newverse/privacy.html
Terms:       https://leiter.github.io/newverse/terms.html
```

---

## 📱 Update App Store Metadata

Use these URLs in App Store Connect:

### Required URLs:

```
Support URL:        https://leiter.github.io/newverse/support.html
Privacy Policy URL: https://leiter.github.io/newverse/privacy.html
```

### Optional URLs:

```
Marketing URL:      https://leiter.github.io/newverse/
Terms of Service:   https://leiter.github.io/newverse/terms.html
```

These URLs have been added to `iosApp/APP_STORE_METADATA.md`.

---

## 🛠️ Customization

### Update Contact Email

Currently: **markro77@arcor.de**

To change:
```bash
cd docs
# Replace in all HTML files
sed -i '' 's/markro77@arcor.de/your-new-email@example.com/g' *.html
```

### Update Brand Colors

Edit the CSS in each HTML file:
```css
background: linear-gradient(135deg, #008577 0%, #0A6308 100%);
color: #008577;  /* Primary color */
```

### Update Logo

The logo uses an inline SVG. To change it, edit the `<svg>` element in each HTML file's `.logo` section.

### Add New Pages

1. Create a new HTML file in `docs/` directory
2. Copy the structure from an existing page
3. Update navigation links in all pages
4. Push to GitHub - automatic deployment

---

## 🎨 Design System

### Colors
- **Primary**: #008577 (Teal)
- **Secondary**: #0A6308 (Dark Green)
- **Background**: #fafafa (Light Gray)
- **Cards**: #ffffff (White)
- **Text**: #333333 (Dark Gray)

### Layout
- **Max Width**: 600px (centered)
- **Card Padding**: 30px
- **Border Radius**: 12px (cards), 8px (buttons)
- **Shadows**: 0 2px 8px rgba(0,0,0,0.1)

### Typography
- **Font**: System fonts (-apple-system, BlinkMacSystemFont)
- **H1**: 2.5rem, bold
- **H2**: 1.5rem
- **H3**: 1.2rem
- **Body**: 1rem, line-height 1.6

---

## 📊 Monitoring

### Check Deployment

Visit https://github.com/leiter/newverse/actions to see deployment status.

### Update Pages

Any changes to `docs/` folder pushed to `main` branch will automatically redeploy in 2-3 minutes.

---

## ✅ Pre-Launch Checklist

Before submitting to App Store:

- [ ] Push files to GitHub repository
- [ ] Enable GitHub Pages in repository settings
- [ ] Verify all pages load correctly
- [ ] Test navigation links between pages
- [ ] Check pages on mobile devices
- [ ] Verify contact email is correct
- [ ] Update App Store Connect with URLs
- [ ] Test external links (Firebase, Google)

---

## 🆘 Troubleshooting

### Pages Not Deploying

1. Check **Actions** tab for errors
2. Ensure GitHub Pages source is set to **GitHub Actions**
3. Verify workflow file exists: `.github/workflows/pages.yml`
4. Check that `docs/` folder contains HTML files

### 404 Errors

- URLs must include `/newverse/` in path
- Use `.html` extension: `support.html` not `support`
- Check GitHub Pages settings for correct repository

### Styling Issues

- All CSS is embedded in HTML files
- Check browser console for errors
- Test in different browsers (Chrome, Safari, Firefox)
- Verify responsive design on mobile

---

## 📝 Maintenance

### Regular Updates

**Privacy Policy**: Review annually or when:
- New data collection practices added
- Third-party services change
- Regulations require updates

**Terms of Service**: Update when:
- Business model changes
- New features added
- Legal requirements change

**Support Page**: Keep updated with:
- New FAQs from user questions
- Updated contact information
- New features

---

## 🔗 Comparison with qreverywhere

Your pages now match the qreverywhere.github.io style:
- ✅ Pure HTML with embedded CSS
- ✅ Centered, single-column layout (600px)
- ✅ Gradient logo design
- ✅ Card-based content sections
- ✅ Clean, modern aesthetic
- ✅ Smooth hover animations
- ✅ Mobile-responsive

---

*Setup created: April 5, 2025*
*Repository: https://github.com/leiter/newverse*
*Style: HTML + CSS (no Jekyll/Markdown)*
