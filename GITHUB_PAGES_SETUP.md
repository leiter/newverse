# GitHub Pages Setup Guide

**Verified against the live site and the repository on 2026-11-20.** Pure HTML with
embedded CSS, no Jekyll, no Markdown, no build step.

## What is served

### There are two parallel page sets, and both are live

This is the first thing to know, because it is easy to edit one and think the job is
done. The legal pages exist twice:

| Set | Linked from | Shape |
|---|---|---|
| `docs/privacy.html`, `support.html`, `terms.html` | the **store listings** (App Store Connect, Play Console — the URLs below) | English, longer |
| `docs/de/*` and `docs/en/*` | the **app**, `AboutScreenModern.kt` opens `…/newverse/de/privacy.html` | localized, shorter |

`docs/index.html` is **only a language picker** — two buttons into `de/` and `en/`. It
is not the landing page; `docs/de/index.html` and `docs/en/index.html` are, and they
carry the logo, features and download sections.

Neither set is dead. **A change to one needs the same change to the other.**

### The pages

1. **index.html** — language picker into `de/` and `en/`
2. **de/index.html**, **en/index.html** — the actual landing pages: gradient logo, app
   overview, feature lists for customers and sellers, links to the three legal pages
3. **support.html** (+ `de/`, `en/`) — Support & FAQ, contact, account deletion
4. **privacy.html** (+ `de/`, `en/`) — GDPR/CCPA policy, Firebase disclosure, user rights
5. **terms.html** (+ `de/`, `en/`) — user agreement, liability, dispute resolution

### How it deploys

**Deploy from a branch: `main`, folder `/docs`.** There is no workflow in this
repository — no `.github/` directory has ever existed here, in any branch. GitHub
itself creates a "pages build and deployment" run on each push, which is why the
Actions tab shows deployments that no file in the repo describes.

So: **push `docs/` changes to `main` and they go live in a couple of minutes.** No
other step, no script.

There is also no `CNAME`, so the site serves on `leiter.github.io/newverse/` and not on
a custom domain.

### 🎨 Design Features

All pages feature:
- **Responsive Design**: Mobile-friendly, centered layout
- **Brand Colors**: the Bodenschätze flyer palette — brand green `#0A6308` with
  soil brown `#5A3E2B`, badge orange `#FA9C4D` on paper `#F6F2E8`

> **The site is on the app's palette since 2026-11-20.** It previously served the
> pre-2026-10-26 teal `#008577`, which left site and app looking like two products.
> The migration was a pure recolour — no copy, markup or layout changed — and it
> fixed two contrast failures on the way: the teal was 4.35:1 on the old background
> (used for links and bold text, so under the 4.5:1 AA bar) and the footer grey was
> 2.73:1. Every text pair on the site now clears AA, the worst at 5.98:1.
> `doc/guides/theme.md` is the source of truth for the palette.
- **Modern UI**: Card-based layout with shadows and rounded corners
- **Clean Typography**: System fonts for fast loading
- **Smooth Animations**: Hover effects on links and buttons
- **Consistent Navigation**: Links between all pages

---

## 🚀 Publishing a change

Pages is already enabled; this is the whole routine.

```bash
git add docs/
git commit -m "..."
git push origin main        # live in ~2 minutes
```

Then **check it rather than assume it**, because a push that succeeds says nothing
about what the CDN serves:

```bash
curl -s https://leiter.github.io/newverse/privacy.html | grep -o '#[0-9a-fA-F]\{6\}' | sort -u
```

If the old values are still coming back after a few minutes, the deployment is behind,
not the file.

**Only `main` publishes.** A `docs/` change committed on a feature branch is invisible
until it merges — which matters when a long-lived branch is being held back, as
`price-per-100g` was in November 2026.

### The URLs

```
Language picker:  https://leiter.github.io/newverse/
Landing (de/en):  https://leiter.github.io/newverse/de/index.html
Support:          https://leiter.github.io/newverse/support.html
Privacy:          https://leiter.github.io/newverse/privacy.html
Terms:            https://leiter.github.io/newverse/terms.html
```

The store listings use the three root URLs. The app opens the localized ones under
`de/` — same pages, different copies.

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

Currently: **bodenschaetze@cutthecrap.link**

To change — note `*.html` alone misses the localized set, which is half the site:
```bash
cd docs
sed -i 's/bodenschaetze@cutthecrap.link/your-new-email@example.com/g' \
    *.html de/*.html en/*.html
```
(`sed -i ''` in an earlier version of this file was macOS syntax and fails on Linux.)

### Update Brand Colors

Edit the CSS in each HTML file:
```css
background: linear-gradient(135deg, #0A6308 0%, #5A3E2B 100%);
color: #0A6308;  /* Primary color */
```

Take new values from `doc/guides/theme.md`, not from here — that guide is kept in
step with `ui/theme/Color.kt`, and this file is a copy that can fall behind it.

### Update Logo

The logo uses an inline SVG. To change it, edit the `<svg>` element in each HTML file's `.logo` section.

### Add New Pages

1. Create the HTML file in `docs/`, and the `de/` and `en/` copies if it is a page the
   app links to
2. Copy the structure from an existing page — the CSS is embedded per file, so there is
   no shared stylesheet to add it to
3. Update the navigation links in **every** page, both sets
4. Push to `main`

---

## 🎨 Design System

### Colors

The flyer palette, same values as the app. Roles as `doc/guides/theme.md` assigns
them — in particular the orange is a **fill, never a text colour**.

- **Primary**: `#0A6308` (BodenGreen) — h1, h2, links, list ticks
- **Secondary**: `#5A3E2B` (BodenSoil) — h3, gradient end, link hover
- **Accent**: `#FA9C4D` (BodenOrange) — the highlight box's left rule
- **Background**: `#F6F2E8` (BodenPaper)
- **Cards**: `#FFFDF8` (BodenCard)
- **Text**: `#1B2A16` (BodenInk), muted `#4A5145` (BodenMuted)

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

https://github.com/leiter/newverse/actions lists the "pages build and deployment" runs
GitHub generates. They are not from a workflow in this repo, so there is no workflow
file to fix if one fails — the failure is in the Pages settings or in the HTML.

Any `docs/` change pushed to `main` redeploys in 2–3 minutes.

---

## ✅ Checklist when the legal pages change

- [ ] Both sets updated — root **and** `de/` + `en/` (see the table at the top)
- [ ] Navigation links between pages still correct
- [ ] Contact email correct in all of them
- [ ] Pushed to `main`, and the live page checked with `curl`, not assumed
- [ ] Layout still sane at phone width
- [ ] External links (Firebase, Google) still resolve
- [ ] App Store Connect / Play Console URLs still point at the root copies

---

## 🆘 Troubleshooting

### Pages Not Deploying

1. Confirm the change is on **`main`** — a feature branch publishes nothing
2. Check the **Actions** tab for a failed "pages build and deployment"
3. Check Settings → Pages still reads **Deploy from a branch**, `main` / `/docs`.
   Do **not** switch it to "GitHub Actions": there is no workflow in this repo, so
   that setting would stop the site deploying altogether
4. Check `docs/` still contains the HTML files

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

*Setup created: April 5, 2025. Recoloured onto the Bodenschätze palette and this guide
corrected against the live site on 2026-11-20 — the workflow it used to describe
(`.github/workflows/pages.yml`) never existed, and `DEPLOY_PAGES.sh`, which referenced
it, was deleted the same day.*
*Repository: https://github.com/leiter/newverse*
*Style: HTML + CSS, one embedded stylesheet per file (no Jekyll, no Markdown, no build)*
