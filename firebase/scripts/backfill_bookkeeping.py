#!/usr/bin/env python3
# Builds the seller_articles (bookkeeping) half for an exported production catalog.
#
#   python3 backfill_bookkeeping.py <export.json> <plf.bnn> <out.json> <review.csv>
#
# The export carries articles but no seller_articles: no purchase price, supplier,
# origin or certification code — the seller-only half the CSV tax export needs.
# Two sources fill it, and neither is the article number: the production ids are the
# seller's own shelf numbers ("17", "233", "F/") and none of them is a Terra article
# number, so the Terra cross-check runs on product names.
#
#   detailInfo text  -> certification + origin. In practice it reads "certification
#                       + country" ("EG-Bio Italien", "Deutschland Demeter") and,
#                       unlike a price, does not go stale.
#   Terra BNN row    -> supplier, quality, packageSize, and the purchase price.
#
# Health warning on the price: the production articles were created in March 2021
# (every push id decodes into that month) while the price list is KW 31/26. Pairing
# the two gives markups that mostly read like a real margin, but a handful land under
# the 1.15 floor — a record of two mismatched dates, not of selling at a loss. Set
# WRITE_BNN_PRICE to False to keep acquirePrice unknown instead.
#
# See doc/bookkeeping-backfill.md for the full reasoning.
import json, re, sys, csv, unicodedata, collections

# The 2026 purchase price goes into acquirePrice on exact matches, markup derived.
WRITE_BNN_PRICE = True

# BNN v3 field positions, as in BnnParser.kt
P_ID, P_BARCODE, P_NAME, P_DETAIL = 0, 4, 6, 7
P_QUALITY, P_SUPPLIER, P_ORIGIN, P_CERT = 9, 10, 12, 13
P_PACKSIZE, P_UNIT, P_TAX, P_PRICE = 22, 23, 33, 37

REGIONAL = "REG"   # BnnCodeTables.ORIGIN_REGIONAL

# Mirrors the association codes in BnnCodeTables.kt
CERT_TEXT = [("verbund oekohoefe","DV"), ("oekohoefe","DV"), ("bioland","DB"),
             ("demeter","DD"), ("eg bio","EG"), ("eu bio","EG"),
             ("biologisch dynamisch","DD")]
COUNTRY_TEXT = [("italien","IT"), ("spanien","ES"), ("frankreich","FR"),
                ("griechenland","GR"), ("niederlande","NL"), ("marokko","MA"),
                ("peru","PE"), ("costa rica","CR"), ("oesterreich","AT"),
                ("deutschland","DE")]
# Named as a place but meaning "our own region", so REG rather than DE.
REGIONAL_TEXT = ["brandenburg", "waldpferdehof", "waldpferde hof",
                 "aus der region", "jansforst", "dahmsdorf"]


def norm(s):
    s = s.lower().replace("ä","ae").replace("ö","oe").replace("ü","ue").replace("ß","ss")
    s = unicodedata.normalize("NFKD", s)
    s = "".join(c for c in s if not unicodedata.combining(c))
    return re.sub(r"[^a-z0-9]+", " ", s).strip()


def from_detail(text):
    t = norm(text)
    if not t:
        return "", ""
    cert = next((c for k, c in CERT_TEXT if k in t), "")
    if any(k in t for k in REGIONAL_TEXT):
        origin = REGIONAL
    else:
        origin = next((c for k, c in COUNTRY_TEXT if k in t), "")
    return cert, origin


def load_bnn(path):
    rows = []
    # BNN files from Terra are DOS-encoded, as in the seller's file import
    for line in open(path, encoding="cp850").read().splitlines()[1:]:
        f = line.rstrip("\r").split(";")
        if len(f) < 70 or not f[P_ID].strip():
            continue
        def num(x):
            try: return float(x.replace(",", "."))
            except ValueError: return 0.0
        rows.append(dict(artnr=f[P_ID].strip(), name=f[P_NAME].strip(),
                         quality=f[P_QUALITY].strip(), supplier=f[P_SUPPLIER].strip(),
                         origin=f[P_ORIGIN].strip(), cert=f[P_CERT].strip(),
                         unit=f[P_UNIT].strip().upper(), packsize=num(f[P_PACKSIZE]),
                         price=num(f[P_PRICE]), barcode=f[P_BARCODE].strip(),
                         tax=0.07 if f[P_TAX].strip() == "1" else 0.19,
                         nname=norm(f[P_NAME])))
    return rows


# Curated produce term -> (BNN name patterns, the category it must be in).
# Curated only: an article whose head word is absent simply gets no match. A generic
# fallback is what made a first pass match Robuschka (a beetroot) to "Apfel Roter
# Jonagold" on the word "rote".
G, O, S, K, P, KA, KO, E = ("Gemüse","Obst","Salat","Kräuter","Pilze",
                            "Kartoffeln","Konserven","Eier")
TERMS = {
 "aubergine":([r"aubergine"],G), "blumenkohl":([r"blumenkohl"],G),
 "brokkoli":([r"broccoli",r"brokkoli"],G), "chinakohl":([r"chinakohl"],G),
 "fenchel":([r"fenchel"],G), "ingwer":([r"ingwer"],G),
 "knoblauch":([r"knoblauch"],G), "kohlrabi":([r"kohlrabi"],G),
 "kurkuma":([r"kurkuma"],G), "lauch":([r"lauch",r"porree"],G),
 "lauchzwiebeln":([r"lauchzwiebeln",r"fruehlingszwiebeln"],G),
 "mangold":([r"mangold"],G), "meerrettich":([r"meerrettich"],G),
 "moehren":([r"moehren"],G), "pastinaken":([r"pastinaken"],G),
 "rettich":([r"rettich"],G), "rosenkohl":([r"rosenkohl"],G),
 "rotkohl":([r"rotkohl"],G), "salatgurke":([r"salatgurke"],G),
 "schalotten":([r"schalotten"],G), "schwarzwurzel":([r"schwarzwurzel"],G),
 "sellerie":([r"knollensellerie",r"sellerieknolle"],G),
 "spinat":([r"spinat"],G), "spitzkohl":([r"spitzkohl"],G),
 "stangensellerie":([r"stangensellerie",r"staudensellerie"],G),
 "steckruebe":([r"steckruebe"],G), "topinambur":([r"topinambur"],G),
 "wirsing":([r"wirsing"],G), "wurzelpetersilie":([r"wurzelpetersilie"],G),
 "zucchini":([r"zucchini"],G), "zwiebeln":([r"zwiebeln"],G),
 "kuerbis":([r"kuerbis"],G), "hokkaido":([r"hokkaido"],G),
 "paprika":([r"paprika"],G), "spitzpaprika":([r"spitzpaprika"],G),
 "pepperoni":([r"pepperoni"],G),
 "kartoffel":([r"kartoffel"],KA), "suesskartoffel":([r"suesskartoffel"],KA),
 "apfel":([r"apfel",r"aepfel"],O), "birne":([r"birnen"],O),
 "orange":([r"orangen"],O), "saftorange":([r"saftorangen"],O),
 "bitterorange":([r"bitterorange",r"pomeranze"],O),
 "zitrone":([r"zitronen"],O), "clementinen":([r"clementinen"],O),
 "granatapfel":([r"granatapfel"],O), "kaki":([r"kaki",r"sharon"],O),
 "kiwi":([r"kiwi"],O), "mango":([r"mango"],O),
 "kopfsalat":([r"kopfsalat"],S), "bataviasalat":([r"batavia"],S),
 "eichblatt":([r"eichblatt"],S), "endiviensalat":([r"endivien"],S),
 "feldsalat":([r"feldsalat"],S), "chicoree":([r"chicoree"],S),
 "radicchio":([r"radicchio"],S), "puntarelle":([r"puntarelle",r"catalogna"],S),
 "miniromana":([r"romana"],S), "kresse":([r"kresse"],S),
 "baerlauch":([r"baerlauch"],K), "koriander":([r"koriander"],K),
 "petersilie":([r"petersilie"],K), "rosmarin":([r"rosmarin"],K),
 "salbei":([r"salbei"],K), "thymian":([r"thymian"],K),
 "sprossen":([r"sprossen"],K),
 "austernpilze":([r"austernpilz",r"austernseitling"],P),
 "austernseitlinge":([r"austernseitling",r"austernpilz"],P),
 "champignons":([r"champignon"],P), "steinchampignons":([r"champignon"],P),
 "kraeuterseitlinge":([r"kraeuterseitling"],P), "portobello":([r"portobello"],P),
 "shiitake":([r"shiitake"],P),
 "sauerkraut":([r"sauerkraut"],KO), "eier":([r"eier"],E),
}

# Words in an article name that never identify a variety.
ARTICLE_NOISE = {"gelb","rot","rote","weiss","gruen","gross","grosse","lose",
                 "kohl","kartoffel","groesse","schaelchen"}
# Colour and pack noise in a BNN name: the default variety, not a different good.
BNN_NOISE = re.compile(r"^(?:\d+|st|kg|g|bd|kal|lose|ca|x|aktion|und|im|aus|"
                       r"gelb|gelbe|rot|rote|roter|weiss|weisse|gruen|gruene|"
                       r"bunt|bunte|orange|samenfest|sortierung|mittlere|"
                       r"premium|gelegt|stueck|schale|schaelchen|salat|pilze|"
                       r"apfel|aepfel)$")

# An article's unit must be reachable from the BNN unit. Hard filter, not a bonus.
UNIT_OK = {"kg":{"KG"}, "stück":{"ST","KI","PA","KT","TO"}, "bund":{"BD"},
           "beutel":{"BT","NE"}, "schale":{"SC","SCH"}}
PIECES_RE = re.compile(r"(\d+)(?:\s*-\s*(\d+))?\s*St\b")


def pieces_in(name):
    # A Kiste is Terra's trade unit only: sold per piece, at the per-piece price.
    m = PIECES_RE.search(name)
    if not m: return None
    lo = float(m.group(1)); hi = float(m.group(2)) if m.group(2) else lo
    return (lo + hi) / 2.0


def bnn_qualifiers(nname, pats):
    # Meaningful words left after the matched produce and the noise: "Knoblauch,
    # getrocknet" keeps 'getrocknet' and is not the same good as plain Knoblauch,
    # while "Zwiebeln, gelb" keeps nothing and stays an exact match.
    rest = nname
    for p in pats:
        rest = re.sub(rf"\b{p}\w*", " ", rest)
    return [w for w in rest.split() if len(w) > 1 and not BNN_NOISE.match(w)]


def head_terms(art):
    # From the product name; a term inside a longer word is dropped, so Meerrettich
    # never offers 'rettich' and Süßkartoffel never offers 'kartoffel'. searchTerms
    # are consulted only when the name yields nothing.
    name = norm(art["productName"])
    found = [t for t in TERMS if re.search(rf"\b{t}", name)]
    if not found:
        found = [norm(r) for r in art["searchTerms"].split(",") if norm(r) in TERMS]
    return sorted(set(found), key=len, reverse=True)


def match(art, bnn):
    """(row, 'EXACT'|'SPECIES') or None."""
    units = UNIT_OK.get(art["unit"].lower(), set())
    aname = norm(art["productName"])
    best = None
    for term in head_terms(art):
        pats, term_cat = TERMS[term]
        if term_cat != art["category"]:
            continue                         # never cross Obst/Gemüse/Salat
        for row in bnn:
            if row["unit"] not in units:
                continue
            if not any(re.search(rf"\b{p}", row["nname"]) for p in pats):
                continue
            # EXACT when a variety word of the article name is in the BNN name
            # ("Topaz" in "Apfel Topaz"), or both sides are the plain produce.
            extra = [w for w in aname.split()
                     if len(w) > 3 and w not in TERMS and w not in ARTICLE_NOISE]
            if extra:
                exact = any(re.search(rf"\b{w}", row["nname"]) for w in extra)
            elif re.fullmatch(rf"{term}s?", aname.replace(" ", "")):
                exact = not bnn_qualifiers(row["nname"], pats)
            else:
                exact = False
            rank = (0 if exact else 1, len(row["nname"]))
            if best is None or rank < best[2]:
                best = (row, "EXACT" if exact else "SPECIES", rank)
    return best[:2] if best else None


# Which produce two articles share, for inheriting a blank field from a sibling.
# Looser than TERMS on purpose: the farm's own varieties (Milan, Oxehella) name no
# produce at all and are only recognisable through their searchTerms.
PRODUCE_KEY = {
 "karotte":"moehre", "moehre":"moehre", "moehren":"moehre",
 "rote beete":"rotebeete", "beete":"rotebeete", "rote":"rotebeete",
 "apfel":"apfel", "aepfel":"apfel", "birne":"birne",
 "orange":"orange", "saftorange":"orange", "bitterorange":"orange",
 "zwiebeln":"zwiebeln", "kuechenzwiebeln":"zwiebeln",
 "kartoffel":"kartoffel", "kartoffeln":"kartoffel",
 "champignon":"champignon", "champignons":"champignon",
 "pepperoni":"pepperoni", "paprika":"paprika",
 "sauerkraut":"sauerkraut", "kraut":"sauerkraut",
 "eier":"eier", "ei":"eier", "salat":"salat",
 "eichblatt":"eichblatt", "eichblattsalat":"eichblatt",
 "kresse":"kresse", "petersilie":"petersilie",
 "seitlinge":"austernpilz", "austernpilze":"austernpilz",
 "austernseitlinge":"austernpilz", "rettich":"rettich",
 "gurke":"gurke", "salatgurke":"gurke", "kuerbis":"kuerbis",
 "sellerie":"sellerie", "rueben":"rueben",
 "zitrusfrucht":"zitrone", "zitrone":"zitrone",
}


def produce_key(art):
    for raw in art["searchTerms"].split(","):
        t = norm(raw)
        if t in PRODUCE_KEY:
            return PRODUCE_KEY[t]
    return norm(art["productName"])


def sibling_fill(rep, out):
    # Inherit a blank code from another article of the same produce, but only when
    # every sibling agrees: a disagreement leaves it blank rather than picking one.
    groups = collections.defaultdict(list)
    for r in rep:
        groups[r["key"]].append(r)
    by_id = {r["id"]: r for r in rep}
    for members in groups.values():
        if len(members) < 2:
            continue
        for field, col in (("certification","cert"), ("origin","origin")):
            donors = {out[m["id"]][field] for m in members if out[m["id"]][field]}
            if len(donors) != 1:
                continue
            value = donors.pop()
            for m in members:
                if not out[m["id"]][field]:
                    out[m["id"]][field] = value
                    by_id[m["id"]][col] = value
                    by_id[m["id"]][col + "_src"] = "sibling"


def main():
    src, bnn_path, dst, report = sys.argv[1], sys.argv[2], sys.argv[3], sys.argv[4]
    db = json.load(open(src, encoding="utf-8"))
    if "seller_articles" in db:
        sys.exit("refusing: export already has a seller_articles node")
    seller = next(iter(db["articles"]))
    arts = db["articles"][seller]
    bnn = load_bnn(bnn_path)

    out, rep = {}, []
    for aid, art in sorted(arts.items(), key=lambda x: (x[1]["category"], x[1]["productName"])):
        cert, origin = from_detail(art["detailInfo"])
        m = match(art, bnn)
        row, conf = (m[0], m[1]) if m else (None, "NONE")
        data = dict(acquirePrice=0.0, markupFactor=1.0, supplier="", origin=origin,
                    certification=cert, quality="", barcode="", packageSize=0.0,
                    reorderLevel=0.0)
        price_ref = markup_ref = 0.0
        if row:
            per = pieces_in(row["name"]) or 1.0 if row["unit"] == "KI" else 1.0
            price_ref = round(row["price"] / per, 2)
            if price_ref > 0:
                markup_ref = round(art["price"] / (price_ref * (1 + row["tax"])), 3)
            if conf == "EXACT":
                data["supplier"] = row["supplier"]
                data["quality"] = row["quality"]
                data["packageSize"] = round(row["packsize"] / per, 3)
                data["barcode"] = row["barcode"]
                # BNN codes only where the article's own description gave nothing
                data["origin"] = data["origin"] or row["origin"]
                data["certification"] = data["certification"] or row["cert"]
                if WRITE_BNN_PRICE and price_ref > 0:
                    data["acquirePrice"] = price_ref
                    data["markupFactor"] = markup_ref
        out[aid] = data
        rep.append(dict(id=aid, key=produce_key(art), cat=art["category"],
                        name=art["productName"], unit=art["unit"], price=art["price"],
                        conf=conf, artnr=row["artnr"] if row else "",
                        bnn=row["name"] if row else "", price_ref=price_ref,
                        markup_ref=markup_ref, cert=data["certification"],
                        cert_src="detail" if cert else ("bnn" if data["certification"] else ""),
                        origin=data["origin"],
                        origin_src="detail" if origin else ("bnn" if data["origin"] else ""),
                        supplier=data["supplier"], quality=data["quality"],
                        packsize=data["packageSize"], detail=art["detailInfo"].strip()))

    sibling_fill(rep, out)

    # Every private half needs its public article, or the database rules reject it.
    orphans = set(out) - set(arts)
    if orphans:
        sys.exit(f"refusing: {len(orphans)} seller_articles without a public article")

    db["seller_articles"] = {seller: out}
    json.dump(db, open(dst, "w", encoding="utf-8"), indent=1, ensure_ascii=False,
              sort_keys=True)

    cols = ["cat","name","unit","price","conf","artnr","bnn","price_ref","markup_ref",
            "cert","cert_src","origin","origin_src","supplier","quality","packsize",
            "key","detail","id"]
    with open(report, "w", newline="", encoding="utf-8-sig") as fh:
        w = csv.DictWriter(fh, fieldnames=cols, delimiter=";")
        w.writeheader()
        w.writerows({c: r[c] for c in cols} for r in rep)

    conf = collections.Counter(r["conf"] for r in rep)
    print(f"{len(rep)} articles -> {dst}")
    print(f"  match      {dict(conf)}")
    print(f"  acquirePrice {sum(1 for v in out.values() if v['acquirePrice'])}"
          f"  certification {sum(1 for v in out.values() if v['certification'])}"
          f"  origin {sum(1 for v in out.values() if v['origin'])}")
    thin = [r for r in rep if 0 < r["markup_ref"] < 1.15 and out[r["id"]]["acquirePrice"]]
    for r in thin:
        print(f"  thin margin: {r['name']} mk={r['markup_ref']} (2021 price vs 2026 cost)")
    print(f"  review     {report}")


if __name__ == "__main__":
    main()
