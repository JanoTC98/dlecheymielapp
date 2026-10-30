package com.grupo4.dlecheymielapp.data.admin

import com.grupo4.dlecheymielapp.core.network.InMemoryCookieStorage
import com.grupo4.dlecheymielapp.core.network.NetworkModule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Item shapes from the backend's CommercialSnapshot (payments/persistence) and CartDtos.ComponentSnapshot. */
class AdminOrderPreparationTest {
    private val json = NetworkModule("https://example.test/", InMemoryCookieStorage(), debugLogging = false).json

    private fun item(body: String): AdminOrderItem = json.decodeFromString(AdminOrderItem.serializer(), body)

    private fun pack(components: String) = item(
        """
        {"lineNumber": 1, "itemType": "PACK", "name": "Pack familiar", "quantity": 1, "subtotal": 30.0,
         "commercialSnapshot": {"itemType": "PACK", "name": "Pack familiar", "packModelId": 3, "components": [$components]}}
        """,
    )

    @Test
    fun aPackListsEachComponentWithItsQuantityAndName() {
        val line = pack(
            """
            {"sellableUnitId": 21, "productName": "Alfajor de maicena", "sku": "ALF-01", "requestedQuantity": 2, "configuration": null, "summary": ""},
            {"sellableUnitId": 22, "productName": "Torta tres leches", "sku": "TOR-TRES-01", "requestedQuantity": 1, "configuration": null, "summary": ""}
            """,
        )

        assertEquals(listOf("2 × Alfajor de maicena", "1 × Torta tres leches"), line.preparationLines())
    }

    @Test
    fun aComboListsItsComponentsWithTheirQuantities() {
        val line = item(
            """
            {"lineNumber": 2, "itemType": "COMBO", "name": "Combo desayuno", "quantity": 2, "subtotal": 40.0,
             "commercialSnapshot": {"itemType": "COMBO", "name": "Combo desayuno", "comboId": 7, "components": [
               {"sellableUnitId": 31, "productName": "Café americano", "requestedQuantity": 1, "configuration": null, "summary": ""},
               {"sellableUnitId": 32, "productName": "Croissant", "requestedQuantity": 3, "configuration": null, "summary": ""}
             ]}}
            """,
        )

        assertEquals(listOf("1 × Café americano", "3 × Croissant"), line.preparationLines())
    }

    @Test
    fun aCustomizedComponentGivesItsReadableGroupAndOptionLines() {
        // The component summary is the backend's own "Grupo: opción" text; the item configuration carries only ids.
        val line = pack(
            """
            {"sellableUnitId": 21, "productName": "Torta tres leches", "requestedQuantity": 1,
             "configuration": {"general": {"group_4": "option_9", "group_5": ["option_11", "option_12"]}}, "summary": "Relleno: Manjar; Cobertura: Chocolate, Fresa"}
            """,
        )

        val lines = line.preparationLines()
        assertTrue(lines.contains("Relleno: Manjar"))
        assertTrue(lines.contains("Cobertura: Chocolate, Fresa"))
        assertTrue(lines.none { it.contains("group_") || it.contains("option_") })
    }

    @Test
    fun aProductConfigurationThatHoldsOnlyIdsShowsNoLinesAndNoRawIds() {
        val line = item(
            """
            {"lineNumber": 1, "itemType": "PRODUCT", "name": "Torta personalizada", "quantity": 1, "subtotal": 55.0,
             "commercialSnapshot": {"itemType": "PRODUCT", "sku": "TOR-PERS-01", "name": "Torta personalizada", "components": [],
               "configuration": {"general": {"group_4": "option_9"}}, "customizationSchemaVersion": 1}}
            """,
        )

        assertEquals(emptyList<String>(), line.preparationLines())
    }

    @Test
    fun anUnknownOrMalformedConfigurationYieldsNoLinesAndDoesNotThrow() {
        listOf("\"oops\"", "[1, 2]", "{\"general\": \"x\", \"layers\": 5}", "null", "{\"groups\": [{\"label\": null}]}").forEach { configuration ->
            val line = item(
                """
                {"lineNumber": 1, "itemType": "PRODUCT", "name": "Torta", "quantity": 1, "subtotal": 55.0,
                 "commercialSnapshot": {"itemType": "PRODUCT", "components": [], "configuration": $configuration}}
                """,
            )

            assertEquals(configuration, emptyList<String>(), line.preparationLines())
        }
    }

    @Test
    fun aPresentationNameIsShownWhenPresent() {
        val line = item(
            """
            {"lineNumber": 1, "itemType": "PRODUCT", "name": "Rol de canela", "quantity": 1, "subtotal": 6.0,
             "commercialSnapshot": {"itemType": "PRODUCT", "presentationId": 5, "presentationName": "Caja x6", "components": []}}
            """,
        )

        assertEquals(listOf("Presentación: Caja x6"), line.preparationLines())
    }

    @Test
    fun aPlainProductFromTheBackendSampleAddsNoExtraLines() {
        // Shape of admin_order_6.json, line 1: no presentation, no components, no configuration.
        val line = item(
            """
            {"lineNumber": 1, "itemType": "PRODUCT", "name": "Rol de canela", "sku": "PAN-CANE-01", "quantity": 2, "subtotal": 12.0, "currency": "PEN",
             "commercialSnapshot": {"sku": "PAN-CANE-01", "name": "Rol de canela", "price": {"currency": "PEN", "finalPrice": 6, "normalPrice": 6, "discountAmount": 0, "discountPercentage": 0},
               "comboId": null, "itemType": "PRODUCT", "quantity": 2, "subtotal": 12.0, "packTotal": null, "productId": 18, "cartItemId": 17, "components": [],
               "packModelId": null, "productName": "Rol de canela", "packCapacity": null, "packDiscount": null, "packSubtotal": null, "configuration": null,
               "pricingPolicy": null, "presentationId": null, "sellableUnitId": 18, "packDiscountType": null, "presentationName": null, "packDiscountValue": null,
               "customizationSchemaVersion": null}}
            """,
        )

        assertEquals(emptyList<String>(), line.preparationLines())
        assertEquals(2, line.quantity)
        assertEquals(12.0, line.subtotal, 0.0)
    }
}
