import json,sys,collections
a=json.load(open(sys.argv[1])); b=json.load(open(sys.argv[2]))
sa=a['articles'][list(a['articles'])[0]]; sb=b['articles'][list(b['articles'])[0]]
ok=lambda n,c: print(("  OK   " if c else "  FAIL ")+n)
print("INTEGRITY")
ok("top-level nodes unchanged", sorted(a)==sorted(b))
ok("buyer_profile byte-identical", a['buyer_profile']==b['buyer_profile'])
ok("orders byte-identical", a['orders']==b['orders'])
ok("104 articles, same keys", set(sa)==set(sb) and len(sb)==104)
ok("no field added/removed", all(set(sa[k])==set(sb[k]) for k in sa))
for f in ('price','imageUrl','available','productId','weighPerPiece','mode'):
    ok(f"{f} untouched", all(sa[k].get(f)==sb[k].get(f) for k in sa))
# detailInfo is trimmed by design; assert nothing but whitespace changed
ok("detailInfo whitespace-trim only",
   all(sa[k].get('detailInfo','').strip()==sb[k].get('detailInfo','') for k in sa))
print("\nSEARCHABILITY (app does raw substring on searchTerms / productName / category)")
def finds(v,q):
    q=q.lower()
    return q in v.get('productName','').lower() or q in v.get('searchTerms','').lower() or q in v.get('category','').lower()
lost=[k for k in sa if (sa[k].get('category') or '').strip() and not finds(sb[k], sa[k]['category'].strip())]
ok(f"every old category still findable ({len(lost)} lost)", not lost)
for k in lost[:5]: print("     ", k, sa[k]['category'], '->', sb[k]['searchTerms'])
reg=[k for k in sa if any(not finds(sb[k],t.strip()) for t in (sa[k].get('searchTerms') or '').split(',') if t.strip())]
ok(f"no original search term lost ({len(reg)})", not reg)
print("\nFILTER CHIPS (ProductFilter.OBST / GEMUESE)")
av=lambda s: [v for v in s.values() if v.get('available')]
for w in ('obst','gemüse'):
    before=sum(1 for v in av(sa) if w in v.get('searchTerms','').lower())
    after =sum(1 for v in av(sb) if w in v.get('searchTerms','').lower())
    print(f"  {w:7} available matches: {before} -> {after}")
nei=[v['productName'] for v in av(sb) if not any(w in v.get('searchTerms','').lower() for w in ('obst','gemüse'))]
print(f"  available in NEITHER chip: {len(nei)} -> {sorted(set(nei))}")
print("\nRESULT")
print("  categories:", dict(collections.Counter(v['category'] for v in sb.values())))
print("  units:     ", dict(collections.Counter(v['unit'] for v in sb.values())))
print("\n  sample:", json.dumps(sb[list(sb)[0]], ensure_ascii=False, indent=1))
