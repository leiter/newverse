# Mirrors shared/src/commonMain/.../domain/model/ProductCategory.kt fromString()
CAT = {}
def _add(names, target):
    for n in names: CAT[n] = target
_add("äpfel apfel birnen birne bananen banane erdbeeren erdbeere kirschen kirsche pflaumen pflaume traube trauben weintraube weintrauben orange orangen zitrone zitronen limette limetten pfirsiche pfirsich nektarinen nektarine melonen melone beeren himbeeren brombeeren heidelbeeren johannisbeeren mango kiwi ananas granatapfel feigen feige maracuja papaya litschi grapefruit mandarine clementine zwetschgen zwetschge mirabellen mirabelle aprikose aprikosen".split(), "Obst")
_add("tomaten tomate gurken gurke paprika zwiebeln zwiebel karotten karotte möhren möhre zucchini auberginen aubergine brokkoli blumenkohl kohlrabi kohl weißkohl rotkohl sellerie stangensellerie knollensellerie lauch porree bohnen erbsen linsen kürbis kürbisse radieschen rettich fenchel spargel artischocken artischocke zuckermais mais spinat mangold knoblauch peperoni chinakohl grünkohl wirsing rosenkohl".split(), "Gemüse")
for n in ["rote beete","rote bete","pak choi","rüben"]: CAT[n]="Gemüse"
_add("kartoffeln kartoffel süßkartoffeln süßkartoffel speisekartoffel speisekartoffeln".split(), "Kartoffeln")
_add("salat blattsalat kopfsalat eisbergsalat feldsalat rucola rukola radicchio endivien römersalat chicorée".split(), "Salat")
CAT["lollo rosso"]="Salat"
_add("petersilie basilikum schnittlauch dill koriander minze pfefferminze oregano thymian rosmarin salbei majoran estragon liebstöckel bärlauch kräuter".split(), "Kräuter")
_add("champignons champignon pilze pilz pfifferlinge steinpilze austernpilze shiitake".split(), "Pilze")
_add("eier ei".split(), "Eier")
_add("milch käse butter sahne schmand joghurt quark frischkäse molkereiprodukte milchprodukte".split(), "Milchprodukte")
_add("fleisch wurst schinken geflügel huhn hähnchen rind rindfleisch schwein schweinefleisch lamm lammfleisch".split(), "Fleisch")
_add("brot brötchen kuchen gebäck backwaren".split(), "Backwaren")
_add("saft wasser tee kaffee getränke getränk limonade bier wein".split(), "Getränke")
_add("marmelade konfitüre honig konserven eingelegtes eingemachtes kompott".split(), "Konserven")
def category(c):
    return CAT.get((c or "").lower().strip(), "Sonstiges")

# Mirrors ProductUnit.kt fromString()
U = {"kg":"kg","kilogram":"kg","kilogramm":"kg","g":"g","gram":"g","gramm":"g",
     "l":"L","liter":"L","ml":"ml","milliliter":"ml",
     "stück":"Stück","stueck":"Stück","st":"Stück","stck":"Stück",
     "bund":"Bund","bd":"Bund","beutel":"Beutel","bt":"Beutel","btl":"Beutel",
     "schale":"Schale","sc":"Schale","sch":"Schale","kasten":"Kasten","kst":"Kasten",
     "glas":"Glas","gl":"Glas","flasche":"Flasche","fl":"Flasche","dose":"Dose","ds":"Dose"}
def unit(u):
    return U.get((u or "").lower().strip(), "Stück")

# Migration extension: legacy categories that fromString() does not yet know.
EXT = {
 "pepperoni":"Gemüse",      # alias of the code's "peperoni"
 "sauerkraut":"Konserven",  # judgment call: sold as a preserve, not fresh produce
 "pastinaken":"Gemüse", "pastinake":"Gemüse",
 "puntarelle":"Salat",      # Italian chicory, eaten as salad
 "spitzkohl":"Gemüse",
 "clementinen":"Obst",      # plural of the code's "clementine"
 "kaki":"Obst", "kakis":"Obst",
 "kurkuma":"Gemüse",        # sold fresh as a root
 "schwarzwurzel":"Gemüse", "schwarzwurzeln":"Gemüse",
 "topinambur":"Gemüse",
 "ingwer":"Gemüse",
 "seitlinge":"Pilze", "austernseitlinge":"Pilze",
 # round-trip: the canonical names themselves
 "obst":"Obst", "gemüse":"Gemüse", "kartoffeln":"Kartoffeln", "sonstiges":"Sonstiges",
}
def category2(c):
    k=(c or "").lower().strip()
    return EXT.get(k) or CAT.get(k) or "Sonstiges"
