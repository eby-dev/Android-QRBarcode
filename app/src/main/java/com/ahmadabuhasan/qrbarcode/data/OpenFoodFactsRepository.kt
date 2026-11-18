package com.ahmadabuhasan.qrbarcode.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import com.ahmadabuhasan.qrbarcode.BuildConfig
import java.net.HttpURLConnection
import java.net.URL

data class FoodProduct(
    val name: String?,
    val brands: String?,
    val quantity: String?,
    val ingredients: String?,
    val allergens: String?,
    val nutritionSummary: String?,
)

sealed interface ProductLookupResult {
    data class Found(val product: FoodProduct) : ProductLookupResult
    data object NotFound : ProductLookupResult
    data object Failed : ProductLookupResult
}

object OpenFoodFactsRepository {
    suspend fun findProduct(barcode: String): ProductLookupResult = withContext(Dispatchers.IO) {
        var connection: HttpURLConnection? = null
        try {
            connection = URL(
                "https://world.openfoodfacts.org/api/v3/product/$barcode" +
                    "?fields=product_name,brands,quantity,ingredients_text,allergens,nutriments"
            ).openConnection() as HttpURLConnection
            connection.connectTimeout = 8_000
            connection.readTimeout = 8_000
            connection.setRequestProperty(
                "User-Agent",
                "QRBarcode/${BuildConfig.VERSION_NAME} (https://github.com/eby-dev/Android-QRBarcode)"
            )

            if (connection.responseCode == HttpURLConnection.HTTP_NOT_FOUND) {
                return@withContext ProductLookupResult.NotFound
            }
            if (connection.responseCode !in 200..299) return@withContext ProductLookupResult.Failed
            val response = connection.inputStream.bufferedReader().use { it.readText() }
            val root = JSONObject(response)
            val product = root.optJSONObject("product") ?: return@withContext ProductLookupResult.NotFound
            val nutriments = product.optJSONObject("nutriments")
            val nutrition = listOfNotNull(
                nutrient(nutriments, "energy-kcal_100g", "kcal"),
                nutrient(nutriments, "fat_100g", "g fat"),
                nutrient(nutriments, "carbohydrates_100g", "g carbs"),
                nutrient(nutriments, "proteins_100g", "g protein"),
                nutrient(nutriments, "salt_100g", "g salt"),
            ).joinToString(" · ").ifBlank { null }

            ProductLookupResult.Found(
                FoodProduct(
                    name = product.optString("product_name").cleanOrNull(),
                    brands = product.optString("brands").cleanOrNull(),
                    quantity = product.optString("quantity").cleanOrNull(),
                    ingredients = product.optString("ingredients_text").cleanOrNull(),
                    allergens = product.optString("allergens").cleanOrNull(),
                    nutritionSummary = nutrition,
                )
            )
        } catch (_: Exception) {
            ProductLookupResult.Failed
        } finally {
            connection?.disconnect()
        }
    }

    private fun nutrient(nutriments: JSONObject?, key: String, unit: String): String? {
        val value = nutriments?.optDouble(key, Double.NaN) ?: return null
        if (!value.isFinite()) return null
        val formatted = if (value % 1.0 == 0.0) value.toInt().toString() else "%.1f".format(value)
        return "$formatted $unit / 100 g"
    }

    private fun String?.cleanOrNull(): String? = this?.trim()?.takeIf { it.isNotEmpty() && it != "null" }
}
