package com.together.newverse.data.parser

/**
 * Lookup tables for the coded fields of a BNN price list.
 *
 * Sources (see `tmp/Terra/`):
 * - Country codes: "Unsere Länderkürzel", Terra Gastro-Sortiment 2025 price list.
 * - Certification codes: "BNN-Liste der Identifikationskürzel (IK-Liste)",
 *   BNN e.V., valid from 01.01.2022.
 * - Producer codes: "Unsere Marken und Hersteller", Terra Gastro-Sortiment 2025.
 *
 * Note that country and certification codes live in separate namespaces and
 * collide: `EG` is Ägypten as a country, but "EU-Öko-VO, Monoprodukt" as a
 * certification. Never look a code up in the wrong table.
 */
internal object BnnCodeTables {

    /** Origin code used by Terra for goods grown in its own delivery region. */
    const val ORIGIN_REGIONAL = "REG"

    /**
     * How [ORIGIN_REGIONAL] is described to customers.
     *
     * Deliberately unspecific. `REG` covers Terra's whole delivery region — by
     * their own account "von der Lausitz bis zur Ostsee", supplied from depots
     * in Berlin, Rostock and Leipzig — so naming a single state would be a
     * claim the price list does not make. Where the producer code resolves, the
     * "Erzeuger" sentence carries the specifics instead.
     */
    const val REGIONAL_ORIGIN = "Aus regionalem Anbau"

    /**
     * A country as it is named in a German sentence.
     *
     * @param name Nominative name, e.g. "Spanien".
     * @param dative Dative phrase including its article for countries that take
     *   one, e.g. "den Niederlanden" — so "Angebaut in den Niederlanden" reads
     *   correctly. Null for the majority of countries, which take no article.
     */
    data class Country(val name: String, val dative: String? = null) {
        /** The country as it appears after the preposition "in". */
        val afterIn: String get() = dative ?: name
    }

    /** Country code -> country. Codes are Terra's, which mostly follow ISO 3166-1. */
    val countries: Map<String, Country> = mapOf(
        "AM" to Country("Armenien"),
        "AR" to Country("Argentinien"),
        "AT" to Country("Österreich"),
        "AZ" to Country("Aserbaidschan"),
        "BA" to Country("Bosnien-Herzegowina"),
        "BE" to Country("Belgien"),
        "BF" to Country("Burkina Faso"),
        "BO" to Country("Bolivien"),
        "BR" to Country("Brasilien"),
        "CA" to Country("Kanada"),
        "CH" to Country("Schweiz", "der Schweiz"),
        "CL" to Country("Chile"),
        "CN" to Country("China"),
        "CO" to Country("Kolumbien"),
        "CR" to Country("Costa Rica"),
        "CU" to Country("Kuba"),
        "CY" to Country("Zypern"),
        "CZ" to Country("Tschechische Republik", "der Tschechischen Republik"),
        "DE" to Country("Deutschland"),
        "DK" to Country("Dänemark"),
        "DO" to Country("Dominikanische Republik", "der Dominikanischen Republik"),
        "EC" to Country("Ecuador"),
        "EE" to Country("Estland"),
        "EG" to Country("Ägypten"),
        "ES" to Country("Spanien"),
        "ET" to Country("Äthiopien"),
        "EU" to Country("EU", "der EU"),
        "EX" to Country("Nicht-EU-Ausland", "dem Nicht-EU-Ausland"),
        "FI" to Country("Finnland"),
        "FR" to Country("Frankreich"),
        "GB" to Country("Großbritannien"),
        "GR" to Country("Griechenland"),
        "GT" to Country("Guatemala"),
        // Terra's list uses HA for Kroatien; HR is the ISO code.
        "HA" to Country("Kroatien"),
        "HR" to Country("Kroatien"),
        "HU" to Country("Ungarn"),
        "ID" to Country("Indonesien"),
        "IE" to Country("Irland"),
        "IL" to Country("Israel"),
        "IN" to Country("Indien"),
        "IS" to Country("Island"),
        "IT" to Country("Italien"),
        "JP" to Country("Japan"),
        "KE" to Country("Kenia"),
        "KG" to Country("Kirgisistan"),
        "KH" to Country("Kambodscha"),
        "KM" to Country("Komoren", "den Komoren"),
        "KR" to Country("Südkorea"),
        "KZ" to Country("Kasachstan"),
        "LK" to Country("Sri Lanka"),
        "LT" to Country("Litauen"),
        "LV" to Country("Lettland"),
        "MA" to Country("Marokko"),
        "MG" to Country("Madagaskar"),
        "MX" to Country("Mexiko"),
        "NL" to Country("Niederlande", "den Niederlanden"),
        "NZ" to Country("Neuseeland"),
        "PE" to Country("Peru"),
        "PH" to Country("Philippinen", "den Philippinen"),
        "PK" to Country("Pakistan"),
        "PL" to Country("Polen"),
        "PT" to Country("Portugal"),
        "PY" to Country("Paraguay"),
        "RU" to Country("Russland"),
        "SE" to Country("Schweden"),
        // Terra's list carries both SF (historic) and FI for Finnland.
        "SF" to Country("Finnland"),
        "SK" to Country("Slowakei", "der Slowakei"),
        "SN" to Country("Senegal"),
        "TH" to Country("Thailand"),
        "TN" to Country("Tunesien"),
        "TR" to Country("Türkei", "der Türkei"),
        "UA" to Country("Ukraine", "der Ukraine"),
        "UG" to Country("Uganda"),
        "US" to Country("Vereinigte Staaten", "den Vereinigten Staaten"),
        "UY" to Country("Uruguay"),
        "VN" to Country("Vietnam"),
        "ZA" to Country("Südafrika")
    )

    /** How a certification is phrased to a customer. */
    enum class CertificationKind {
        /** Certified to the standard of a growers' association, e.g. Demeter. */
        ASSOCIATION,

        /** Certified to the EU organic regulation only. */
        EU_ORGANIC,

        /** IFOAM accreditation. */
        IFOAM,

        /** In conversion to organic farming. */
        IN_CONVERSION,

        /** Not an organic claim — conventional, or outside the scope of the EU regulation. */
        NONE
    }

    data class Certification(val label: String, val kind: CertificationKind)

    /**
     * BNN identification code (IK) -> certification.
     *
     * Mixed-product percentage codes (95-99) are resolved by [certificationFor]
     * rather than listed here.
     */
    val certifications: Map<String, Certification> = mapOf(
        // Anbauverbände & IFOAM accreditation
        "BA" to Certification("Bio Austria", CertificationKind.ASSOCIATION),
        "BS" to Certification("Bio Suisse", CertificationKind.ASSOCIATION),
        "DB" to Certification("Bioland", CertificationKind.ASSOCIATION),
        "DC" to Certification("Ecoland", CertificationKind.ASSOCIATION),
        "DD" to Certification("Demeter", CertificationKind.ASSOCIATION),
        "DG" to Certification("Gäa", CertificationKind.ASSOCIATION),
        "DK" to Certification("Biokreis", CertificationKind.ASSOCIATION),
        "DN" to Certification("Naturland", CertificationKind.ASSOCIATION),
        "NF" to Certification("Naturland fair", CertificationKind.ASSOCIATION),
        "DP" to Certification("Biopark", CertificationKind.ASSOCIATION),
        "DV" to Certification("Verbund Ökohöfe", CertificationKind.ASSOCIATION),
        "DW" to Certification("Ecovin", CertificationKind.ASSOCIATION),
        "IA" to Certification("IFOAM", CertificationKind.IFOAM),
        // EU organic regulation
        "EG" to Certification("EU-Öko-Verordnung", CertificationKind.EU_ORGANIC),
        "C%" to Certification("EU-Öko-Verordnung", CertificationKind.EU_ORGANIC),
        "UW" to Certification("Umstellungsware", CertificationKind.IN_CONVERSION),
        // Outside the EU organic regulation, or conventional
        "NK" to Certification("Naturkosmetik", CertificationKind.NONE),
        "WP" to Certification("Wasch-, Putz- und Reinigungsmittel", CertificationKind.NONE),
        "S#" to Certification("Sonstiges Produkt nach BNN-Sortimentsrichtlinien", CertificationKind.NONE),
        "##" to Certification("Konventionell", CertificationKind.NONE),
        "NG" to Certification("Nicht geregelt", CertificationKind.NONE)
    )

    /**
     * Resolve an IK code, including the mixed-product percentage codes 95-99
     * which all mean "EU organic" for a customer's purposes.
     */
    fun certificationFor(code: String): Certification? {
        val trimmed = code.trim()
        if (trimmed.isEmpty()) return null
        certifications[trimmed]?.let { return it }
        val percentage = trimmed.toIntOrNull()
        if (percentage != null && percentage in 95..99) {
            return Certification("EU-Öko-Verordnung", CertificationKind.EU_ORGANIC)
        }
        return null
    }

    /**
     * Producer / brand code -> name, from Terra's "Marken und Hersteller" list.
     *
     * The list covers Terra's full assortment, so many of the fruit and
     * vegetable supplier codes that appear in a produce price list are not in
     * it. [producerFor] returns null for those and the description simply omits
     * the producer rather than showing a raw code to the customer.
     */
    val producers: Map<String, String> = mapOf(
        "AAC" to "ARTWERK",
        "ACA" to "ACAI",
        "AFE" to "Annes Feinste",
        "AFO" to "Agro Food",
        "AGA" to "Agava",
        "AGO" to "ALB-GOLD",
        "AHZ" to "Adelholzener",
        "AIT" to "ai:tea",
        "AJO" to "Anjola",
        "ALO" to "Allos",
        "ALS" to "Alsan",
        "ALT" to "Alberts - Tofu und mehr",
        "ALU" to "Alb-Natur",
        "AMI" to "Amaizin",
        "AMW" to "Almawin",
        "AND" to "Andechser Molkerei",
        "AOR" to "Aronia Original Produkte",
        "APH" to "Alter Pfarrhof",
        "AQM" to "Aqua Monaco",
        "ARC" to "Arche",
        "ARI" to "Aries",
        "ATO" to "Altomayo",
        "BAC" to "Backensholz",
        "BAE" to "Biofarm A.E.",
        "BAH" to "Das Backhaus",
        "BAK" to "Bauck",
        "BAS" to "Bastiaansen",
        "BAU" to "Bauernmarkt Chiemgauer",
        "BCG" to "Brick Gin",
        "BDH" to "Hof Butendiek",
        "BDL" to "Biokäserei Walchsee",
        "BEG" to "Bergerie",
        "BEP" to "Bergpracht Milchwerk",
        "BFT" to "Braumanufaktur",
        "BGL" to "Milchwerke Berchtesgadener Land",
        "BHO" to "Barnhouse",
        "BIV" to "BioVita",
        "BJR" to "Bella Nana",
        "BKÄ" to "Baldauf Käserei",
        "BKT" to "Biokorntakt Vertriebs",
        "BLJ" to "Black Forest Ice Cream",
        "BLU" to "Blumenbrot",
        "BLÜ" to "Blütenmeer Imkerei",
        "BMH" to "Biomanufaktur Havelland",
        "BMJ" to "Bouche",
        "BML" to "Bio Molkerei Lembach",
        "BMR" to "Bio-Mare",
        "BND" to "Bionade",
        "BOB" to "Bobalis",
        "BOL" to "Bohlsener Mühle",
        "BPF" to "Brandenburger Bio Linse",
        "BPL" to "Bio Planete",
        "BSI" to "Bio Inside",
        "BSK" to "Bio-Schaukäserei Wiggensbach",
        "BSX" to "Bisson",
        "BTP" to "BIOTOPIA",
        "BTR" to "BioTropic",
        "BUM" to "Burgermühle",
        "BVE" to "Bio Vegan",
        "BWI" to "Bio Wiesenmilch",
        "BYO" to "Byodo",
        "CBG" to "Christine Berger",
        "CHI" to "Chiemgauer Naturfleisch",
        "CHN" to "CHIRON Naturdelikatessen",
        "CHO" to "Chora",
        "CHT" to "Charitea",
        "CNS" to "Coteaux Nantais",
        "COB" to "Cosi Bio",
        "CUP" to "Cupper Teas",
        "DAN" to "Danival",
        "DAP" to "D‘Angelo Pasta",
        "DAV" to "Davert",
        "DBH" to "Die Biohennen",
        "DFE" to "Demeter Felderzeugnisse",
        "DFG" to "Svenssons",
        "DFL" to "Die Feine Linie",
        "DOI" to "Do-It B.V.",
        "DOQ" to "Doc‘s Ginger",
        "DRB" to "Dr. Mannah´s",
        "DST" to "Donaustrudel",
        "EBR" to "[ECHT BIO.]",
        "ECA" to "Ei Care",
        "ECE" to "ECOVER Essenial",
        "EDN" to "Eden",
        "ELM" to "Kelterei Elm",
        "EMI" to "EcoMil",
        "ERD" to "Erdmannhauser",
        "ERH" to "Erhardt",
        "ERN" to "Erntesegen",
        "FBA" to "Frisches Biogemüse Brandenburg",
        "FDM" to "FDM",
        "FEL" to "Felicia",
        "FEW" to "FEW",
        "FFF" to "Fairfood Freiburg",
        "FHW" to "Wilke‘s Bioforelle",
        "FIN" to "Finck Ghee - Expert of Ayurveda",
        "FLF" to "Flores Farm",
        "FLL" to "Bürstenfabrik Faller",
        "FLN" to "Flemming",
        "FOD" to "foodloose",
        "FON" to "Fontaine",
        "FPF" to "Freiländer",
        "FRC" to "Francia Mozzarella",
        "FRD" to "Fredo‘s",
        "FRT" to "Florentin",
        "FSP" to "fritz-kulturgüter",
        "GAU" to "Greenhorn",
        "GCC" to "G. Ceci",
        "GEC" to "Gelato Classico",
        "GEP" to "GEPA",
        "GHR" to "Green Heart",
        "GIA" to "GiaPizza",
        "GKR" to "Gut Krauscha Feinkost e.K.",
        "GLM" to "Gläserne Molkerei",
        "GOE" to "Dr. Goerg",
        "GOF" to "Good Sip",
        "GOV" to "Govinda",
        "GOX" to "goodmoodfood",
        "GRH" to "Grünhof",
        "GRN" to "greenorganics",
        "GUA" to "GUA",
        "GUK" to "Gut Kerkow",
        "GÜS" to "Güstrower Schlossquell",
        "HAM" to "Hamfelder Hof",
        "HEB" to "Herzberger Bäckerei",
        "HEU" to "Heuschrecke",
        "HGR" to "Hänsel und Gretel",
        "HHI" to "Heißer Hirsch",
        "HIB" to "Hier2O",
        "HLA" to "Hollala",
        "HLP" to "Hufe8",
        "HMI" to "Havelmi",
        "HMW" to "Marienwaerdt",
        "HÖF" to "Höflich Bio Spirituosen",
        "HOI" to "Hooidammer",
        "HOL" to "Holle",
        "HOY" to "Hoyer",
        "HRL" to "Heirler",
        "HSZ" to "Heisszeit",
        "HUA" to "Humbel Brennerei",
        "HVM" to "Harvest Moon",
        "ICF" to "Ice Cream Factory",
        "ISA" to "bio-verde",
        "JAF" to "Jacky F.",
        "JMS" to "Spree Gin",
        "JOM" to "jolle-mate",
        "JOU" to "Jouis Nour",
        "KAA" to "Kakuzo",
        "KBN" to "KBN",
        "KHT" to "Kaspar Hauser Therapeutikum",
        "KII" to "kilimo",
        "KLY" to "KLUUK",
        "KPL" to "Kräutergarten Pommerland",
        "KRI" to "Kornelia Urkorn",
        "KUL" to "KULAU",
        "LAC" to "LACOA",
        "LAG" to "Landgarten",
        "LAN" to "Landkrone",
        "LBI" to "La Bio-Idea",
        "LCJ" to "Little Cow Jersey",
        "LEA" to "LemonAid",
        "LEB" to "Lebensbaum",
        "LGP" to "Landgut Pretschen",
        "LHQ" to "St. Leonhards Quelle",
        "LHW" to "Lebenshilfe Werkstätten",
        "LIB" to "Lichtblick - Bodo Kracht",
        "LIM" to "Lima",
        "LIT" to "Lilith",
        "LMA" to "Le Maître",
        "LNA" to "Linea Natura",
        "LOB" to "Lobetaler Bio",
        "LOG" to "Logona",
        "LOV" to "Lovechock B.V.",
        "LTA" to "Lauretana",
        "LUB" to "Lubs Fruchtriegel",
        "LUI" to "Luisenhof",
        "MAN" to "Mani Bläuel",
        "MAR" to "Marschland",
        "MÄR" to "Märkisches Landbrot",
        "MBI" to "Molkerei Biedermann",
        "MBW" to "Malikmint",
        "MIR" to "MIR.Bio",
        "MIV" to "Minor Figures",
        "MKI" to "Monki",
        "MKS" to "Münchner Kind‘l Senf",
        "MOI" to "Moin",
        "MOM" to "MostManufaktur Havelland",
        "MOR" to "Morgenland",
        "MOU" to "Mount Hagen",
        "MRH" to "Marienhöhe",
        "MZO" to "Monte Ziego",
        "NAG" to "Nagel Tofu",
        "NAL" to "Naturli",
        "NAN" to "Privatmolkerei Naarmann",
        "NAT" to "Naturata",
        "NBI" to "Nabio",
        "NCL" to "Natural Cool",
        "NCO" to "Natur Compagnie",
        "NER" to "NERO",
        "NEU" to "Neumarkter Lammsbräu",
        "NEV" to "Neovita",
        "NGG" to "Naturgenuss",
        "NHU" to "Hurtig",
        "NIE" to "NICE",
        "NKG" to "NKG",
        "NOL" to "Nohrlund",
        "NOU" to "NOU",
        "NOW" to "NOW - New Organic World",
        "NPG" to "Verpackung und ToGo-Zubehör",
        "NSF" to "Nordic Seafood",
        "NTM" to "Natumi",
        "ÖBW" to "Brodowin Ökodorf",
        "OEL" to "OEL Berlin",
        "ÖKL" to "Ökoland",
        "ÖKT" to "Ökotopia",
        "ÖKV" to "Ökovital",
        "ÖMA" to "ÖMA",
        "OMB" to "Ombar",
        "OTM" to "Ostmost Streuobstwiesen Manufaktur",
        "PAE" to "Paletas",
        "PAN" to "Pasta Nuova",
        "PAT" to "Partisan Vodka",
        "PBE" to "pack‘s bio ein",
        "PBY" to "Pur Plants",
        "PDM" to "Pan do Mar",
        "PED" to "Peaceful delicious",
        "PIJ" to "Pijökel",
        "PIN" to "Pinkus",
        "PKR" to "PFFF KRSCH",
        "PLA" to "Käserei Plangger",
        "POB" to "Poggioli",
        "PRV" to "Provamel",
        "PUF" to "PUR Die Biomanufaktur",
        "PUK" to "Pukka Herbs Ltd",
        "PVB" to "Proviant Berlin",
        "QMS" to "Quartiermeister",
        "QUA" to "Quarkwerk",
        "QUE" to "Dr. Quendt KG",
        "RAB" to "Rabenhorst",
        "RAC" to "Rachelli Italia s.r.l.",
        "RAW" to "Rawito",
        "RBB" to "Riedenburger Brauhaus",
        "RDA" to "Rigoni di Asiago",
        "REA" to "Bioreal",
        "RIE" to "Peter Riegel Weinimport",
        "RIS" to "Ristic",
        "RIT" to "De Rit",
        "RIW" to "Regional ist 1. Wahl",
        "ROG" to "Roggenburger Bio",
        "ROS" to "Rosengarten",
        "RPQ" to "Rheinsberger Preussenquelle",
        "RTS" to "Ratschillers Bäckerei",
        "RÜB" to "Rübbelberg",
        "RUN" to "Runge",
        "RUP" to "Rupp",
        "RUT" to "Rusticana",
        "RWB" to "Raw Bite",
        "SAC" to "Sanchon",
        "SBG" to "Schrozberger Milchbauern",
        "SDL" to "Schedel",
        "SDY" to "StorkClub",
        "SEC" to "Weingut Seck",
        "SEL" to "La Selva",
        "SFF" to "Sunflower Family",
        "SFH" to "Schnitzer GLUTENFREIHEIT",
        "SFR" to "Saucenfritz",
        "SFS" to "Stralsunder",
        "SMI" to "Savon du Midi",
        "SNA" to "Soyana",
        "SNT" to "Sonett",
        "SNW" to "Sonnenweg",
        "SOB" to "SOBO Naturkost",
        "SÖB" to "Söbbeke",
        "SOD" to "Sodasan",
        "SOF" to "SOTO",
        "SOJ" to "Sojade",
        "SOL" to "soluxfire",
        "SOM" to "Sommer & Co.",
        "SPG" to "Grote & Co.",
        "SPI" to "Spielberger",
        "SSB" to "Senst Biofrucht",
        "STN" to "Sonnentor",
        "STÖ" to "Störtebeker",
        "SWB" to "Saumweber",
        "SWL" to "Swema Gemüsebrühe",
        "SWY" to "Swaytis",
        "SYR" to "Syring-Feinkost",
        "TAH" to "TranzAlpine",
        "TAI" to "Taifun",
        "TAR" to "Tarpa",
        "TER" to "Terrasana",
        "TFO" to "Trafo",
        "THO" to "Thönes Natur",
        "THT" to "The Beauty Organic Gin",
        "THV" to "The vegan cow",
        "TLA" to "Tlaxcalli",
        "TMA" to "tempehmanufaktur",
        "TMJ" to "Thise Mejeri",
        "TNH" to "Terra Naturkost",
        "TOU" to "Tofubar",
        "UBM" to "Upländer Bauern Molkerei",
        "VAV" to "Vallée-Verte",
        "VAX" to "Valdigrano",
        "VBB" to "VeneZero",
        "VCD" to "Verrano",
        "VEG" to "VitaVegan",
        "VEZ" to "Vegeatal",
        "VFK" to "veggi filata",
        "VGT" to "Vegetus",
        "VGY" to "Veggyness",
        "VII" to "VICI",
        "VLV" to "VivoLoVin",
        "VNI" to "Vivani",
        "VOE" to "Voelkel",
        "WAY" to "Waysa",
        "WCC" to "Susanne Wild",
        "WCK" to "Weck",
        "WCO" to "Wild & Coco",
        "WCT" to "Wacha Wodka",
        "WDG" to "Witty`s",
        "WED" to "Werder Feinkost",
        "WEK" to "Wein- und Sektgut Matthias Keth",
        "WER" to "Werz",
        "WHE" to "WHEATY",
        "WIB" to "Wilmersburger",
        "WIK" to "Wikana",
        "WMF" to "Weißenhorner Milch Manufaktur",
        "WOC" to "Wild Ocean",
        "WOS" to "Wostok",
        "WÜM" to "Wünsch-dir-Mahl",
        "XBE" to "Eiermacher",
        "YAK" to "Yakso",
        "YOG" to "Yogi Tee",
        "YOU" to "Youkon",
        "ZEL" to "Zellertaler Keller",
        "ZFK" to "Züger Frischkäse",
        "ZOR" to "zotrine",
        "ZWE" to "Zwergenwiese Naturkost",
        "ZZK" to "zickzack Kollektiv",
    )

    /**
     * Look up a producer name, tolerating the lower-case codes Terra uses for
     * some suppliers.
     *
     * A handful of entries in Terra's list are placeholders whose "name" is just
     * the code again (FDM, NKG, ÖMA …). Those tell a customer nothing, so they
     * are treated as unknown.
     */
    fun producerFor(code: String): String? {
        val trimmed = code.trim()
        if (trimmed.isEmpty()) return null
        val name = producers[trimmed] ?: producers[trimmed.uppercase()] ?: return null
        return name.takeIf { !it.equals(trimmed, ignoreCase = true) }
    }

    /** Look up a country, or null for a blank or unknown code. */
    fun countryFor(code: String): Country? {
        val trimmed = code.trim()
        if (trimmed.isEmpty()) return null
        return countries[trimmed] ?: countries[trimmed.uppercase()]
    }
}
