package com.together.newverse.data.parser

import com.together.newverse.data.parser.BnnCodeTables.CertificationKind

/**
 * Turns the coded fields of a BNN price-list row into the customer-facing
 * description shown on the product detail screen.
 *
 * The text answers what a buyer wants to know — where the food was grown, how
 * it was certified and who grew it. Trade and logistics details that only
 * concern the seller (Gebinde, Handelsklasse, article numbers) are deliberately
 * left out.
 *
 * Unknown codes are omitted rather than printed raw, so a description is always
 * either correct or shorter, never confusing.
 */
class ProductDescriptionBuilder {

    /**
     * Build the description for one product.
     *
     * @param bnnDetail The BNN detail field, usually blank but occasionally
     *   carrying genuinely useful composition info ("Alfalfa + Roter Rettich").
     * @param originCode Country code, or `REG` for Terra's own region.
     * @param certificationCode BNN identification code (IK).
     * @param producerCode Producer / brand code.
     * @return A German description, or an empty string if nothing is known.
     */
    fun build(
        bnnDetail: String = "",
        originCode: String = "",
        certificationCode: String = "",
        producerCode: String = ""
    ): String {
        val sentences = mutableListOf<String>()

        cleanDetail(bnnDetail)?.let { sentences.add(it) }
        originSentence(originCode, certificationCode)?.let { sentences.add(it) }
        BnnCodeTables.producerFor(producerCode)?.let { sentences.add("Erzeuger: $it.") }

        return sentences.joinToString(" ")
    }

    /**
     * Normalise the BNN detail field into a sentence. The field is written for
     * a printed price list, so entries arrive wrapped in dashes
     * ("- extra kleine Sortierung -") and without a full stop.
     */
    private fun cleanDetail(detail: String): String? {
        val cleaned = detail.trim().trim('-').trim()
        if (cleaned.isEmpty()) return null
        return if (cleaned.endsWith(".")) cleaned else "$cleaned."
    }

    /**
     * The sentence carrying origin and certification. These are combined
     * because a buyer reads them as one statement ("grown in Spain to Bioland
     * standards") and because either one alone would be a very thin sentence.
     */
    private fun originSentence(originCode: String, certificationCode: String): String? {
        val certification = BnnCodeTables.certificationFor(certificationCode)
        val isRegional = originCode.trim().uppercase() == BnnCodeTables.ORIGIN_REGIONAL
        val country = if (isRegional) null else BnnCodeTables.countryFor(originCode)

        val origin = when {
            isRegional -> BnnCodeTables.REGIONAL_ORIGIN
            country != null -> "Angebaut in ${country.afterIn}"
            else -> null
        }

        return when {
            origin != null -> origin + (certification?.let { clause(it) } ?: "") + "."
            certification != null -> standalone(certification)?.plus(".")
            else -> null
        }
    }

    /**
     * The certification as a clause appended to an origin phrase. Both origin
     * phrasings already say the food was grown, so the clause never repeats
     * that — "Angebaut in Deutschland nach Bioland-Richtlinien", not "Angebaut
     * in Deutschland, angebaut nach Bioland-Richtlinien".
     */
    private fun clause(certification: BnnCodeTables.Certification): String? =
        when (certification.kind) {
            CertificationKind.ASSOCIATION -> " nach ${certification.label}-Richtlinien"
            CertificationKind.EU_ORGANIC -> ", zertifiziert nach ${certification.label}"
            CertificationKind.IFOAM -> ", zertifiziert nach ${certification.label}-Standard"
            CertificationKind.IN_CONVERSION -> ", noch in der Umstellung auf ökologischen Landbau"
            // Not an organic claim — saying nothing beats implying one.
            CertificationKind.NONE -> null
        }

    /** The certification as a sentence of its own, for rows with no usable origin. */
    private fun standalone(certification: BnnCodeTables.Certification): String? =
        when (certification.kind) {
            CertificationKind.ASSOCIATION -> "Angebaut nach ${certification.label}-Richtlinien"
            CertificationKind.EU_ORGANIC -> "Zertifiziert nach ${certification.label}"
            CertificationKind.IFOAM -> "Zertifiziert nach ${certification.label}-Standard"
            CertificationKind.IN_CONVERSION -> "Noch in der Umstellung auf ökologischen Landbau"
            CertificationKind.NONE -> null
        }
}
