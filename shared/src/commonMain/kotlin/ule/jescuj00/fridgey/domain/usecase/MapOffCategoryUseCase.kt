package ule.jescuj00.fridgey.domain.usecase

import ule.jescuj00.fridgey.domain.model.Categoria

/**
 * Infers a Fridgey [Categoria] from an Open Food Facts product's
 * `categories_tags` — the `en:`-prefixed, hierarchical taxonomy that runs from
 * MOST GENERIC (first) to MOST SPECIFIC (last), e.g. for an orange juice:
 * `en:beverages` → … → `en:fruit-juices` → `en:orange-juices`.
 *
 * Pure and deterministic (no network, no platform deps) so it can be unit
 * tested directly.
 *
 * Strategy (two passes):
 *  1. FOOD TYPE — walk the tags from MOST SPECIFIC (end of the list) to most
 *     generic and, for each, test the food-type keyword dictionary in a fixed
 *     within-tag priority order (proteins → dairy → beverages → produce →
 *     bakery). Return the first category whose keyword matches a *token* of the
 *     tag, so a concrete tag (`en:yogurts`) wins over a broad one
 *     (`en:dairies`).
 *  2. STORAGE STATE — only if pass 1 found nothing, look for "frozen" and map
 *     to [Categoria.CONGELADOS]. This guarantees a frozen vegetable lands on
 *     VERDURAS (a real food type) rather than CONGELADOS (just a storage state).
 *  3. Nothing matched → [Categoria.OTROS].
 *
 * Token matching (with simple `+s`/`+es` plural handling) is used instead of
 * raw substring matching to avoid traps like "water" inside "watermelons" or
 * "bread" inside "breaded". Every keyword is therefore a single lowercase
 * token (no hyphens/spaces) — OFF tokenises compound tags on hyphens, e.g.
 * `en:semi-skimmed-milks` → ["semi", "skimmed", "milks"].
 *
 * The result is only a SUGGESTION; the user can change it in AddProducto.
 */
class MapOffCategoryUseCase {

    operator fun invoke(categoriesTags: List<String>?): Categoria {
        if (categoriesTags.isNullOrEmpty()) return Categoria.OTROS
        // Most specific tags are last → tokenise once, walk in reverse.
        val tokenisedSpecificFirst: List<List<String>> = categoriesTags
            .asReversed()
            .map { tag -> tag.lowercase().split(NON_ALNUM).filter { it.isNotBlank() } }
            .filter { it.isNotEmpty() }

        // Pass 1 — real food type wins (specific tag beats generic).
        for (tokens in tokenisedSpecificFirst) {
            for ((categoria, keywords) in FOOD_KEYWORDS) {
                if (keywords.any { kw -> tokens.any { it.matchesKeyword(kw) } }) {
                    return categoria
                }
            }
        }
        // Pass 2 — storage-state fallback, only when no food type matched.
        for (tokens in tokenisedSpecificFirst) {
            if (FROZEN_KEYWORDS.any { kw -> tokens.any { it.matchesKeyword(kw) } }) {
                return Categoria.CONGELADOS
            }
        }
        return Categoria.OTROS
    }

    /** Exact token match with naive `+s` / `+es` plural handling. */
    private fun String.matchesKeyword(kw: String): Boolean =
        this == kw || this == "${kw}s" || this == "${kw}es"

    private companion object {
        val NON_ALNUM = Regex("[^a-z0-9]+")

        // Order encodes WITHIN-TAG priority (first category to match a single
        // tag wins). Cross-tag specificity is handled by walking tags in
        // reverse. Keywords include English (the `en:` tags are English) plus
        // Spanish, and must be single tokens (no hyphens/spaces).
        val FOOD_KEYWORDS: List<Pair<Categoria, List<String>>> = listOf(
            Categoria.PESCADOS to listOf(
                "fish", "seafood", "salmon", "tuna", "shellfish", "cod", "anchovy",
                "sardine", "mackerel", "shrimp", "prawn",
                "pescado", "marisco", "atun", "bacalao", "merluza", "gamba", "sardina",
            ),
            Categoria.CARNES to listOf(
                "meat", "beef", "pork", "chicken", "poultry", "sausage", "ham", "bacon",
                "turkey", "veal", "lamb", "salami",
                "carne", "pollo", "cerdo", "ternera", "embutido", "jamon", "chorizo",
                "pavo", "salchicha",
            ),
            Categoria.LACTEOS to listOf(
                "dairy", "milk", "cheese", "yogurt", "yoghurt", "butter", "cream",
                "leche", "queso", "yogur", "mantequilla", "nata", "lacteo",
            ),
            Categoria.BEBIDAS to listOf(
                "beverage", "drink", "juice", "water", "soda", "cola", "wine", "beer",
                "lemonade", "tea", "coffee",
                "bebida", "zumo", "agua", "refresco", "vino", "cerveza", "limonada",
            ),
            Categoria.FRUTAS to listOf(
                "fruit", "berry", "berries", "apple", "banana", "orange", "grape",
                "fruta", "manzana", "platano", "naranja", "uva",
            ),
            Categoria.VERDURAS to listOf(
                "vegetable", "legume", "salad", "tomato", "potato", "onion", "carrot",
                "verdura", "hortaliza", "ensalada", "tomate", "patata", "legumbre",
                "cebolla", "zanahoria",
            ),
            Categoria.PANADERIA to listOf(
                "bread", "bakery", "pastry", "biscuit", "cereal", "cookie", "cake",
                "pan", "bolleria", "galleta", "reposteria", "pasteleria", "bizcocho",
                "cereales",
            ),
        )

        // Storage state, not a food type → matched only as a last resort.
        val FROZEN_KEYWORDS: List<String> = listOf("frozen", "congelado", "helado")
    }
}
