#!/usr/bin/env python3
"""Check the generated fixtures for referential integrity before uploading one.

Catches the mistakes that make a fixture look fine in the console and then behave
oddly in the app: an order pointing at a market that is not there, a placedOrderIds
entry under the wrong date key, a sale whose index entry disagrees, a status that
contradicts the edit deadline.

Usage:
    python3 firebase/fixtures/validate_fixtures.py [files...]
"""

import datetime as dt
import json
import pathlib
import sys
import zoneinfo

TZ = zoneinfo.ZoneInfo("Europe/Berlin")


def check(path):
    data = json.loads(path.read_text(encoding="utf-8"))
    meta = data["_test"]
    seller = meta["seller"]
    problems = []

    def bad(msg):
        problems.append(msg)

    articles = data.get("articles", {}).get(seller, {})
    private = data.get("seller_articles", {}).get(seller, {})
    markets = data.get("markets", {}).get(seller, {})
    profiles = data.get("buyer_profile", {})
    access = data.get("buyer_access_status", {}).get(seller, {})

    for aid in private:
        if aid not in articles:
            bad(f"seller_articles/{aid} has no public half (the rules refuse this write)")

    profile_markets = {m["id"] for m in data["seller_profile"][seller]["markets"]}
    if profile_markets != set(markets):
        bad(f"seller_profile.markets {sorted(profile_markets)} != /markets {sorted(markets)}")

    for mid, market in markets.items():
        if market["id"] != mid:
            bad(f"markets/{mid} carries id {market['id']!r}")
        if not -1 <= market["dayIndex"] <= 6:
            bad(f"markets/{mid} dayIndex {market['dayIndex']} out of range")
        if market["priceConditions"] and market["pricingRevision"] == 0:
            bad(f"markets/{mid} has conditions but pricingRevision 0")

    for uid, profile in profiles.items():
        if profile["id"] != uid:
            bad(f"buyer_profile/{uid} carries id {profile['id']!r}")
        default = profile.get("defaultMarket", "")
        if default and default not in markets:
            bad(f"buyer_profile/{uid} defaultMarket {default!r} does not exist")
        for fav in profile.get("favouriteArticles", []) + profile.get("favouriteProductIds", []):
            if fav not in articles:
                bad(f"buyer_profile/{uid} favourite {fav!r} does not exist")
        if uid not in access:
            bad(f"buyer_profile/{uid} has no buyer_access_status record")

    for uid, record in access.items():
        if record["buyerId"] != uid:
            bad(f"buyer_access_status/{uid} carries buyerId {record['buyerId']!r}")
        if record["status"] == "PENDING" and record["allowedMarkets"]:
            bad(f"buyer_access_status/{uid} is PENDING but scopes markets")
        for mid in filter(None, record["allowedMarkets"].split(",")):
            if mid not in markets:
                bad(f"buyer_access_status/{uid} allows unknown market {mid!r}")
        encoded = ",".join(sorted(filter(None, record["allowedMarkets"].split(","))))
        if encoded != record["allowedMarkets"]:
            bad(f"buyer_access_status/{uid} allowedMarkets is not MarketScope.encode order")

    seen_orders = {}
    for root, demo_expected in (("orders", False), ("demo_orders", True)):
        for date_key, by_id in data.get(root, {}).get(seller, {}).items():
            for oid, order in by_id.items():
                where = f"{root}/{date_key}/{oid}"
                seen_orders[oid] = (root, date_key, order)
                if order["id"] != oid:
                    bad(f"{where} carries id {order['id']!r}")
                if order["sellerId"] != seller:
                    bad(f"{where} sellerId {order['sellerId']!r}")
                if order["buyerId"] not in profiles:
                    bad(f"{where} buyerId has no profile")
                pickup = dt.datetime.fromtimestamp(order["pickUpDate"] / 1000, TZ)
                if pickup.strftime("%Y%m%d") != date_key:
                    bad(f"{where} pickUpDate is {pickup.date()}, not the path's date")
                if (pickup.hour, pickup.minute) != (0, 0):
                    bad(f"{where} pickUpDate is not local midnight")
                market_id = order["marketId"]
                if market_id and market_id not in markets:
                    bad(f"{where} marketId {market_id!r} does not exist")
                if market_id:
                    day_index = markets[market_id]["dayIndex"]
                    if pickup.isoweekday() - 1 != day_index:
                        bad(f"{where} pickup is a {pickup.strftime('%A')}, but "
                            f"{market_id} runs on dayIndex {day_index}")
                origin = order.get("origin")
                if demo_expected and origin not in (None, "DEMO"):
                    bad(f"{where} is under demo_orders with origin {origin!r}")
                if not demo_expected and origin == "DEMO":
                    bad(f"{where} is under orders with origin DEMO")
                if order.get("fulfilment") == "DELIVERY" and "deliveryAddress" in order:
                    address = order["deliveryAddress"]
                    if not address.get("street") or not address.get("houseNumber"):
                        bad(f"{where} deliveryAddress is incomplete")
                # A delivery has to name a stall that delivers, or no stall at all.
                # The database rules refuse anything else, so a fixture holding one
                # could never have been written by the app.
                if (order.get("fulfilment") == "DELIVERY" and market_id and
                        markets[market_id].get("allowsDelivery") is False):
                    bad(f"{where} is a DELIVERY at pickup-only market {market_id!r}")
                for i, entry in enumerate(order["articles"]):
                    key = entry["productId"] if entry["productId"] not in ("", "-1") else entry["id"]
                    if key not in articles:
                        bad(f"{where} line {i} names unknown article {key!r}")
                    if "basePrice" in entry and entry["basePrice"] <= 0:
                        bad(f"{where} line {i} basePrice must be absent, not 0")
                    if entry.get("priceComponents") and "basePrice" not in entry:
                        bad(f"{where} line {i} has components but no basePrice")
                    if entry.get("priceComponents"):
                        total = entry["basePrice"] + sum(c["amount"] for c in entry["priceComponents"])
                        if abs(total - entry["price"]) > 0.011:
                            bad(f"{where} line {i} components sum to {total:.2f}, "
                                f"charged {entry['price']:.2f}")
                if order.get("priceComponents") and order.get("pricingRevision", 0) == 0:
                    bad(f"{where} is priced but carries pricingRevision 0")
                # Status against the deadline the app computes: pickup - 2d 23:59.
                # Demo orders are exempt: they are parked at PLACED and only get a real
                # lifecycle when the seller approves the buyer (Order.statusAfterTransition).
                if demo_expected:
                    continue
                deadline = (pickup - dt.timedelta(days=2)).replace(hour=23, minute=59)
                now = dt.datetime.now(TZ)
                status = order["status"]
                if status == "PLACED" and now > deadline:
                    bad(f"{where} is PLACED but its deadline ({deadline:%Y-%m-%d %H:%M}) passed")
                if status == "LOCKED" and (now < deadline or now > pickup):
                    bad(f"{where} is LOCKED outside the locked window")
                if status == "COMPLETED" and now < pickup:
                    bad(f"{where} is COMPLETED before its pickup date")

    for uid, profile in profiles.items():
        for date_key, oid in profile.get("placedOrderIds", {}).items():
            if oid not in seen_orders:
                bad(f"buyer_profile/{uid} placedOrderIds points at missing order {oid!r}")
                continue
            root, stored_key, order = seen_orders[oid]
            if stored_key != date_key:
                bad(f"buyer_profile/{uid} placedOrderIds[{date_key}] = {oid} lives at {stored_key}")
            if order["buyerId"] != uid:
                bad(f"buyer_profile/{uid} claims {oid}, which belongs to {order['buyerId']}")
            if root == "demo_orders":
                bad(f"buyer_profile/{uid} placedOrderIds points at a demo order {oid!r}")

    for month, by_id in data.get("stock_movements", {}).get(seller, {}).items():
        if len(month) != 6 or not month.isdigit():
            bad(f"stock_movements/{month} is not a yyyyMM key")
        for mid, mv in by_id.items():
            if mv["articleId"] not in articles:
                bad(f"stock_movements/{month}/{mid} names unknown article")
            if mv["kind"] == "STOCKTAKE" and "countedTo" not in mv:
                bad(f"stock_movements/{month}/{mid} is a STOCKTAKE without countedTo")
            recorded = dt.datetime.fromtimestamp(mv["recordedAt"] / 1000, TZ)
            if recorded.strftime("%Y%m") != month:
                bad(f"stock_movements/{month}/{mid} was recorded in "
                    f"{recorded.strftime('%Y%m')}")

    # The level is a cache of the ledger: a stocktake sets it, later movements add on.
    for article_id, lvl in data.get("stock", {}).get(seller, {}).items():
        if article_id not in articles:
            bad(f"stock/{article_id} names unknown article")
        moves = [mv for by_id in data.get("stock_movements", {}).get(seller, {}).values()
                 for mv in by_id.values() if mv["articleId"] == article_id]
        if not moves:
            bad(f"stock/{article_id} has a level but no movements")
            continue
        moves.sort(key=lambda mv: mv["recordedAt"])
        counts = [i for i, mv in enumerate(moves) if mv["kind"] == "STOCKTAKE"]
        if counts:
            expected = moves[counts[-1]]["countedTo"] + sum(
                mv["quantity"] for mv in moves[counts[-1] + 1:])
        else:
            expected = sum(mv["quantity"] for mv in moves)
        if abs(expected - lvl["onHand"]) > 0.001:
            bad(f"stock/{article_id} onHand {lvl['onHand']} but the ledger says {expected}")
        if lvl["unit"] != moves[-1]["unit"]:
            bad(f"stock/{article_id} unit {lvl['unit']!r} != newest movement "
                f"{moves[-1]['unit']!r}")

    sales = data.get("sales", {}).get(seller, {})
    index = data.get("sale_index", {}).get(seller, {})
    for month, by_id in sales.items():
        for sid, record in by_id.items():
            order_id = record["orderId"]
            if order_id not in seen_orders:
                bad(f"sales/{month}/{sid} books missing order {order_id!r}")
            if index.get(order_id, {}).get(sid) != month:
                bad(f"sales/{month}/{sid} has no matching sale_index entry "
                    f"(the rules refuse this write)")
            if not record.get("lines"):
                bad(f"sales/{month}/{sid} books nothing")
            for key, entry in record.get("lines", {}).items():
                if entry["articleId"] not in articles:
                    bad(f"sales/{month}/{sid} line {key} names unknown article")
    for order_id, by_id in index.items():
        for sid, month in by_id.items():
            if sales.get(month, {}).get(sid, {}).get("orderId") != order_id:
                bad(f"sale_index/{order_id}/{sid} points nowhere")

    for aid, action in data.get("seller_ui_actions", {}).get(seller, {}).items():
        if action["id"] != aid:
            bad(f"seller_ui_actions/{aid} carries id {action['id']!r}")
        if ":" in action["value"] or action["value"].startswith("/"):
            bad(f"seller_ui_actions/{aid} value carries a scheme (the rules refuse it)")
        if not 0 < len(action["label"]) <= 24:
            bad(f"seller_ui_actions/{aid} label is {len(action['label'])} chars")

    for uid, record in data.get("access_requests", {}).get(seller, {}).items():
        if uid not in profiles:
            bad(f"access_requests/{uid} has no profile")
        if record["marketId"] and record["marketId"] not in markets:
            bad(f"access_requests/{uid} names unknown market")

    for closure in data["seller_profile"][seller]["closures"]:
        if closure["marketId"] and closure["marketId"] not in markets:
            bad(f"closure {closure['date']} names unknown market")
        if len(closure["date"]) != 8 or not closure["date"].isdigit():
            bad(f"closure date {closure['date']!r} is not yyyyMMdd")

    for token, record in data.get("invite_tokens", {}).get(seller, {}).items():
        for mid in filter(None, record.get("grantsMarkets", "").split(",")):
            if mid not in markets:
                bad(f"invite_tokens/{token} grants unknown market {mid!r}")
        if record["expiresAt"] <= record["createdAt"]:
            bad(f"invite_tokens/{token} expires before it was created")

    ids = {c["id"] for c in meta["cases"]}
    if len(ids) != len(meta["cases"]):
        bad("_test.cases has duplicate ids")

    return problems, len(meta["cases"]), len(seen_orders)


def main():
    paths = [pathlib.Path(p) for p in sys.argv[1:]] or sorted(
        pathlib.Path(__file__).parent.glob("fixture-*.json"))
    failed = False
    for path in paths:
        problems, cases, orders = check(path)
        if problems:
            failed = True
            print(f"✗ {path.name}")
            for problem in problems:
                print(f"    {problem}")
        else:
            print(f"✓ {path.name} — {orders} orders, {cases} test cases, no problems")
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main())
