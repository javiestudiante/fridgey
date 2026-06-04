package ule.jescuj00.fridgey.domain.usecase

import ule.jescuj00.fridgey.domain.model.Categoria
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Tags below mirror real Open Food Facts `categories_tags` (generic → specific
 * order). The mapper must read them as a SUGGESTION and pick the right Fridgey
 * [Categoria].
 */
class MapOffCategoryUseCaseTest {

    private val map = MapOffCategoryUseCase()

    private fun assertCat(expected: Categoria, tags: List<String>?) =
        assertEquals(expected, map(tags), "tags=$tags")

    // ---- the four cases the brief asks for --------------------------------

    @Test fun dairy_yogurt() = assertCat(
        Categoria.LACTEOS,
        listOf("en:dairies", "en:fermented-foods", "en:fermented-milk-products", "en:yogurts"),
    )

    @Test fun beverage_cola() = assertCat(
        Categoria.BEBIDAS,
        listOf("en:beverages", "en:carbonated-drinks", "en:sodas", "en:colas"),
    )

    @Test fun fish_canned_tuna() = assertCat(
        Categoria.PESCADOS,
        listOf(
            "en:seafood", "en:fishes", "en:canned-foods",
            "en:canned-fishes", "en:tunas", "en:canned-tunas",
        ),
    )

    @Test fun no_keyword_falls_back_to_otros() = assertCat(
        Categoria.OTROS,
        listOf("en:condiments", "en:sauces", "en:mustards"),
    )

    // ---- specificity / priority -------------------------------------------

    /** Orange juice is a BEVERAGE, not a fruit: "juice" must win over "orange". */
    @Test fun orange_juice_is_beverage_not_fruit() = assertCat(
        Categoria.BEBIDAS,
        listOf(
            "en:plant-based-foods-and-beverages", "en:beverages",
            "en:fruit-based-beverages", "en:fruit-juices", "en:orange-juices",
        ),
    )

    /** Plain milk via the `en:milks` token (not just `en:dairies`). */
    @Test fun whole_milk_is_dairy() = assertCat(
        Categoria.LACTEOS,
        listOf("en:dairies", "en:milks", "en:whole-milks"),
    )

    @Test fun fresh_fruit_is_fruta() = assertCat(
        Categoria.FRUTAS,
        listOf("en:plant-based-foods", "en:fruits", "en:apples"),
    )

    @Test fun bread_is_panaderia() = assertCat(
        Categoria.PANADERIA,
        listOf("en:plant-based-foods", "en:breads", "en:baguettes"),
    )

    // ---- frozen: food type beats storage state ----------------------------

    /** A frozen vegetable must land on VERDURAS, not CONGELADOS. */
    @Test fun frozen_vegetables_prefer_food_type() = assertCat(
        Categoria.VERDURAS,
        listOf("en:frozen-foods", "en:vegetables", "en:frozen-vegetables"),
    )

    /** Pure frozen item with no recognisable food type → CONGELADOS. */
    @Test fun frozen_only_falls_back_to_congelados() = assertCat(
        Categoria.CONGELADOS,
        listOf("en:frozen-foods", "en:frozen-desserts"),
    )

    // ---- substring traps avoided by token matching ------------------------

    /** "water" inside "watermelons" must NOT make it a beverage. */
    @Test fun watermelon_is_not_a_beverage() = assertCat(
        Categoria.FRUTAS,
        listOf("en:plant-based-foods", "en:fruits", "en:melons", "en:watermelons"),
    )

    // ---- edge cases --------------------------------------------------------

    @Test fun empty_list_is_otros() = assertCat(Categoria.OTROS, emptyList())
    @Test fun null_is_otros() = assertCat(Categoria.OTROS, null)
}
