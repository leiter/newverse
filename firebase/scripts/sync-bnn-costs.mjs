#!/usr/bin/env node
/**
 * Fill `acquirePrice` and `markupFactor` on live articles from a week's Terra BNN file.
 *
 * The seller half of the catalog was never filled in: every row in production carries
 * `acquirePrice: 0` and `markupFactor: 1`, so the app can show a price but not what it
 * earns, and the CSV tax export has no cost to report. The price list has the cost, and
 * the catalog already has the price — this script joins the two.
 *
 * The companion to `sync-bnn-availability.mjs`, and deliberately disjoint from it: that
 * one owns `available` and `productId` and decides what can be sold this week, this one
 * owns `acquirePrice` and `markupFactor` and decides nothing about the shop. Run that
 * one first — it is what put Terra article numbers in `productId`, which is what makes
 * the matching here exact rather than a guess at names.
 *
 * ### The price stays, the margin is derived
 *
 * `price` is **never written**. It is the seller's decision and the one number buyers
 * see; a script that recomputed it from a markup would reprice a market stall from a
 * wholesaler's list. So the arithmetic runs the other way:
 *
 *     markupFactor = price / (acquirePrice × (1 + taxRate))
 *
 * the inverse of `ProductPricing.sellPrice`, exactly as the seller form's own reverse
 * calculation does it. The stored margin then matches the price actually charged, and
 * a repricing decision can be made later in the app, against real numbers, by the
 * person whose business it is.
 *
 * ### What it refuses to do
 *
 * Writing a wrong cost is worse than writing none — it would quietly misstate every
 * margin and every tax figure derived from it. So a row is skipped, and named in the
 * report, when:
 *
 * - **No live price-list row.** Either `productId` points at nothing in this week's
 *   file, or at a row flagged `X` (delisted). About a quarter of this catalog is the
 *   farm's own produce (Milan, Robuschka, Oxehella, the herbs), which is never in a
 *   Terra list and whose cost only the seller knows.
 * - **The units disagree** and nothing says how to convert. Terra invoices Kohlrabi
 *   per piece and carrots per kilo; where the stall's unit differs, the conversion
 *   must come from the article's own `weightPerPiece` or from [REPOINT]'s `divide`,
 *   never from a guess at how much a Wirsing weighs.
 * - **The price is below cost.** A factor under 1 is either the wrong row matched or a
 *   loss the seller has not noticed; both deserve a human. `--allow-below-cost` writes
 *   it anyway.
 * - **No price at all**, so there is nothing to derive a margin from.
 *
 * ### Tax
 *
 * Read per row from BNN field 34 (1 = 7 %, 2 = 19 %), not assumed: Süßkartoffeln are
 * 19 % while everything around them is 7 %, and using the catalog's own default would
 * overstate that one article's margin by eleven points. Where the row's rate differs
 * from what the article stores, `taxRate` is written too — a public field, but not one
 * a buyer sees, and a wrong one makes every figure above it wrong.
 *
 * ### Repointing
 *
 * An article number can move between weeks while the produce stays on the shelf: this
 * week Knoblauch went from 124403 to 124410, Spinat from 125614 to 125615. [REPOINT]
 * carries those, each one checked by hand against the product's name, and `--repoint`
 * writes the new number into `productId` so the next run needs no table. Nothing is
 * repointed by similarity — a number is either in that table or the row is skipped.
 *
 * Writes go as one multi-path update per seller, keyed per field, so nothing outside
 * the fields named above is touched. Same shape as `ArticleNodes.saveUpdate`.
 *
 * Dry run by default. Pass --apply to write.
 *
 *   node sync-bnn-costs.mjs --bnn <plf.bnn> --from-export <export.json>
 *   node sync-bnn-costs.mjs --bnn <plf.bnn> --project <id> --apply
 *   ... --report <file.csv>        every row and why, for checking afterwards
 *   ... --repoint                  also write the corrected productId
 *   ... --loose                     source an orphaned article from the nearest row
 *   ... --publish                   sell exactly what got a cost, and nothing else
 *   ... --allow-below-cost         write a factor under 1 instead of skipping
 */
import { execFileSync } from "node:child_process";
import { readFileSync, writeFileSync } from "node:fs";

// BNN v3 column positions, as BnnParser.kt reads them.
const P_ID = 0, P_FLAG = 1, P_BARCODE = 4, P_NAME = 6, P_QUALITY = 9, P_SUPPLIER = 10;
const P_ORIGIN = 12, P_CERT = 13, P_PACKAGE = 22, P_UNIT = 23, P_TAX = 33, P_PRICE = 37;

/**
 * Article numbers that moved, and the live row they moved to.
 *
 * Hand-checked against this week's file, product by product, which is why this is a
 * table and not a matcher. `divide` turns a crate price into a piece price where Terra
 * sells by the Kiste and the stall by the piece — 8 heads of cauliflower to a crate.
 */
const REPOINT = {
  "124403": { to: "124410", why: "Knoblauch, getrocknet (ES)" },
  "125614": { to: "125615", why: "Spinat, lose" },
  "122545": { to: "122531", why: "Stangensellerie" },
  "127470": { to: "127465", why: "Kürbis Hokkaido orange" },
  "125713": { to: "125710", why: "Chinakohl" },
  "122230": { to: "122235", why: "Kohlrabi, weiß" },
  "126319": { to: "126322", why: "Blumenkohl, 8 St je Kiste", divide: 8 },
};

/**
 * Articles whose stored number is wrong for them, by article id rather than by number.
 *
 * [REPOINT] cannot carry these: the number they hold is right for *another* article
 * and must keep working there — 124249 really is `Zwiebeln, rot`, it is just not
 * `Große Küchenzwiebeln`. Each one was read against this week's list by hand.
 *
 * They are here because the first availability run matched on names, which its own
 * documentation warns cannot separate one apple from another, and the number it
 * guessed then looked authoritative to everything downstream.
 *
 * `to` names the row the article really is. `skip` means this week's list has no row
 * for it and the cost is the seller's to enter: a wrong cost would misstate the margin
 * and every tax figure drawn from it, which is worse than an empty field.
 */
const FIX = {
  "-MVpiBcikUhTg_IkKwlA": { to: "124264", why: "Große Küchenzwiebeln 70-90+, nicht rote Zwiebeln" },
  "-MVq0yUkHJzy-Uhe2IvL": { to: "128270", why: "Austernseitling = Austernpilz, nicht Kräuterseitling" },
  "-MVaZbwxd94nAo74q9H6": { skip: "Rote Pepperoni: 122415 ist Rote Bete, diese Woche keine Peperoni" },
  "-MVXlVhDWm90j13xMyld": { skip: "Belana: 1284522 ist Kartoffelscheiben, Sorte nicht in der Liste" },
  "-MVXm-wsuwYT4eobodMW": { skip: "Goldmarie: 1284522 ist Kartoffelscheiben, Sorte nicht in der Liste" },
  "-MVpkqgjDq1wUUQ2KkCW": { skip: "Eier M: 129110 ist Größe L, andere Größe anderer Preis" },
  "-MVpl4vMk2eWpIlEX6MT": { skip: "Eier S: 129110 ist Größe L, andere Größe anderer Preis" },
  // Varieties this week's list does not carry, given the nearest live row of the same
  // produce. The stall keeps the name and the photograph it has and sources from
  // whatever the list offers, which is what the week actually looks like: one apple
  // stands in for another, and the cost is then right even though the variety is not.
  // Each pairing avoids a row another article already uses, so the shelf stays varied.
  "-MV1cvn7vblVAk2aKosY": { to: "1121690", why: "Auralia -> Apfel Pinova" },
  "-MV1_jFpdEpoCL8uBiyR": { to: "1121621", why: "Autento -> Apfel Santana" },
  "-MV1ZpzRuH_As_xgnzf3": { to: "112029", why: "Braeburn -> Apfel Kizuri" },
  "-MV1cKUCoJ_aJg5IGCTb": { to: "112080", why: "Galant -> Apfel Roter Altländer" },
  "-MV1YYVCMnpX0MV5aRGU": { to: "1121871", why: "Marnica -> Apfel Gala" },
  // The farm's own carrot varieties, all pointing at the delisted Brodowin row. Five
  // articles, five different live carrots, so each keeps a cost of its own.
  "-MVWlvxZegkz1IVNPHeW": { to: "120608", why: "Möhren -> Möhren 5 kg" },
  "-MVaX2Bu5_9CK6_YcxAp": { to: "120610", why: "Milan -> Möhren 10 kg" },
  "-MVaXZLetHGvX9yL6A_O": { to: "120612", why: "Maruschka -> Möhren Auehof Reese" },
  "-MVaXMzsgShxwcF_gScU": { to: "120643", why: "Jaune de Doubs -> Möhren violett Purpurne Paula" },
  "-MVaXmLUHALt_Ls_MnhO": { to: "120650", why: "Oxehella -> Möhren violett Purple Sun" },
  // Potato varieties against the two festkochend rows nothing else uses.
  "-MVXlVhDWm90j13xMyld": { to: "122828", why: "Belana -> Speisekartoffel Bellinda fk" },
  "-MVXm-wsuwYT4eobodMW": { to: "122803", why: "Goldmarie -> Speisekartoffel Leyla vfk" },
  // Right produce, wrong row: a yellow beet is not a red one, and Peperoni are back
  // in the list this week under a spelling the stall does not use.
  "-MVpxrEpDWd04wSNQVix": { to: "122416", why: "Wintersonne -> Gelbe Bete" },
  "-MVaZbwxd94nAo74q9H6": { to: "127266", why: "Rote Pepperoni -> Peperoni rot" },
  "-MVXCn93giHmENeczIBl": { to: "127266", why: "Pepperoni -> Peperoni rot" },
  "-MVWnxZaSV_ug47-EXJn": { to: "1115321", why: "Saftorange -> Saft-Orangen Navel" },
  "-MVWoBddOyuKsb-O44Zh": { to: "111500", why: "Bitterorange -> Orangen Navelina" },
  // Lettuces Terra sells by the crate and the stall by the head.
  "-MV1k-FpldA73g7Mt8Wp": { to: "1255051", why: "Kopfsalat, 12 St je Kiste", divide: 12 },
  "-MV1kMrFAj6FmRD57QFh": { to: "125556", why: "Eichblatt rot, 12 St je Kiste", divide: 12 },
  "-MVWgqwntVwdWG09YHHp": { to: "125521", why: "Endiviensalat, 8 St je Kiste", divide: 8 },
  "-MVXoy4mCSyrrWAo9Mrs": { to: "1254564", why: "Miniromana, 2 St je Tüte", divide: 2 },
  // Crates and jars the stall breaks open. `divide` is a plain factor, so a kilo
  // price out of a 500 g Becher divides by 0,5 as a head out of a crate divides by 12.
  "-MVphIj-mFl6qtUdEp2m": { to: "125512", why: "Bataviasalat grün, 12 St je Kiste", divide: 12 },
  "-MVpgzox509aXIhb9ZvT": { to: "125557", why: "Eichblatt grün, 12 St je Kiste", divide: 12 },
  "-MVWjPpCqcORXVWa6Wn4": { to: "127365", why: "Salatgurke, 12 St je Kiste", divide: 12 },
  "-MVaZ9IIZFFaHSxO6TBh": { to: "127371", why: "Salatgurke, 12 St je Kiste", divide: 12 },
  "-MVXH8caXETWEz-aGZhn": { to: "126588", why: "Sauerkraut je kg aus dem 500-g-Becher", divide: 0.5 },
  "-MVpgAOSisD95FmqBFph": { to: "126588", why: "Havelländer Sauerkraut, Beutel = Becher", divide: 1 },
  // Nothing in this week's list is this produce, not even loosely. The stall either
  // sells its own or does not sell it: Bärlauch is a spring plant, the only broccoli
  // is sprouts, and a Wirsing crate cannot be turned into a kilo price without
  // guessing what a head weighs.
  "-MWgvQsfbOJfk1KRkqYb": { skip: "Bärlauch: nicht in der Saison" },
  // 13080480 is cut onion strips, and no whole white onion is listed this week. Left
  // as "not in the list" rather than as a loss, which is what the price comparison
  // against a processed row made it look like.
  "-MVXJOeR6ojcgjc9z70G": { skip: "Zwiebeln weiß: diese Woche keine ganzen weißen Zwiebeln" },
  "-MVXn4S14VQ1NlILYJl6": { skip: "Brokkoli: nur Brokkolisprossen in der Liste" },
  "-MVWj2BjJEP6cp6pTwNl": { skip: "Granatapfel: diese Woche nicht in der Liste" },
  "-MVWlbrqyBjMul9aydKD": { skip: "Mangold: diese Woche nicht in der Liste" },
  "-MV1i8oX-IaqtUP3rDve": { skip: "Puntarelle: diese Woche nicht in der Liste" },
  "-MVXogKsUYzYTUeJ2pyz": { skip: "Wirsing: 126209 ist eine Kiste à 6 St, kg-Preis nicht ableitbar" },
};

/**
 * Articles whose name is another name for the row they point at.
 *
 * [confirms] compares words and cannot know that an Austernseitling and an
 * Austernpilz are the same mushroom. Each entry here is a promise that the number is
 * right even though the words disagree.
 */
const SYNONYM = {
  "-MVq0yUkHJzy-Uhe2IvL": "Austernseitling = Austernpilz",
};

/**
 * Words that name a kind of produce rather than a particular one.
 *
 * They are what makes a loose match possible — `Apfel` finds the apples — and what
 * makes a strict one meaningless, since every apple row carries them. So they score
 * for [bestMatch] and are ignored by [confirms].
 */
const KIND_WORD_LIST = [
  "obst", "gemüse", "frucht", "apfel", "äpfel", "kartoffel", "kartoffeln", "möhren",
  "karotte", "karotten", "zwiebel", "zwiebeln", "kohl", "salat", "bete", "beete",
  "rote", "weiß", "gelb", "grün", "rot", "lose", "frisch", "bio", "demeter",
  "größe", "stück", "bund", "beutel", "schale", "kraut", "kräuter", "pilze", "eier",
  // Colours, which Terra uses to describe produce and the stall sometimes to name it.
  // `Bitterorange` shares nothing but the word `orange` with `Kürbis Hokkaido orange`.
  "orange", "violett", "schwarz", "blau", "bunt", "bunte", "gross", "klein",
];

/**
 * What Terra does to produce before selling it, which a market stall does not buy.
 *
 * A stall's `Mango` is not `Mangos getrocknet 5 x 1 Kg`, and its `Kartoffel` is not
 * `vorgegarte Kartoffeln, geviertelt`. These words are a strong signal that a row
 * sharing every other word is still the wrong row.
 */
const PROCESSED = /getrocknet|gegart|vorgegart|geschält|gewürfelt|geraspelt|scheiben|mischung|püree|saft|tiefgekühlt|konserv/i;

/** German plural and diminutive endings, enough to let Sellerie meet Knollensellerie. */
function stem(word) {
  for (const suffix of ["innen", "chen", "lein", "en", "er", "es", "e", "n", "s"]) {
    if (word.length - suffix.length >= 5 && word.endsWith(suffix)) {
      return word.slice(0, -suffix.length);
    }
  }
  return word;
}

const stems = (text) =>
  new Set((text.toLowerCase().match(/[a-zäöüß]{4,}/g) ?? []).map(stem));

// Stemmed as well as spelled out: every comparison below runs on stems, and `gemüse`
// stems to `gemüs`, which would otherwise slip through the list and make `Gemüse` look
// like a word that distinguishes one article from another.
const KIND_WORDS = new Set([...KIND_WORD_LIST, ...KIND_WORD_LIST.map(stem)]);

/**
 * Whether a row's name carries the article's own distinguishing word.
 *
 * The guard against a match that looks authoritative and is not: the first
 * availability run matched on names and could not tell `Apfel Braeburn` from
 * `Apfel Gala`, so a third of the stored numbers point at a sibling of the right row
 * — `Brokkoli` at a sauerkraut jar, `Wintersonne` (a yellow beet) at red Rote Bete,
 * five apple varieties at Gala. Comparing the variety word catches all of those.
 *
 * Kind words cannot distinguish anything and are skipped. A compound counts — the
 * stall's `Sellerie` is Terra's `Knollensellerie` — but only from five characters, or
 * `Galant` would pass as `Gala`. An article with nothing but kind words has no
 * opinion to offer and confirms by default.
 */
function confirms(article, row) {
  const own = [...stems(`${article.productName ?? ""} ${(article.searchTerms ?? "").replace(/,/g, " ")}`)]
    .filter((w) => !KIND_WORDS.has(w));
  if (own.length === 0) return true;
  const name = row.name.toLowerCase();
  const rowStems = [...stems(name)];
  return own.some((w) =>
    name.includes(w) || rowStems.some((s) => s.length > w.length && s.endsWith(w))
  );
}

/**
 * The live row an article is most plausibly about, by name alone.
 *
 * Used where the stored number leads nowhere — a variety that is not in this week's
 * list, a number that was delisted, the farm's own produce — and the shop would
 * otherwise carry an article it cannot source. Loose on purpose: the seller would
 * rather sell a different carrot under a carrot's photograph than sell nothing, and
 * every choice it makes is printed for them to overrule.
 *
 * Scoring prefers shared words, then the same unit, and pushes away the rows a market
 * stall does not want: Terra's cut and cooked lines (`>` names), and crates priced per
 * Kiste where the stall sells by the kilo.
 */
function bestMatch(article, rows, taken) {
  const own = stems(`${article.productName ?? ""} ${(article.searchTerms ?? "").replace(/,/g, " ")}`);
  const want = UNIT_CODE[article.unit ?? ""] ?? "";
  let best = null;
  for (const row of rows) {
    if (row.flag !== "A" && row.flag !== "N") continue;
    const rowStems = [...stems(row.name)];
    let score = 0;
    for (const w of own) {
      // An exact word, or the stall's short name inside Terra's compound. Only that
      // direction: German builds compounds by prefixing, so a *longer* article word
      // ending in a row's word is a different plant -- Meerrettich is not Rettich,
      // Granatapfel is not Apfel, Mangold is not Mango. The reverse is the same
      // produce under a fuller name: Sellerie is Knollensellerie, Champignons are
      // Steinchampignons.
      const hit = rowStems.includes(w)
        ? w
        : rowStems.find((s) => s.length > w.length && s.endsWith(w));
      // Kindness is the *row's* word, not the article's: `Granatapfel` is not a kind
      // word, but all it shares with `Apfel Holsteiner Cox` is `apfel`, which is.
      if (hit) score += KIND_WORDS.has(hit) || KIND_WORDS.has(w) ? 2 : 6;
    }
    if (score === 0) continue;
    if (row.unit === want) score += 2;
    if (row.name.startsWith(">")) score -= 5;
    if (PROCESSED.test(row.name)) score -= 5;
    if (row.unit === "KI" && want !== "KI") score -= 3;
    // A row already given to another article is a worse fit than a free one, but
    // still better than nothing: two kinds of apple may share a price.
    if (taken.has(row.artnr)) score -= 4;
    if (!best || score > best.score) best = { row, score };
  }
  // Six: one word that names this produce and not merely its kind. A row that shares
  // only `Apfel`, `Salat` or `Kartoffel` with the article is its neighbour on the
  // shelf, not its source.
  return best && best.score >= 6 ? best : null;
}

/** What a stall unit is called in a BNN row. */
const UNIT_CODE = {
  "kg": "KG", "g": "G", "Stück": "ST", "Bund": "BD",
  "Beutel": "BT", "Schale": "SC", "Glas": "GL", "Kasten": "KI",
};

const args = process.argv.slice(2);
const apply = args.includes("--apply");
const repoint = args.includes("--repoint");
const allowBelowCost = args.includes("--allow-below-cost");
const loose = args.includes("--loose");
const publish = args.includes("--publish");
const flag = (n) => (args.includes(n) ? args[args.indexOf(n) + 1] : null);
const bnnPath = flag("--bnn");
const exportPath = flag("--from-export");
const project = flag("--project");
const reportPath = flag("--report");

if (!bnnPath || bnnPath.startsWith("--") || (!exportPath && !project)) {
  console.error(
    "Usage: node sync-bnn-costs.mjs --bnn <plf.bnn> --from-export <export.json> [--apply]\n" +
    "       node sync-bnn-costs.mjs --bnn <plf.bnn> --project <id> [--apply]\n" +
    "       [--report <file.csv>] [--repoint] [--loose] [--publish] [--allow-below-cost]"
  );
  process.exit(2);
}

// ---------------------------------------------------------------- the price list

// Terra ships these DOS-encoded (cp850), as the seller's own file import expects.
// Node's TextDecoder has no cp850, and the bytes that differ from latin-1 are few:
// the same table `sync-bnn-availability.mjs` carries, for the same reason.
const CP850 = {
  0x81: "ü", 0x82: "é", 0x84: "ä", 0x8E: "Ä", 0x94: "ö",
  0x99: "Ö", 0x9A: "Ü", 0xA4: "ñ", 0xE1: "ß",
};

/** Every row of the price list by article number, prices parsed, flag kept. */
function readBnn(path) {
  const bytes = readFileSync(path);
  let text = "";
  for (const b of bytes) text += b < 0x80 ? String.fromCharCode(b) : (CP850[b] ?? "?");
  const lines = text.split(/\r?\n/).filter((l) => l.trim().length > 0);
  const header = lines[0].split(";");
  const rows = new Map();
  for (const line of lines.slice(1)) {
    const f = line.split(";");
    if (f.length < 40) continue;
    const price = Number.parseFloat((f[P_PRICE] ?? "").replace(",", "."));
    if (!Number.isFinite(price) || price <= 0) continue;
    rows.set(f[P_ID], {
      artnr: f[P_ID],
      name: (f[P_NAME] ?? "").trim(),
      flag: f[P_FLAG],
      unit: f[P_UNIT],
      supplier: f[P_SUPPLIER],
      origin: f[P_ORIGIN],
      certification: f[P_CERT],
      quality: f[P_QUALITY],
      barcode: /^0*$/.test(f[P_BARCODE] ?? "") ? "" : f[P_BARCODE],
      packageSize: Number.parseFloat((f[P_PACKAGE] ?? "").replace(",", ".")) || 0,
      price,
      taxRate: f[P_TAX] === "2" ? 0.19 : 0.07,
    });
  }
  return { label: `${header[5]} (gültig ${header[7]}-${header[8]})`, rows };
}

// ------------------------------------------------------------------ the catalog

function readCatalog() {
  if (exportPath) return JSON.parse(readFileSync(exportPath, "utf8"));
  const get = (node) =>
    JSON.parse(
      execFileSync("firebase", ["database:get", node, "--project", project], {
        encoding: "utf8", maxBuffer: 64 * 1024 * 1024,
      })
    );
  return { articles: get("/articles"), seller_articles: get("/seller_articles") };
}

const { label, rows } = readBnn(bnnPath);
const db = readCatalog();
const articles = db.articles ?? {};
const sellerArticles = db.seller_articles ?? {};

console.log(`price list: ${label}`);
console.log(`            ${rows.size} priced rows, ` +
  `${[...rows.values()].filter((r) => r.flag === "A" || r.flag === "N").length} live\n`);

// ------------------------------------------------------------------- the plan

const round = (v, places) => Number(v.toFixed(places));
const plan = [];
/** Rows already handed to an article, so a free row wins over a shared one. */
const taken = new Set();

for (const [sellerId, catalog] of Object.entries(articles)) {
  for (const [articleId, article] of Object.entries(catalog ?? {})) {
    const name = article.productName ?? "";
    const storedPid = (article.productId ?? "").toString().trim();
    const entry = {
      sellerId, articleId, name,
      pid: storedPid, price: Number(article.price) || 0,
      unit: article.unit ?? "", fields: {}, skip: null, row: null,
    };
    plan.push(entry);

    // Which price-list row this article is, after the hand-checked moves. A per-article
    // correction outranks the per-number one: it was written for this row.
    const fix = FIX[articleId];
    // A hand decision outranks the search in both directions: `skip` means a human
    // looked and found nothing, which --loose must not overrule with a guess.
    if (fix?.skip) { entry.skip = fix.skip; continue; }
    const moved = fix?.to ? fix : REPOINT[storedPid];
    let row = rows.get(moved?.to ?? storedPid);
    let live = row && (row.flag === "A" || row.flag === "N");

    // The stored number is only trusted where the row is live *and* carries the
    // article's own word. Both tests have to fail before the name search runs, or a
    // good number would be thrown away for a guess.
    // A number assigned by hand needs no confirming: it was read against the list by
    // a person, who knew the words would not agree -- that is why it is in the table.
    const byHand = Boolean(fix?.to) || Boolean(SYNONYM[articleId]);
    if (live && !byHand && !confirms(article, row)) { live = false; entry.unconfirmed = row; }

    if (!live) {
      if (!loose) {
        entry.skip = !row
          ? (storedPid ? "kein Eintrag in dieser Liste" : "kein productId")
          : entry.unconfirmed
            ? `${row.name} ist eine andere Sorte`
            : `ausgelistet (${row.flag})`;
        continue;
      }
      // --loose: the shop would rather carry a sibling of the right row, under the
      // photograph it already has, than carry an article it cannot source.
      const found = bestMatch(article, [...rows.values()], taken);
      if (!found) { entry.skip = "nichts Ähnliches in dieser Liste"; continue; }
      entry.overrode = { from: storedPid, was: row?.name ?? null, score: found.score };
      row = found.row;
    }
    taken.add(row.artnr);
    entry.row = row;
    entry.moved = moved?.to === row.artnr ? moved : null;

    if (entry.price <= 0) { entry.skip = "kein Verkaufspreis"; continue; }

    // Terra's unit against the stall's, and the one conversion each may carry.
    let net = row.price;
    const want = UNIT_CODE[entry.unit] ?? entry.unit.toUpperCase();
    // A stated divisor applies whether or not the unit names agree: Terra prices
    // `Mini Romanasalat 2 St/ Tüte` per ST, meaning per bag, and the stall sells the
    // heads singly. The unit codes match and the quantities do not.
    if (moved?.divide && moved.divide !== 1) {
      net = net / moved.divide;
    } else if (row.unit !== want) {
      const perPiece = Number(article.weightPerPiece) || 0;
      if (moved?.divide) {
        net = net / moved.divide;
      } else if (row.unit === "KG" && perPiece > 0) {
        net = net * perPiece;
      } else {
        entry.skip = `Einheit ${entry.unit} gegen Terra ${row.unit}, keine Umrechnung`;
        continue;
      }
    }
    net = round(net, 2);

    const factor = entry.price / (net * (1 + row.taxRate));
    if (factor < 1 && !allowBelowCost) {
      entry.skip = `VK ${entry.price.toFixed(2)} unter Einkauf ${net.toFixed(2)}`;
      continue;
    }

    const current = sellerArticles[sellerId]?.[articleId] ?? {};
    entry.acquirePrice = net;
    entry.factor = round(factor, 4);
    if (Number(current.acquirePrice) !== net) entry.fields.acquirePrice = net;
    if (Number(current.markupFactor) !== entry.factor) entry.fields.markupFactor = entry.factor;

    // The rest of what the row knows. Seller-only sourcing fields, and the price list
    // is the authority on them: what is stored was typed by hand and contradicts the
    // row it is sourced from on 47 of 60 articles -- Lauch tells buyers "Eu-Bio aus
    // Italien" while the row it comes from is regional Bioland. `detailInfo` is left
    // alone, being the seller's own words about the produce rather than data.
    for (const [field, value] of Object.entries({
      supplier: row.supplier, origin: row.origin,
      certification: row.certification, quality: row.quality,
      barcode: row.barcode, packageSize: row.packageSize,
    })) {
      if ((current[field] ?? (typeof value === "number" ? 0 : "")) !== value) {
        entry.fields[field] = value;
      }
    }
    // Only where the row contradicts what the article stores: the catalog's own
    // fallback is already the reduced rate.
    const storedTax = Number(article.taxRate);
    if (!Number.isFinite(storedTax) ? row.taxRate !== 0.07 : storedTax !== row.taxRate) {
      entry.fields.taxRate = row.taxRate;
    }
    // The number this article is now sourced from, so the next run needs no table
    // and no search: a loose match made once becomes an exact match next week.
    if ((repoint || loose) && storedPid !== row.artnr) entry.fields.productId = row.artnr;
  }
}

// ------------------------------------------------------------------- publish

/**
 * Put exactly the articles that got a cost in front of buyers, and take the rest out.
 *
 * The shop then says what the bookkeeping can stand behind: a row with a price, a
 * purchase price and a margin derived from both. Everything that fell out above goes
 * dark — produce this week's list does not carry, sizes that do not match, and the
 * handful priced *below* what Terra now charges, which is the one group worth looking
 * at rather than hiding.
 *
 * **Both halves, always.** `available` is derived, not stored
 * (`available = sellerAvailable && stockPermits`), and `StockViewModel.correctAvailability()`
 * on the seller's device is its only real author. Writing the public flag alone would
 * last until that pass next ran: 30 of the priced articles hold `sellerAvailable:
 * false`, and intent wins. So the intent is written with it.
 *
 * **No stock to seed.** `stockPermits` is `!stockGated || ...`, no article in this
 * catalogue is gated, and there is no `/stock` node — so nothing waits on a ledger and
 * nothing has to be stocked up for an article to be sellable.
 *
 * This is the one place where this script and `sync-bnn-availability.mjs` disagree:
 * that one publishes what the price list flags as orderable, this one publishes what
 * it could also price. Run this last, or do not pass --publish.
 */
if (publish) {
  for (const entry of plan) {
    const sellable = !entry.skip;
    const article = articles[entry.sellerId]?.[entry.articleId] ?? {};
    const current = sellerArticles[entry.sellerId]?.[entry.articleId] ?? {};
    if (Boolean(article.available) !== sellable) entry.fields.available = sellable;
    if (current.sellerAvailable !== sellable) entry.fields.sellerAvailable = sellable;
  }
}

// --------------------------------------------------------------------- report

const costed = plan.filter((p) => !p.skip);
const skipped = plan.filter((p) => p.skip);
const changing = plan.filter((p) => Object.keys(p.fields).length > 0);

console.log(`articles                        ${plan.length}`);
console.log(`  cost found                    ${costed.length}`);
console.log(`  skipped                       ${skipped.length}`);
const byReason = {};
for (const p of skipped) {
  const key = p.skip.replace(/\(.*\)|\d+[,.]\d+/g, "").trim();
  byReason[key] = (byReason[key] ?? 0) + 1;
}
for (const [reason, n] of Object.entries(byReason).sort((a, b) => b[1] - a[1])) {
  console.log(`      ${reason.padEnd(26)}${n}`);
}
console.log(`\nfield writes                    ${changing.reduce((n, p) => n + Object.keys(p.fields).length, 0)}`);
console.log(`  acquirePrice                  ${changing.filter((p) => "acquirePrice" in p.fields).length}`);
console.log(`  markupFactor                  ${changing.filter((p) => "markupFactor" in p.fields).length}`);
console.log(`  taxRate                       ${changing.filter((p) => "taxRate" in p.fields).length}`);
console.log(`  productId (umgehängt)         ${changing.filter((p) => "productId" in p.fields).length}`);
console.log(`  Herkunft/Zertifikat/Gebinde   ${changing.filter((p) => "origin" in p.fields || "certification" in p.fields || "packageSize" in p.fields).length}`);
if (publish) {
  const on = plan.filter((p) => p.fields.available === true);
  const off = plan.filter((p) => p.fields.available === false);
  console.log(`\nim Laden                        ${plan.filter((p) => !p.skip).length} von ${plan.length}`);
  console.log(`  schalten sich ein             ${on.length}`);
  console.log(`  schalten sich aus             ${off.length}${off.length ? ": " + off.map((p) => p.name).join(", ") : ""}`);
  console.log(`  Absicht nachgezogen           ${plan.filter((p) => "sellerAvailable" in p.fields).length} (sellerAvailable)`);
}

const overrides = plan.filter((p) => p.overrode);
if (overrides.length > 0) {
  console.log(`\nübersteuert (Artikel behält Bild und Preis, neue Quelle):`);
  for (const p of overrides.sort((a, b) => a.name.localeCompare(b.name, "de"))) {
    const was = p.overrode.was ? `${p.overrode.from} ${p.overrode.was}` : `${p.overrode.from || "ohne"}`;
    console.log(`  ${p.name.slice(0, 22).padEnd(23)} ${was.slice(0, 34).padEnd(35)} -> ${p.row.artnr} ${p.row.name.slice(0, 30)}`);
  }
}

if (costed.length > 0) {
  const factors = costed.map((p) => p.factor).sort((a, b) => a - b);
  const q = (f) => factors[Math.floor(factors.length * f)].toFixed(2);
  console.log(`\nmargin as charged today         ` +
    `Faktor ${q(0.25)} / ${q(0.5)} / ${q(0.75)} (25/50/75 %)`);
}

if (reportPath) {
  const csv = [
    "seller;articleId;name;unit;VK;productId;terra;terraName;flag;netto;terraUnit;MwSt;Faktor;Marge%;skip;übersteuert",
  ];
  for (const p of plan.sort((a, b) => a.name.localeCompare(b.name, "de"))) {
    const margin = p.factor
      ? (((p.price / (1 + p.row.taxRate) - p.acquirePrice) / (p.price / (1 + p.row.taxRate))) * 100).toFixed(1)
      : "";
    csv.push([
      p.sellerId, p.articleId, p.name, p.unit, p.price.toFixed(2), p.pid,
      p.row?.artnr ?? "", p.row?.name ?? "", p.row?.flag ?? "",
      p.acquirePrice?.toFixed(2) ?? "", p.row?.unit ?? "",
      p.row ? (p.row.taxRate * 100).toFixed(0) : "",
      p.factor?.toFixed(4) ?? "", margin, p.skip ?? "",
      p.overrode ? `war ${p.overrode.from} ${p.overrode.was ?? ""}`.trim() : "",
    ].join(";"));
  }
  writeFileSync(reportPath, csv.join("\n") + "\n");
  console.log(`\nreport -> ${reportPath}`);
}

// ---------------------------------------------------------------------- write

const updates = {};
for (const p of changing) {
  for (const [field, value] of Object.entries(p.fields)) {
    const root = ["productId", "taxRate", "available"].includes(field)
      ? "articles"
      : "seller_articles";
    (updates[p.sellerId] ??= {})[`${root}/${p.sellerId}/${p.articleId}/${field}`] = value;
  }
}

if (!apply) {
  console.log("\ndry run - nothing written. Pass --apply to write.");
  for (const [sellerId, update] of Object.entries(updates)) {
    console.log(`  ${sellerId}: ${Object.keys(update).length} paths`);
  }
  process.exit(0);
}
if (!project) {
  console.error("\n--apply needs --project (an export cannot be written back).");
  process.exit(2);
}

for (const [sellerId, update] of Object.entries(updates)) {
  const file = `/tmp/bnn-costs-${sellerId}.json`;
  writeFileSync(file, JSON.stringify(update, null, 2));
  // The payload goes as the positional infile, the way `sync-bnn-availability.mjs`
  // sends its own: `--data` takes escaped JSON on the command line, not a file, and
  // would have written the literal string "@/tmp/...". `--force` skips the prompt.
  execFileSync(
    "firebase",
    ["database:update", "/", file, "--project", project, "--force"],
    { stdio: "inherit" }
  );
  console.log(`wrote ${Object.keys(update).length} paths for ${sellerId}`);
}
