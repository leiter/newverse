#!/usr/bin/env python3
"""Generate the three RTDB test fixtures in this folder.

Every field name here was taken from the code that reads it — the repositories in
`shared/src/commonMain/.../data/repository/`, `ArticleNodes`, `StockNodes`,
`SaleNodes` and `firebase/database.rules.json`. Keep it that way: if a field is
renamed in the app, change it here and regenerate rather than hand-editing JSON.

Dates are relative to --today (default: the system date), so a fixture regenerated
tomorrow still has an order that is editable and one that is locked. Order status is
*derived* from the pickup date and the edit deadline, never asserted, so the data can
never claim a lock that the app would not compute.

Usage:
    python3 firebase/fixtures/generate_fixtures.py [--today 2026-10-22]
"""

import argparse
import datetime as dt
import json
import pathlib
import zoneinfo

TZ = zoneinfo.ZoneInfo("Europe/Berlin")

SELLER = "cPkcZSiF3LMXjWoqW6AqpA9paoO2"
B1 = "bcQHALrRkuSljXKdO6sxhHlnhNv2"  # Anna Berger      — approved, long history
B2 = "mX4gbxB5wuNUmfj9U4PWhO7BOJD3"  # unnamed buyer     — not approved yet (demo)
B3 = "jOSoP5UxAohZDtdpluhHg6VrfvQ2"  # Tobias Krenz      — approved, one market only

# Market ids. Stable across fixtures so an order's marketId resolves in each.
MKT_THU = "mkt-thu-onkel-tom"
MKT_SAT = "mkt-sat-kranoldplatz"
MKT_MON = "mkt-mon-breitenbachplatz"
MKT_POP = "mkt-popup-hofladen"  # day = tomorrow, so a locked order is always real

REDUCED, STANDARD = 0.07, 0.19

# Real Storage URLs lifted from firebase/fire-one-58ddc-articles-export.json, so the
# images actually load in the app instead of every tile falling back to a placeholder.
IMG = {
    "kartoffel": "https://firebasestorage.googleapis.com/v0/b/fire-one-58ddc.appspot.com/o/images%2Ftmp1576373532957500855.tmp?alt=media&token=81892fcd-c346-479a-8b56-4b56d7ce8381",
    "apfel": "https://firebasestorage.googleapis.com/v0/b/fire-one-58ddc.appspot.com/o/images%2F1609270827791_tmp1052065387795357072.tmp?alt=media&token=3ae17db0-a140-4a3d-bd6d-2843cfd5b36e",
    "banane": "https://firebasestorage.googleapis.com/v0/b/fire-one-58ddc.appspot.com/o/images%2Ftmp6852941846258768194.tmp?alt=media&token=f4b2a6a2-a8fa-495b-a093-04c269e97abe",
    "knoblauch": "https://firebasestorage.googleapis.com/v0/b/fire-one-58ddc.appspot.com/o/images%2F1609405653076_tmp824873246873729560.tmp?alt=media&token=3cd22727-cec8-4d86-ba92-140b24c6675b",
    "erdbeere": "https://firebasestorage.googleapis.com/v0/b/fire-one-58ddc.appspot.com/o/images%2Ftmp1145260240680560593.tmp?alt=media&token=0670c0da-e260-4d41-b5d8-1a119ea24a64",
    "paprika": "https://firebasestorage.googleapis.com/v0/b/fire-one-58ddc.appspot.com/o/images%2Ftmp7534516650759375907.tmp?alt=media&token=d474b967-46e9-45b2-8931-336f9c780ee3",
    "sellerie": "https://firebasestorage.googleapis.com/v0/b/fire-one-58ddc.appspot.com/o/images%2Ftmp274159401886863829.tmp?alt=media&token=e725f46f-5ab3-440c-9586-c04c6e1b7392",
}

GERMAN_DAYS = ["Montag", "Dienstag", "Mittwoch", "Donnerstag", "Freitag", "Samstag", "Sonntag"]


# --- time helpers ----------------------------------------------------------------
# pickUpDate is local midnight of the pickup day (OrderDateUtils.calculateNextPickupDate),
# and the edit deadline is two days before it at 23:59 (DefaultOrderScheduleConfig).

def millis(date, hour=0, minute=0):
    return int(dt.datetime(date.year, date.month, date.day, hour, minute, tzinfo=TZ).timestamp() * 1000)


def date_key(date):
    return date.strftime("%Y%m%d")


def month_key(date):
    return date.strftime("%Y%m")


def weekday_of(day_index):
    """Market dayIndex counts Monday as 0; isoweekday() counts it as 1."""
    return day_index + 1


def next_on(today, day_index, weeks_ahead=0):
    """The next occurrence of a market's weekday strictly after `today`."""
    delta = (weekday_of(day_index) - today.isoweekday()) % 7 or 7
    return today + dt.timedelta(days=delta + 7 * weeks_ahead)


def last_on(today, day_index, weeks_back=0):
    """The most recent occurrence of a market's weekday strictly before `today`."""
    delta = (today.isoweekday() - weekday_of(day_index)) % 7 or 7
    return today - dt.timedelta(days=delta + 7 * weeks_back)


def deadline_millis(pickup_date):
    return millis(pickup_date - dt.timedelta(days=2), 23, 59)


def derived_status(pickup_date, now_ms):
    """What the app will compute for this pickup date — see Order.transitionStatusIfNeeded."""
    if now_ms > millis(pickup_date):
        return "COMPLETED"
    if now_ms > deadline_millis(pickup_date):
        return "LOCKED"
    return "PLACED"


# --- node builders ---------------------------------------------------------------

def article(product_name, unit, price, category, search_terms, detail,
            image, tax=REDUCED, weight_per_piece=0.0, available=True, scarcity="UNKNOWN"):
    """The public half: /articles/{sellerId}/{articleId}. Fields per ArticleNodes.publicFields."""
    return {
        "productId": "0",
        "productName": product_name,
        "available": available,
        "unit": unit,
        "price": price,
        "weightPerPiece": weight_per_piece,
        "imageUrl": image,
        "category": category,
        "searchTerms": search_terms,
        "detailInfo": detail,
        "taxRate": tax,
        "scarcity": scarcity,
    }


def seller_article(acquire, markup, supplier, origin, cert="DD", quality="HK I",
                   barcode="", package_size=0.0, reorder_level=0.0,
                   seller_available=True, stock_gated=False):
    """The private half: /seller_articles/{sellerId}/{articleId}. ArticleNodes.privateFields."""
    data = {
        "acquirePrice": acquire,
        "markupFactor": markup,
        "supplier": supplier,
        "origin": origin,
        "certification": cert,
        "quality": quality,
        "barcode": barcode,
        "packageSize": package_size,
        "reorderLevel": reorder_level,
        "stockGated": stock_gated,
    }
    if seller_available is not None:
        data["sellerAvailable"] = seller_available
    return data


def market(mid, name, street, house, zip_code, city, day_index, begin, end,
           active=True, contacts=(), price_conditions=(), pricing_revision=0):
    """/markets/{sellerId}/{marketId} — GitLiveMarketRepository.marketToMap."""
    return {
        "id": mid,
        "name": name,
        "street": street,
        "houseNumber": house,
        "city": city,
        "zipCode": zip_code,
        "dayOfWeek": GERMAN_DAYS[day_index],
        "begin": begin,
        "end": end,
        "dayIndex": day_index,  # Monday = 0. The live DBs once stored Sunday = 1.
        "active": active,
        "pricingRevision": pricing_revision,
        "priceConditions": list(price_conditions),
        "contacts": list(contacts),
    }


def contact(channel, value, label):
    return {"channel": channel, "value": value, "label": label}


def condition(cause, effect, amount, scope_type="ALL", scope_value="", note=""):
    return {
        "cause": cause,
        "scopeType": scope_type,
        "scopeValue": scope_value,
        "effect": effect,
        "amount": amount,
        "note": note,
    }


def buyer_profile(uid, display_name, email, phone, anonymous=False, default_market="",
                  default_pickup_time="", placed=None, favourites=(), street="",
                  house="", self_pickup=False, legacy=False):
    """/buyer_profile/{uid} — GitLiveProfileRepository. `legacy` writes the pre-rename shape."""
    profile = {
        "id": uid,
        "displayName": display_name,
        "emailAddress": email,
        "telephoneNumber": phone,
        "photoUrl": "",
        "anonymous": anonymous,
        "defaultMarket": default_market,
        "defaultPickUpTime": default_pickup_time,
        "placedOrderIds": placed or {},
        "favouriteArticles": list(favourites),
        "street": street,
        "houseNumber": house,
        "isSelfPickup": self_pickup,
    }
    if legacy:
        # How profiles written by the universe-era app still look in the live DB:
        # "defaultTime" instead of "defaultPickUpTime", favourites under the Firebase
        # name, and `anonymous` as 0/1 rather than a boolean.
        profile["defaultTime"] = profile.pop("defaultPickUpTime")
        profile["favouriteProductIds"] = profile.pop("favouriteArticles")
        profile["anonymous"] = 0 if anonymous is False else 1
    return profile


def access(uid, display_name, status, allowed_markets=(), updated_ms=0, via_token=None):
    """/buyer_access_status/{sellerId}/{buyerId}. allowedMarkets is ONE comma-joined
    string (MarketScope.encode) — a child map cannot be compared inside a rule."""
    record = {
        "buyerId": uid,
        "displayName": display_name,
        "status": status,
        "allowedMarkets": ",".join(sorted(allowed_markets)),
        "updatedAt": updated_ms,
    }
    if via_token:
        record["viaToken"] = via_token
    return record


def line(article_id, name, unit, price, amount_count, base_price=None,
         price_components=(), pieces_count=-1):
    """One entry of Order.articles — GitLiveOrderRepository.orderToMap.

    The article's database id goes in productId and `id` stays empty: that is what
    the buy app writes and what articleKey() keys on."""
    if unit == "kg":
        amount = f"{amount_count:.3f}".replace(".", ",") + " kg"
    elif unit in ("Stück", "Bund"):
        amount = f"{int(amount_count)} {unit}"
    else:
        amount = f"{amount_count:.2f}".replace(".", ",") + f" {unit}"
    entry = {
        "id": "",
        "productId": article_id,
        "productName": name,
        "unit": unit,
        "price": price,
        "amount": amount,
        "amountCount": amount_count,
        "piecesCount": pieces_count,
    }
    if base_price is not None:
        entry["basePrice"] = base_price
    if price_components:
        entry["priceComponents"] = list(price_components)
    return entry


def order(oid, buyer_uid, buyer_name, buyer_email, pickup_date, lines, now_ms,
          market_id=MKT_THU, status=None, origin="PRODUCTION", pricing_revision=0,
          message="", created_offset_days=9, fulfilment="PICKUP", delivery_address=None,
          hidden_by_seller=False, hidden_by_buyer=False, not_favourite=True):
    """/orders/{sellerId}/{yyyyMMdd}/{orderId} — flat buyerId/buyerName/buyerEmail."""
    record = {
        "id": oid,
        "buyerId": buyer_uid,
        "buyerName": buyer_name,
        "buyerEmail": buyer_email,
        "createdDate": millis(pickup_date - dt.timedelta(days=created_offset_days), 10, 12),
        "sellerId": SELLER,
        "marketId": market_id,
        "fulfilment": fulfilment,
        "pricingRevision": pricing_revision,
        "pickUpDate": millis(pickup_date),
        "message": message,
        "notFavourite": not_favourite,
        "articles": lines,
        "status": status or derived_status(pickup_date, now_ms),
        "hiddenBySeller": hidden_by_seller,
        "hiddenByBuyer": hidden_by_buyer,
        "origin": origin,
    }
    if delivery_address is not None:
        record["deliveryAddress"] = delivery_address
    return record


def movement(article_id, quantity, unit, kind, source, recorded_ms,
             counted_to=None, note="", sale_id=None):
    """/stock_movements/{sellerId}/{yyyyMM}/{id} — append-only. StockNodes.movementToMap."""
    record = {
        "articleId": article_id,
        "quantity": quantity,
        "unit": unit,
        "kind": kind,
        "source": source,
        "recordedAt": recorded_ms,
    }
    if counted_to is not None:
        record["countedTo"] = counted_to
    if note:
        record["note"] = note
    if sale_id:
        record["saleId"] = sale_id
    return record


def level(on_hand, unit, last_movement_ms, last_counted_ms=None, last_source="MANUAL"):
    """/stock/{sellerId}/{articleId} — the cache the movements add up to."""
    record = {
        "onHand": on_hand,
        "unit": unit,
        "lastMovementAt": last_movement_ms,
        "lastSource": last_source,
    }
    if last_counted_ms is not None:
        record["lastCountedAt"] = last_counted_ms
    return record


def sale_line(article_id, name, unit, quantity, unit_price_cents, tax_rate, acquire_cents=None):
    record = {
        "articleId": article_id,
        "productId": "0",
        "productName": name,
        "unit": unit,
        "quantity": quantity,
        "unitPriceCents": unit_price_cents,
        "taxRate": tax_rate,
    }
    if acquire_cents is not None:
        record["acquirePriceCents"] = acquire_cents
    return record


def sale(order_id, confirmed_ms, pickup_ms, lines):
    """/sales/{sellerId}/{yyyyMM}/{saleId}. `lines` is an index-keyed map, as
    SaleNodes.saleToMap writes it — the reader accepts a list too."""
    return {
        "orderId": order_id,
        "confirmedAt": confirmed_ms,
        "pickUpDate": pickup_ms,
        "lines": {str(i): entry for i, entry in enumerate(lines)},
    }


def ui_action(aid, label, value, location="TOPBAR_HOME", audience="EVERYONE"):
    """/seller_ui_actions/{sellerId}/{actionId}. `value` carries no scheme on purpose
    — the rules refuse a ':' — and the buy app prepends https://."""
    return {"id": aid, "kind": "OPEN_URL", "label": label, "value": value,
            "location": location, "audience": audience}


def scarcity_band(on_hand, committed, reorder_level, package_size):
    """The band the seller app would publish — SellerArticle.scarcityOf."""
    reference = reorder_level or package_size
    if not reference:
        return "UNKNOWN"
    remaining = on_hand - committed
    if remaining <= reference / 2:
        return "LAST_FEW"
    if remaining <= reference:
        return "LIMITED"
    return "PLENTIFUL"


# --- the catalogue ---------------------------------------------------------------

def catalogue():
    """The eight articles every fixture shares, as (id, public, private) triples."""
    return [
        ("art-kartoffel-linda",
         article("Linda Kartoffeln", "kg", 2.30, "Kartoffel", "Kartoffel,Linda,festkochend",
                 "Festkochende Kartoffel vom Hof Apfeltraum. Ungewaschen, lose.", IMG["kartoffel"]),
         seller_article(1.10, 1.30, "BTR", "DE", package_size=12.5, reorder_level=20.0)),
        ("art-apfel-granny",
         article("Granny Smith", "kg", 3.40, "Apfel", "Apfel,Granny,saeuerlich",
                 "Leicht säuerlicher Apfel aus kontrolliert biologischem Anbau.", IMG["apfel"]),
         seller_article(2.05, 1.30, "BTR", "IT", package_size=13.0, reorder_level=10.0)),
        ("art-banane-feige",
         article("Feigenbananen", "kg", 4.20, "Bananen", "Banane,Obst,Feigenbanane",
                 "Demeter Biobananen aus Peru.", IMG["banane"]),
         seller_article(2.60, 1.30, "SCH", "PE", cert="DB", package_size=18.0)),
        ("art-knoblauch-frisch",
         article("Knoblauch", "Stück", 0.90, "Gemuese", "Knoblauch,Gemuese",
                 "Vom Biohof Waldheide. Frisch geerntet.", IMG["knoblauch"],
                 weight_per_piece=0.08),
         seller_article(0.45, 1.50, "SCH", "REG", package_size=50.0)),
        ("art-erdbeere-schale",
         article("Erdbeeren", "Schale", 5.69, "Obst", "Erdbeere,Obst,Schale",
                 "500g-Schale, je nach Wetter. Begrenzte Menge.", IMG["erdbeere"],
                 tax=REDUCED),
         seller_article(3.40, 1.50, "BTR", "DE", package_size=8.0, reorder_level=6.0,
                        stock_gated=True)),
        ("art-paprika-rot",
         article("Rote Paprika", "kg", 6.90, "Gemuese", "Paprika,Gemuese,rot",
                 "Knackige rote Paprika vom Biohof Waldheide.", IMG["paprika"]),
         seller_article(4.20, 1.30, "SCH", "ES", package_size=5.0)),
        ("art-basilikum-topf",
         article("Basilikum im Topf", "Stück", 2.80, "Kraeuter", "Basilikum,Kraeuter,Topf",
                 "Frischer Basilikum im Topf, Bio-Qualität.", IMG["sellerie"],
                 weight_per_piece=0.3),
         seller_article(1.60, 1.50, "SCH", "DE", package_size=10.0)),
        ("art-sellerie-stange",
         article("Stangensellerie", "Stück", 2.50, "Gemuese", "Sellerie,Gemuese,Stange",
                 "Stangensellerie, regional im Freiland gewachsen.", IMG["sellerie"],
                 weight_per_piece=0.45),
         seller_article(1.45, 1.30, "BTR", "REG", package_size=12.0)),
    ]


def markets_for(today, pop_up=False, deactivate_monday=False):
    """The seller's stalls. One per weekday so each fixture can exercise a different
    pickup day and a different edit deadline from the same seller."""
    stalls = {
        MKT_THU: market(
            MKT_THU, "Wochenmarkt Onkel Toms Hütte", "Onkel-Tom-Straße", "99",
            "14169", "Berlin", 3, "12:00", "18:30",
            contacts=[contact("PHONE", "+4930123456", "Stand Onkel Tom")],
        ),
        MKT_SAT: market(
            MKT_SAT, "Markt am Kranoldplatz", "Kranoldplatz", "4",
            "12051", "Berlin", 5, "08:00", "13:00",
            pricing_revision=3,
            price_conditions=[
                condition("STALL_FEE", "FACTOR", 1.10, note="Standmiete Samstagsmarkt"),
                condition("TRANSPORT", "SURCHARGE", 0.20, scope_type="CATEGORY",
                          scope_value="Bananen", note="Kühltransport"),
            ],
            contacts=[contact("WHATSAPP", "4915112345678", "Kranoldplatz")],
        ),
        MKT_MON: market(
            MKT_MON, "Breitenbachplatz", "Breitenbachplatz", "1",
            "14195", "Berlin", 0, "15:00", "19:00",
            active=not deactivate_monday,
        ),
    }
    if pop_up:
        tomorrow = today + dt.timedelta(days=1)
        stalls[MKT_POP] = market(
            MKT_POP, "Hofladen-Abholung (Pop-up)", "Apfeltraumweg", "7",
            "15377", "Müncheberg", tomorrow.isoweekday() - 1, "09:00", "17:00",
        )
    return stalls


def seller_profile(today, stalls, closures=()):
    """/seller_profile/{sellerId}.

    `markets` is duplicated here as a list because GitLiveProfileRepository still reads
    the stalls off the profile as well as from /markets — dropping either one leaves
    half the app without market names. Closures live here too (SellerProfile.closures)."""
    return {
        "id": SELLER,
        "sellerId": SELLER,
        "displayName": "BodenSchätze",
        "firstName": "Eric",
        "lastName": "Dehn",
        "street": "Frankfurter Allee",
        "houseNumber": "27",
        "city": "Berlin",
        "zipCode": "14195",
        "telephoneNumber": "01724623741",
        "lat": "52.4567",
        "lng": "13.2890",
        "urls": ["https://bodenschaetze.example"],
        "defaultFilterChips": ["Obst", "Gemüse", "Favouriten", "Anderes"],
        "contacts": [
            contact("PHONE", "+4930123456", "Büro"),
            contact("EMAIL", "hallo@bodenschaetze.example", "Bestellungen"),
        ],
        "closures": list(closures),
        "markets": list(stalls.values()),
        "mode": -1,
    }


# --- fixture 1: a cold shop ------------------------------------------------------

def fixture_cold_start(today, now_ms):
    arts = catalogue()
    stalls = markets_for(today)
    data = {
        "articles": {SELLER: {aid: pub for aid, pub, _ in arts}},
        "seller_articles": {SELLER: {aid: priv for aid, _, priv in arts}},
        "markets": {SELLER: stalls},
        "seller_profile": {SELLER: seller_profile(today, stalls)},
        "buyer_profile": {
            # Approved, complete profile, nothing ordered yet: the clean first run.
            B1: buyer_profile(B1, "Anna Berger", "anna.berger@example.com", "+4915100000001",
                              default_market=MKT_THU, default_pickup_time="16:30",
                              street="Argentinische Allee", house="12",
                              favourites=["art-apfel-granny"]),
            # Signed in, never said who they are: carries the stored placeholder.
            B2: buyer_profile(B2, "Bodenschatz", "", "", anonymous=True),
            # Approved but has not picked a market yet — onboarding should ask.
            B3: buyer_profile(B3, "Tobias Krenz", "tobias.krenz@example.com", "+4915100000003",
                              self_pickup=True),
        },
        "buyer_access_status": {
            SELLER: {
                B1: access(B1, "Anna Berger", "APPROVED",
                           updated_ms=now_ms - 30 * 86400_000),
                # Asked for access and is waiting. No allowedMarkets while PENDING.
                B2: access(B2, "Bodenschatz", "PENDING", updated_ms=now_ms - 2 * 86400_000),
                B3: access(B3, "Tobias Krenz", "APPROVED", updated_ms=now_ms - 5 * 86400_000),
            }
        },
        "access_requests": {
            SELLER: {B2: {"marketId": MKT_THU, "requestedAt": now_ms - 2 * 86400_000,
                          "note": "Würde gerne donnerstags abholen"}}
        },
        "seller_ui_actions": {
            SELLER: {"act-shop-info": ui_action("act-shop-info", "Hofladen",
                                                "bodenschaetze.example/hofladen")}
        },
    }
    cases = [
        case("F1-01", "First run, approved buyer, empty history",
             f"buy flavour, sign in as {B1}",
             ["Open the app and log in as Anna Berger."],
             "Catalogue shows 8 articles, order history is empty, and the next pickup "
             "slots offered are one per market (Mon/Thu/Sat)."),
        case("F1-02", "Market pick is asked once",
             "buy flavour, sign in as " + B3,
             ["Log in as Tobias Krenz, whose defaultMarket is empty."],
             "The scaffold asks which market he collects at, writes it to "
             "buyer_profile/defaultMarket, and never asks again."),
        case("F1-03", "Placeholder name reaches the seller",
             "buy flavour as " + B2 + ", then sell flavour",
             ["Place an order as the unnamed buyer.", "Open the seller's order list."],
             "The order shows 'Bodenschatz', not an empty line — the placeholder is "
             "stored on the profile, not rendered."),
        case("F1-04", "Pending buyer is gated",
             "buy flavour as " + B2,
             ["Place an order while the access status is PENDING."],
             "The order is written to /demo_orders, carries origin DEMO, and never "
             "appears in the seller's /orders subscription."),
        case("F1-05", "Approved buyer with no market scope sees every stall",
             "buy flavour as " + B1,
             ["Open the pickup slot picker."],
             "All three markets are offered: allowedMarkets is the empty string, "
             "which MarketScope.decode reads as 'all of them'."),
        case("F1-06", "Seller button is published to everyone",
             "buy flavour, top bar on home",
             ["Look at the home top bar."],
             "One 'Hofladen' button, opening https://bodenschaetze.example/hofladen. "
             "The stored value carries no scheme — the rules refuse a ':'."),
        case("F1-07", "Nothing is stock-gated yet",
             "both flavours",
             ["Check the Erdbeeren tile and the seller's stock screen."],
             "Erdbeeren are stockGated with no ledger entry at all, so onHand is "
             "unknown: available stays true and the scarcity band stays UNKNOWN."),
    ]
    return data, cases, {
        "pickupThu": date_key(next_on(today, 3)),
        "pickupSat": date_key(next_on(today, 5)),
        "pickupMon": date_key(next_on(today, 0)),
    }


# --- fixture 2: a week with history ----------------------------------------------

def fixture_busy_week(today, now_ms):
    arts = catalogue()
    public = {aid: pub for aid, pub, _ in arts}
    private = {aid: priv for aid, _, priv in arts}
    stalls = markets_for(today, pop_up=True)

    # A seller-wide closure four Thursdays out, so the slot list has to skip a day.
    closed_thu = next_on(today, 3, weeks_ahead=3)
    stalls_profile = seller_profile(today, stalls, closures=[
        {"date": date_key(closed_thu), "marketId": "", "reason": "Feiertag"},
        {"date": date_key(next_on(today, 0, weeks_ahead=2)), "marketId": MKT_MON,
         "reason": "Urlaub Breitenbachplatz"},
    ])

    # --- history: three collected Thursdays for Anna ----------------------------
    orders, placed_b1 = {}, {}
    history = [
        (last_on(today, 3, weeks_back=2), [
            line("art-kartoffel-linda", "Linda Kartoffeln", "kg", 2.30, 3.0),
            line("art-apfel-granny", "Granny Smith", "kg", 3.40, 1.5),
        ]),
        (last_on(today, 3, weeks_back=1), [
            line("art-kartoffel-linda", "Linda Kartoffeln", "kg", 2.30, 2.0),
            line("art-basilikum-topf", "Basilikum im Topf", "Stück", 2.80, 2.0, pieces_count=2),
        ]),
        (last_on(today, 3), [
            line("art-paprika-rot", "Rote Paprika", "kg", 6.90, 0.8),
            line("art-erdbeere-schale", "Erdbeeren", "Schale", 5.69, 2.0, pieces_count=2),
        ]),
    ]
    for i, (pickup, lines) in enumerate(history, start=1):
        oid = f"ord-f2-hist-{i}"
        add_order(orders, pickup, oid, order(
            oid, B1, "Anna Berger", "anna.berger@example.com", pickup, lines, now_ms,
            message="Bitte die Kartoffeln ungewaschen." if i == 1 else "",
        ))
        placed_b1[date_key(pickup)] = oid

    # --- an order that is still editable: next Thursday -------------------------
    open_pickup = next_on(today, 3)
    open_id = "ord-f2-open-anna"
    add_order(orders, open_pickup, open_id, order(
        open_id, B1, "Anna Berger", "anna.berger@example.com", open_pickup, [
            line("art-kartoffel-linda", "Linda Kartoffeln", "kg", 2.30, 2.5),
            line("art-banane-feige", "Feigenbananen", "kg", 4.20, 1.0),
            line("art-erdbeere-schale", "Erdbeeren", "Schale", 5.69, 3.0, pieces_count=3),
        ], now_ms, created_offset_days=1, not_favourite=False,
    ))
    placed_b1[date_key(open_pickup)] = open_id

    # --- an order that is locked: the pop-up stall runs tomorrow -----------------
    locked_pickup = today + dt.timedelta(days=1)
    locked_id = "ord-f2-locked-tobias"
    add_order(orders, locked_pickup, locked_id, order(
        locked_id, B3, "Tobias Krenz", "tobias.krenz@example.com", locked_pickup, [
            line("art-sellerie-stange", "Stangensellerie", "Stück", 2.50, 2.0, pieces_count=2),
            line("art-knoblauch-frisch", "Knoblauch", "Stück", 0.90, 4.0, pieces_count=4),
        ], now_ms, market_id=MKT_POP, created_offset_days=5,
    ))

    # --- a Saturday order priced through the market's conditions ----------------
    # The charged price is the snapshot: base 4.20 → +0.20 transport → ×1.10 stall fee.
    sat_pickup = next_on(today, 5)
    sat_id = "ord-f2-saturday-tobias"
    add_order(orders, sat_pickup, sat_id, order(
        sat_id, B3, "Tobias Krenz", "tobias.krenz@example.com", sat_pickup, [
            line("art-banane-feige", "Feigenbananen", "kg", 4.84, 2.0, base_price=4.20,
                 price_components=[
                     {"cause": "TRANSPORT", "amount": 0.20, "note": "Kühltransport"},
                     {"cause": "STALL_FEE", "amount": 0.44, "note": "Standmiete Samstagsmarkt"},
                 ]),
            line("art-apfel-granny", "Granny Smith", "kg", 3.74, 1.0, base_price=3.40,
                 price_components=[
                     {"cause": "STALL_FEE", "amount": 0.34, "note": "Standmiete Samstagsmarkt"},
                 ]),
        ], now_ms, market_id=MKT_SAT, pricing_revision=3, created_offset_days=2,
    ))

    # --- the ledger -------------------------------------------------------------
    intake_ms = millis(today - dt.timedelta(days=2), 7, 30)
    count_ms = millis(today - dt.timedelta(days=1), 19, 5)
    month = month_key(today - dt.timedelta(days=2))
    movements = {
        "mv-f2-kartoffel-intake": movement("art-kartoffel-linda", 80.0, "kg", "INTAKE",
                                           "MANUAL", intake_ms, note="Lieferung BTR"),
        "mv-f2-kartoffel-count": movement("art-kartoffel-linda", 0.0, "kg", "STOCKTAKE",
                                          "SCALE", count_ms, counted_to=76.4,
                                          note="Abendzählung"),
        "mv-f2-erdbeere-intake": movement("art-erdbeere-schale", 20.0, "Schale", "INTAKE",
                                          "MANUAL", intake_ms),
        # The two booked Schale of ord-f2-hist-3, carrying the sale they came from...
        "mv-f2-erdbeere-sale": movement("art-erdbeere-schale", -2.0, "Schale", "SALE",
                                        "ORDERED", millis(last_on(today, 3), 17, 40),
                                        sale_id="sale-f2-hist-3"),
        # ...and what went over the counter to walk-in customers, which belongs to no order.
        "mv-f2-erdbeere-counter": movement("art-erdbeere-schale", -6.0, "Schale", "SALE",
                                           "MANUAL", millis(last_on(today, 3), 18, 20),
                                           note="Laufkundschaft"),
        "mv-f2-erdbeere-loss": movement("art-erdbeere-schale", -2.0, "Schale", "LOSS",
                                        "MANUAL", count_ms, note="Druckstellen"),
    }
    erdbeere_on_hand = 20.0 - 2.0 - 6.0 - 2.0
    levels = {
        "art-kartoffel-linda": level(76.4, "kg", count_ms, last_counted_ms=count_ms,
                                     last_source="SCALE"),
        "art-erdbeere-schale": level(erdbeere_on_hand, "Schale", count_ms),
    }

    # Erdbeeren are the only gated article; committed demand is the open order's 3
    # Schale, and reorderLevel 6 is the reference the band is measured against.
    committed = 3.0
    public["art-erdbeere-schale"]["scarcity"] = scarcity_band(
        erdbeere_on_hand, committed, 6.0, 8.0)
    public["art-erdbeere-schale"]["available"] = erdbeere_on_hand - committed > 0

    # --- one booked sale, with its index entry ----------------------------------
    booked_pickup = last_on(today, 3)
    booked_ms = millis(booked_pickup, 18, 10)
    sale_month = month_key(booked_pickup)

    data = {
        "articles": {SELLER: public},
        "seller_articles": {SELLER: private},
        "markets": {SELLER: stalls},
        "seller_profile": {SELLER: stalls_profile},
        "buyer_profile": {
            B1: buyer_profile(B1, "Anna Berger", "anna.berger@example.com", "+4915100000001",
                              default_market=MKT_THU, default_pickup_time="16:30",
                              placed=placed_b1, street="Argentinische Allee", house="12",
                              favourites=["art-apfel-granny", "art-erdbeere-schale"]),
            B2: buyer_profile(B2, "Bodenschatz", "", "", anonymous=True),
            B3: buyer_profile(B3, "Tobias Krenz", "tobias.krenz@example.com", "+4915100000003",
                              default_market=MKT_SAT, default_pickup_time="09:15",
                              placed={date_key(locked_pickup): locked_id,
                                      date_key(sat_pickup): sat_id},
                              self_pickup=True),
        },
        "buyer_access_status": {
            SELLER: {
                B1: access(B1, "Anna Berger", "APPROVED", updated_ms=now_ms - 90 * 86400_000),
                B2: access(B2, "Bodenschatz", "PENDING", updated_ms=now_ms - 86400_000),
                # Scoped to two stalls: the Saturday market and the pop-up he ordered at.
                B3: access(B3, "Tobias Krenz", "APPROVED", allowed_markets=[MKT_SAT, MKT_POP],
                           updated_ms=now_ms - 20 * 86400_000),
            }
        },
        "orders": {SELLER: orders},
        "demo_orders": {SELLER: demo_orders_for(B2, today, now_ms)},
        "stock": {SELLER: levels},
        "stock_movements": {SELLER: {month: movements}},
        "sales": {SELLER: {sale_month: {"sale-f2-hist-3": sale(
            "ord-f2-hist-3", booked_ms, millis(booked_pickup), [
                sale_line("art-paprika-rot", "Rote Paprika", "kg", 0.8, 690, STANDARD,
                          acquire_cents=420),
                sale_line("art-erdbeere-schale", "Erdbeeren", "Schale", 2.0, 569, REDUCED,
                          acquire_cents=340),
            ])}}},
        "sale_index": {SELLER: {"ord-f2-hist-3": {"sale-f2-hist-3": sale_month}}},
        "seller_ui_actions": {
            SELLER: {
                "act-shop-info": ui_action("act-shop-info", "Hofladen",
                                           "bodenschaetze.example/hofladen"),
                "act-demo-help": ui_action("act-demo-help", "Erst freischalten",
                                           "bodenschaetze.example/freischalten",
                                           audience="DEMO_ONLY"),
                "act-preorder": ui_action("act-preorder", "Vorbestellung",
                                          "bodenschaetze.example/vorbestellung",
                                          location="TOPBAR_PROFILE",
                                          audience="APPROVED_ONLY"),
            }
        },
    }
    cases = [
        case("F2-01", "Open order is editable, locked order is not",
             "buy flavour as " + B1 + " and " + B3,
             ["Open Anna's order for next Thursday.",
              "Open Tobias' order for the pop-up stall tomorrow."],
             "Anna's is editable (deadline is two days before pickup at 23:59); "
             "Tobias' is LOCKED and read-only, with the pickup still in the future."),
        case("F2-02", "Order history lists three collected orders",
             "buy flavour as " + B1,
             ["Open the order history."],
             "Three COMPLETED orders from the last three Thursdays plus the open one. "
             "They resolve through buyer_profile.placedOrderIds (date → orderId)."),
        case("F2-03", "Reorder builds a fresh draft",
             "buy flavour as " + B1,
             ["Pick the oldest completed order and reorder it."],
             "A DRAFT with a new id, no pickup date, and today's createdDate; the old "
             "order is untouched and prices are re-checked against the catalogue."),
        case("F2-04", "A Saturday order keeps the price it was charged",
             "buy flavour as " + B3 + ", seller order detail",
             ["Open the Saturday order and its price breakdown."],
             "Feigenbananen show 4,84 € with base 4,20 €, a 0,20 € transport surcharge "
             "and a 0,44 € stall fee. pricingRevision 3 is on the order; editing the "
             "market's conditions must not change these numbers."),
        case("F2-05", "Market scope limits the slots on offer",
             "buy flavour as " + B3,
             ["Open the pickup slot picker."],
             "Only the Kranoldplatz and pop-up stalls appear — allowedMarkets names "
             "those two. The Thursday and Monday markets are not offered."),
        case("F2-06", "A closure removes a pickup day",
             "buy flavour as " + B1,
             ["Scroll the pickup dates four Thursdays out."],
             "The closed Thursday ('Feiertag') is absent, and the Monday two weeks out "
             "is absent for the Breitenbachplatz stall only."),
        case("F2-07", "Scarcity band and derived availability",
             "both flavours, Erdbeeren",
             ["Compare the seller's stock screen with the buyer's tile."],
             "onHand 10 Schale, 3 committed by the open order, reorderLevel 6 → "
             "remaining 7 reads as PLENTIFUL and available stays true. Book 5 more and "
             "the band must fall to LIMITED, then LAST_FEW."),
        case("F2-08", "The ledger adds up to the cached level",
             "sell flavour, stock screen",
             ["Open Linda Kartoffeln's stock detail."],
             "Intake 80 kg then a stocktake to 76,4 kg: the level shows 76,4 and "
             "lastCountedAt is set. The stocktake is absolute, not a delta."),
        case("F2-09", "A booked sale is reconciled to its order",
             "sell flavour, bookkeeping",
             ["Export sales for the current month."],
             "One sale for ord-f2-hist-3 with two lines, found through "
             "sale_index/{seller}/{orderId} → yyyyMM. Both halves must agree or the "
             "rules would have refused the write."),
        case("F2-10", "Three audiences, three buttons",
             "buy flavour as each buyer",
             ["Check the home and profile top bars as B1, B2 and B3."],
             "Anna and Tobias (APPROVED) see 'Hofladen' and the profile-bar "
             "'Vorbestellung'; the pending buyer sees 'Hofladen' and 'Erst freischalten'."),
        case("F2-11", "Demo orders stay out of the seller's list",
             "sell flavour",
             ["Open the order list for the next pickup day."],
             "The two /demo_orders entries from the pending buyer are absent: the seller "
             "subscribes to /orders only."),
    ]
    return data, cases, {
        "openPickup": date_key(open_pickup),
        "lockedPickup": date_key(locked_pickup),
        "saturdayPickup": date_key(sat_pickup),
        "closedThursday": date_key(closed_thu),
        "ledgerMonth": month,
        "saleMonth": sale_month,
    }


def demo_orders_for(buyer, today, now_ms):
    """Two demo orders: one the seller could still take over, one too old to move."""
    future = next_on(today, 3)
    past = last_on(today, 3, weeks_back=1)
    out = {}
    oid = "ord-demo-future"
    add_order(out, future, oid, order(
        oid, buyer, "Bodenschatz", "", future, [
            line("art-apfel-granny", "Granny Smith", "kg", 3.40, 1.0),
        ], now_ms, origin="DEMO", status="PLACED", created_offset_days=1))
    old = "ord-demo-stale"
    add_order(out, past, old, order(
        old, buyer, "Bodenschatz", "", past, [
            line("art-kartoffel-linda", "Linda Kartoffeln", "kg", 2.30, 1.0),
        ], now_ms, origin="DEMO", status="PLACED", created_offset_days=3))
    return out


# --- fixture 3: legacy records and edges -----------------------------------------

def fixture_legacy_edges(today, now_ms):
    arts = catalogue()
    public = {aid: pub for aid, pub, _ in arts}
    private = {aid: priv for aid, _, priv in arts}
    stalls = markets_for(today, pop_up=True, deactivate_monday=True)

    # Seasonal article the seller switched off by hand: intent is off, so the derived
    # flag must stay off no matter what the ledger says.
    public["art-spargel-weiss"] = article(
        "Weißer Spargel", "kg", 12.90, "Gemuese", "Spargel,Gemuese,Saison",
        "Nur in der Saison. Derzeit nicht im Angebot.", IMG["sellerie"], available=False)
    private["art-spargel-weiss"] = seller_article(
        8.00, 1.30, "BTR", "DE", package_size=5.0, seller_available=False)

    # Gated article that has run out: available false and the band at LAST_FEW.
    public["art-honig-glas"] = article(
        "Lindenhonig 500g", "Stück", 7.50, "Anderes", "Honig,Glas",
        "Lindenhonig aus Brandenburg, 500g-Glas.", IMG["knoblauch"],
        tax=REDUCED, weight_per_piece=0.5, available=False, scarcity="LAST_FEW")
    private["art-honig-glas"] = seller_article(
        4.60, 1.50, "SCH", "REG", package_size=6.0, reorder_level=4.0, stock_gated=True)

    # The legacy misspelling that is in both live dumps: "weighPerPiece". The current
    # reader looks for "weightPerPiece", so the piece weight comes back as 0.0.
    public["art-knoblauch-frisch"]["weighPerPiece"] = public["art-knoblauch-frisch"].pop(
        "weightPerPiece")

    # An article with no private half at all — written before the split existed.
    # SellerArticle.sellerData is null for it and must not read as "costs nothing".
    public["art-nuesse-walnuss"] = article(
        "Walnüsse", "kg", 14.90, "Anderes", "Walnuss,Nuesse",
        "Walnüsse aus dem Vorjahr, geschält.", IMG["apfel"], tax=REDUCED)

    deactivated_pickup = last_on(today, 0, weeks_back=1)  # a Monday, market now inactive
    legacy_pickup = last_on(today, 3, weeks_back=3)
    not_picked_pickup = last_on(today, 3, weeks_back=1)
    delivery_pickup = next_on(today, 3)
    tomorrow = today + dt.timedelta(days=1)

    orders = {}

    # A pre-market, pre-origin order: flat buyer fields, bare "2.0" amount, no
    # basePrice, piecesCount at Int.MAX, marketId empty, pricingRevision absent.
    legacy_id = "ord-f3-legacy-universe"
    add_order(orders, legacy_pickup, legacy_id, {
        "id": legacy_id,
        "buyerId": B1,
        "buyerName": "Anna Berger",
        "buyerEmail": "anna.berger@example.com",
        "createdDate": millis(legacy_pickup - dt.timedelta(days=4), 9, 0),
        "sellerId": SELLER,
        "marketId": "",
        "message": "",
        "notFavourite": True,
        "pickUpDate": millis(legacy_pickup),
        "status": "COMPLETED",
        "hiddenBySeller": False,
        "hiddenByBuyer": False,
        "articles": [
            {"id": "", "productId": "art-kartoffel-linda", "productName": "Linda Kartoffeln",
             "unit": "kg", "price": 2.30, "amount": "2.0", "amountCount": 2,
             "piecesCount": 2147483647},
            {"id": "", "productId": "art-apfel-granny", "productName": "Granny Smith",
             "unit": "kg", "price": 3.40, "amount": "1.0", "amountCount": 1,
             "piecesCount": 2147483647},
        ],
    })

    # An order against the market the seller has since deactivated.
    inactive_id = "ord-f3-inactive-market"
    add_order(orders, deactivated_pickup, inactive_id, order(
        inactive_id, B1, "Anna Berger", "anna.berger@example.com", deactivated_pickup,
        [line("art-sellerie-stange", "Stangensellerie", "Stück", 2.50, 1.0, pieces_count=1)],
        now_ms, market_id=MKT_MON))

    # Nobody collected it: nothing is booked, and older builds read this as DRAFT.
    nopick_id = "ord-f3-not-picked-up"
    add_order(orders, not_picked_pickup, nopick_id, order(
        nopick_id, B1, "Anna Berger", "anna.berger@example.com", not_picked_pickup,
        [line("art-erdbeere-schale", "Erdbeeren", "Schale", 5.69, 1.0, pieces_count=1)],
        now_ms, status="NOT_PICKED_UP"))

    # A delivery order with no address: the checkout gate refuses this, but orders
    # written before `fulfilment` existed carry neither flag nor address.
    broken_id = "ord-f3-delivery-no-address"
    add_order(orders, delivery_pickup, broken_id, order(
        broken_id, B1, "Anna Berger", "anna.berger@example.com", delivery_pickup,
        [line("art-paprika-rot", "Rote Paprika", "kg", 6.90, 1.2)],
        now_ms, fulfilment="DELIVERY", created_offset_days=1))

    # A proper delivery order, and a price component this build does not know.
    ok_delivery_id = "ord-f3-delivery-ok"
    add_order(orders, tomorrow, ok_delivery_id, order(
        ok_delivery_id, B1, "Anna Berger", "anna.berger@example.com", tomorrow, [
            line("art-banane-feige", "Feigenbananen", "kg", 4.60, 1.0, base_price=4.20,
                 price_components=[
                     {"cause": "HANDLING", "amount": 0.20, "note": "Lieferung"},
                     {"cause": "LOYALTY_BONUS", "amount": 0.20,
                      "note": "Unbekannte Ursache — muss verworfen werden"},
                 ]),
        ], now_ms, market_id=MKT_POP, fulfilment="DELIVERY", delivery_address={
            "street": "Argentinische Allee", "houseNumber": "12", "zipCode": "14163",
            "city": "Berlin", "note": "Seitentür, bitte klingeln"},
        created_offset_days=4))

    # A demo order still carrying the old encoding: isDemoOrder plus the retired
    # OrderStatus.DEMO_ORDER. Origin must be recovered as DEMO, status read as PLACED.
    demo = {}
    demo_future = next_on(today, 3)
    old_demo_id = "ord-f3-demo-legacy"
    add_order(demo, demo_future, old_demo_id, {
        "id": old_demo_id,
        "buyerId": B2,
        "buyerName": "New User",
        "buyerEmail": "",
        "createdDate": now_ms - 3 * 86400_000,
        "sellerId": SELLER,
        "marketId": MKT_THU,
        "message": "",
        "notFavourite": True,
        "pickUpDate": millis(demo_future),
        "status": "DEMO_ORDER",
        "isDemoOrder": True,
        "hiddenBySeller": False,
        "hiddenByBuyer": False,
        "articles": [
            {"id": "", "productId": "art-basilikum-topf", "productName": "Basilikum im Topf",
             "unit": "Stück", "price": 2.80, "amount": "1.0", "amountCount": 1,
             "piecesCount": 1},
        ],
    })
    # And one whose pickup has passed: it must be dropped, not transitioned.
    stale_demo_id = "ord-f3-demo-stale"
    stale_demo_pickup = last_on(today, 3, weeks_back=2)
    add_order(demo, stale_demo_pickup, stale_demo_id, order(
        stale_demo_id, B2, "New User", "", stale_demo_pickup,
        [line("art-knoblauch-frisch", "Knoblauch", "Stück", 0.90, 2.0, pieces_count=2)],
        now_ms, origin="DEMO", status="PLACED"))

    honey_ms = millis(today - dt.timedelta(days=4), 8, 0)
    ledger_month = month_key(today - dt.timedelta(days=4))

    data = {
        "articles": {SELLER: public},
        "seller_articles": {SELLER: private},
        "markets": {SELLER: stalls},
        "seller_profile": {SELLER: seller_profile(today, stalls, closures=[
            {"date": date_key(next_on(today, 3)), "marketId": "", "reason": "Betriebsausflug"},
        ])},
        "buyer_profile": {
            B1: buyer_profile(B1, "Anna Berger", "anna.berger@example.com", "+4915100000001",
                              default_market=MKT_THU, default_pickup_time="16:30",
                              placed={
                                  date_key(legacy_pickup): legacy_id,
                                  date_key(deactivated_pickup): inactive_id,
                                  date_key(not_picked_pickup): nopick_id,
                                  date_key(delivery_pickup): broken_id,
                                  date_key(tomorrow): ok_delivery_id,
                              },
                              street="Argentinische Allee", house="12"),
            # The pre-rename profile shape, exactly as the live DB still holds some.
            B2: buyer_profile(B2, "New User", "", "", anonymous=True, legacy=True),
            # Blocked: may not order, and may not write its own access record back.
            B3: buyer_profile(B3, "Tobias Krenz", "tobias.krenz@example.com",
                              "+4915100000003", default_market=MKT_SAT),
        },
        "buyer_access_status": {
            SELLER: {
                B1: access(B1, "Anna Berger", "APPROVED", updated_ms=now_ms - 120 * 86400_000),
                B2: access(B2, "New User", "PENDING", updated_ms=now_ms - 4 * 86400_000),
                B3: access(B3, "Tobias Krenz", "BLOCKED", updated_ms=now_ms - 6 * 86400_000),
            }
        },
        "access_requests": {
            SELLER: {B2: {"marketId": "", "requestedAt": now_ms - 4 * 86400_000,
                          "note": "Egal welcher Markt"}}
        },
        "invite_tokens": {
            SELLER: {
                # Unredeemed and valid: redeeming it must grant exactly MKT_SAT.
                "tok-valid-sat": {
                    "createdAt": now_ms - 86400_000,
                    "expiresAt": now_ms + 14 * 86400_000,
                    "displayNameHint": "Familie Lindner",
                    "grantsMarkets": MKT_SAT,
                },
                # Expired: the rules refuse the redemption.
                "tok-expired": {
                    "createdAt": now_ms - 40 * 86400_000,
                    "expiresAt": now_ms - 9 * 86400_000,
                    "displayNameHint": "Alter Link",
                    "grantsMarkets": "",
                },
                # Already redeemed by someone else: must not be redeemable twice.
                "tok-redeemed": {
                    "createdAt": now_ms - 20 * 86400_000,
                    "expiresAt": now_ms + 5 * 86400_000,
                    "displayNameHint": "Tobias",
                    "grantsMarkets": f"{MKT_POP},{MKT_SAT}",
                    "redeemedBy": B3,
                },
            }
        },
        "orders": {SELLER: orders},
        "demo_orders": {SELLER: demo},
        "stock": {
            SELLER: {
                # Gated and empty: this is a real zero, because the ledger has seen it.
                "art-honig-glas": level(0.0, "Stück", honey_ms, last_counted_ms=honey_ms),
                # A level for an article whose private half is missing.
                "art-nuesse-walnuss": level(4.0, "kg", honey_ms),
            }
        },
        "stock_movements": {
            SELLER: {ledger_month: {
                "mv-f3-honig-intake": movement("art-honig-glas", 6.0, "Stück", "INTAKE",
                                               "MANUAL", honey_ms),
                "mv-f3-honig-sale": movement("art-honig-glas", -6.0, "Stück", "SALE",
                                             "ORDERED", honey_ms + 3600_000),
                "mv-f3-walnuss-count": movement("art-nuesse-walnuss", 0.0, "kg", "STOCKTAKE",
                                                "SCALE", honey_ms, counted_to=4.0),
            }}
        },
        "seller_ui_actions": {
            SELLER: {
                "act-shop-info": ui_action("act-shop-info", "Hofladen",
                                           "bodenschaetze.example/hofladen"),
                # No `audience` child at all: every action stored before targeting
                # existed. It must read as EVERYONE, not as "nobody".
                "act-no-audience": {
                    "id": "act-no-audience", "kind": "OPEN_URL", "label": "Rezepte",
                    "value": "bodenschaetze.example/rezepte", "location": "TOPBAR_HOME",
                },
            }
        },
    }
    cases = [
        case("F3-01", "A pre-market order still reads",
             "sell flavour, order detail",
             ["Open ord-f3-legacy-universe."],
             "marketId is empty and pricingRevision absent → no market name, no price "
             "breakdown, and the amounts render as '2,000 kg' from amountCount rather "
             "than the stored bare '2.0'. piecesCount 2147483647 must not show."),
        case("F3-02", "The retired demo encoding is recovered",
             "sell flavour after approving " + B2,
             ["Approve the pending buyer."],
             "ord-f3-demo-legacy (isDemoOrder + status DEMO_ORDER) moves into /orders "
             "as origin TRANSITIONED with a real lifecycle status; ord-f3-demo-stale, "
             "whose pickup has passed, is dropped, not moved."),
        case("F3-03", "A blocked buyer cannot order",
             "buy flavour as " + B3,
             ["Try to place an order."],
             "The write is refused by the rules and the app says so. The buyer also "
             "cannot overwrite their own BLOCKED access record."),
        case("F3-04", "A deactivated market still resolves",
             "sell flavour, order history",
             ["Open ord-f3-inactive-market."],
             "Breitenbachplatz shows its name although active is false, and the stall "
             "is no longer offered as a pickup slot. Markets are deactivated, never "
             "deleted, for exactly this."),
        case("F3-05", "Delivery without an address",
             "sell flavour, order list",
             ["Look at ord-f3-delivery-no-address."],
             "The order is flagged as a delivery the seller cannot deliver, rather than "
             "showing a blank address block."),
        case("F3-06", "An unknown price cause is dropped, not invented",
             "both flavours, ord-f3-delivery-ok",
             ["Open the price breakdown."],
             "HANDLING shows; LOYALTY_BONUS is silently dropped (PriceComponentDto "
             "returns null for a cause this build does not know) and the charged "
             "price 4,60 € stays as recorded."),
        case("F3-07", "Seller intent outranks the ledger",
             "both flavours, Weißer Spargel",
             ["Book an intake for Spargel in the seller app."],
             "sellerAvailable is false, so the article stays unavailable to buyers. "
             "Restocking must not undo 'off for the season'."),
        case("F3-08", "A gated article that really is empty",
             "both flavours, Lindenhonig",
             ["Compare the tile with the stock screen."],
             "onHand 0 with lastCountedAt set is a real zero: available false, band "
             "LAST_FEW. Contrast with F1-07, where onHand is simply unknown."),
        case("F3-09", "An article with no private half",
             "sell flavour, Walnüsse",
             ["Open the article editor and the margin column."],
             "sellerData is null: the purchase price shows as unknown, never as 0,00 €, "
             "and saving must not write a private half full of zeroes."),
        case("F3-10", "A legacy buyer profile still loads",
             "buy flavour as " + B2,
             ["Log in and open the profile screen."],
             "The profile carries defaultTime, favouriteProductIds and anonymous as 0. "
             "It loads, and saving migrates it to the current field names."),
        case("F3-11", "Invite tokens: valid, expired, spent",
             "buy flavour, invite links",
             ["Redeem tok-valid-sat, then tok-expired, then tok-redeemed."],
             "The first grants APPROVED scoped to the Kranoldplatz stall only. The "
             "other two are refused — and no redemption may widen the granted markets."),
        case("F3-12", "An action with no audience reaches everyone",
             "buy flavour as each buyer",
             ["Check the home top bar."],
             "'Rezepte' is visible to all three buyers. A missing audience must read as "
             "EVERYONE, not as nobody."),
        case("F3-15", "The weighPerPiece typo",
             "both flavours, Knoblauch",
             ["Open the Knoblauch tile and the seller's article editor."],
             "The stored field is the legacy 'weighPerPiece', which the current reader "
             "does not look at, so the piece weight is 0,00 kg. Either the reader "
             "accepts both spellings or the editor heals it on the next save — it must "
             "not show a wrong weight or crash."),
        case("F3-14", "An order survives its day being closed",
             "sell flavour, order list",
             ["Look at the orders on the closed Thursday."],
             "ord-f3-delivery-no-address sits on a day the seller closed afterwards. "
             "The order must still be listed and openable — a closure removes a day from "
             "what is *offered*, it does not retract orders already placed."),
        case("F3-13", "Every pickup day this week is closed",
             "buy flavour as " + B1,
             ["Open the pickup slot picker."],
             "The seller-wide closure removes the next Thursday, the Monday stall is "
             "inactive, so only the Saturday and pop-up stalls offer a slot."),
    ]
    return data, cases, {
        "legacyPickup": date_key(legacy_pickup),
        "notPickedUp": date_key(not_picked_pickup),
        "deliveryPickup": date_key(delivery_pickup),
        "ledgerMonth": ledger_month,
    }


# --- assembly --------------------------------------------------------------------

def add_order(node, pickup_date, order_id, record):
    node.setdefault(date_key(pickup_date), {})[order_id] = record


def case(cid, title, where, steps, expect):
    return {"id": cid, "title": title, "where": where, "steps": steps, "expect": expect}


FIXTURES = [
    ("fixture-1-cold-start.json", "cold-start",
     "A shop with a catalogue and three buyers, and nothing ordered yet.",
     fixture_cold_start),
    ("fixture-2-busy-week.json", "busy-week",
     "A running week: order history, an editable order, a locked one, stock, a booked sale.",
     fixture_busy_week),
    ("fixture-3-legacy-edges.json", "legacy-edges",
     "Records written by older app versions, plus the awkward cases.",
     fixture_legacy_edges),
]


def build(today):
    now_ms = millis(today, 10, 0)
    out = []
    for filename, name, summary, builder in FIXTURES:
        data, cases, dates = builder(today, now_ms)
        # Everything the tester needs to drive the fixture, in the fixture itself.
        # Underscore-prefixed because no app code reads it and the rules deny it, so
        # it can never be mistaken for data the app owns.
        data = {
            "_test": {
                "fixture": name,
                "summary": summary,
                "generatedFor": today.isoformat(),
                "regenerate": "python3 firebase/fixtures/generate_fixtures.py",
                "warning": "Importing at the database root REPLACES the whole database.",
                "seller": SELLER,
                "buyers": {
                    B1: "Anna Berger — approved, full profile",
                    B2: "unnamed buyer — not approved (demo), stored placeholder name",
                    B3: "Tobias Krenz — approved and market-scoped (BLOCKED in fixture 3)",
                },
                "markets": {MKT_THU: "Donnerstag", MKT_SAT: "Samstag (price conditions)",
                            MKT_MON: "Montag", MKT_POP: "pop-up, runs tomorrow"},
                "dates": dates,
                "timeZone": "Europe/Berlin",
                "cases": cases,
            },
            **data,
        }
        out.append((filename, data, cases))
    return out


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--today", help="ISO date the fixtures are built around")
    parser.add_argument("--out", default=str(pathlib.Path(__file__).parent),
                        help="where to write the JSON files")
    args = parser.parse_args()

    today = dt.date.fromisoformat(args.today) if args.today else dt.datetime.now(TZ).date()
    out_dir = pathlib.Path(args.out)
    out_dir.mkdir(parents=True, exist_ok=True)

    for filename, data, cases in build(today):
        path = out_dir / filename
        path.write_text(json.dumps(data, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
        orders = sum(len(v) for v in data.get("orders", {}).get(SELLER, {}).values())
        demo = sum(len(v) for v in data.get("demo_orders", {}).get(SELLER, {}).values())
        print(f"{path.name}: {len(data['articles'][SELLER])} articles, "
              f"{orders} orders, {demo} demo orders, {len(cases)} test cases")


if __name__ == "__main__":
    main()
