import json,sys,os,collections
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from migrate_prod_categories_maps import category2, unit

# Coarse group driving the buyer's two filter chips (ProductFilter.OBST / GEMUESE,
# SharedStateTypes.kt:517-518, which match on searchTerms). Greengrocer reading:
# potatoes, salad, herbs and mushrooms all sit on the vegetable side of the stall.
GROUP = {"Obst":"Obst", "Gemüse":"Gemüse", "Kartoffeln":"Gemüse",
         "Salat":"Gemüse", "Kräuter":"Gemüse", "Pilze":"Gemüse"}
# Eier and Konserven intentionally belong to neither chip.

src, dst, rep = sys.argv[1], sys.argv[2], sys.argv[3]
d = json.load(open(src))
changes = collections.Counter(); log = []

def trim_node(node, path, fields):
    for f in fields:
        v = node.get(f)
        if isinstance(v, str) and v != v.strip():
            log.append(f"trim    {path}/{f}: {v!r} -> {v.strip()!r}")
            node[f] = v.strip(); changes['trim'] += 1

def add_term(a, term):
    """Append a comma term if the app's own search (raw substring on the whole
    searchTerms string, or on productName) would not already find it."""
    raw = a.get('searchTerms') or ''
    if term.lower() in raw.lower() or term.lower() in (a.get('productName') or '').lower():
        return False
    terms = [x.strip() for x in raw.split(',') if x.strip()]
    terms.append(term)
    a['searchTerms'] = ','.join(terms)
    return True

for sid, arts in d.get('articles', {}).items():
    for aid, a in arts.items():
        p = f"articles/{sid}/{aid}"
        trim_node(a, p, ['productName','detailInfo','searchTerms','category','unit','productId','imageUrl'])
        old_cat = (a.get('category') or '').strip()
        new_cat = category2(old_cat)

        if new_cat != old_cat:
            # keep the specific legacy name findable; search drops the category column
            if add_term(a, old_cat):
                log.append(f"search  {p}: +{old_cat!r} (was the category)"); changes['kept_searchable'] += 1
            log.append(f"cat     {p}: {old_cat!r} -> {new_cat!r}")
            a['category'] = new_cat; changes['category'] += 1

        if add_term(a, new_cat):
            log.append(f"search  {p}: +{new_cat!r} (canonical category)"); changes['term_category'] += 1
        grp = GROUP.get(new_cat)
        if grp and add_term(a, grp):
            log.append(f"search  {p}: +{grp!r} (filter chip)"); changes['term_group'] += 1

        old_u = a.get('unit','')
        if unit(old_u) != old_u:
            log.append(f"unit    {p}: {old_u!r} -> {unit(old_u)!r}")
            a['unit'] = unit(old_u); changes['unit'] += 1
        if a.get('id') != aid:
            log.append(f"id      {p}/id: {a.get('id')!r} -> {aid!r}"); a['id'] = aid; changes['article_id'] += 1

        # Production only ever wrote weighPerPiece; the model reads weightPerPiece
        # (Article.kt, ArticleNodes.articleFromMap) with no fallback, so every
        # article reads back as 0.0 and the buyer's piece count for kg goods is
        # lost. Rename, keeping the value. Only /articles carries the field —
        # order lines hold their own denormalized copies and are untouched.
        if 'weighPerPiece' in a and 'weightPerPiece' not in a:
            a['weightPerPiece'] = a.pop('weighPerPiece')
            log.append(f"field   {p}: weighPerPiece -> weightPerPiece ({a['weightPerPiece']})")
            changes['weight_field'] += 1

for sid, sp in d.get('seller_profile', {}).items():
    p = f"seller_profile/{sid}"
    trim_node(sp, p, ['displayName','firstName','lastName','street','city','zipCode','houseNumber','telephoneNumber'])
    for f in ('id','sellerId'):
        if sp.get(f) != sid:
            log.append(f"id      {p}/{f}: {sp.get(f)!r} -> {sid!r}"); sp[f] = sid; changes['seller_'+f] += 1
    for i, m in enumerate(sp.get('markets') or []):
        trim_node(m, f"{p}/markets/{i}", ['name','street','city','zipCode','houseNumber','dayOfWeek','begin','end'])

# buyer_profile and orders: deliberately untouched
json.dump(d, open(dst,'w'), indent=1, ensure_ascii=False, sort_keys=True)
open(rep,'w').write("\n".join(log) + "\n")
print("changes:", dict(changes), "| total:", sum(changes.values()))
