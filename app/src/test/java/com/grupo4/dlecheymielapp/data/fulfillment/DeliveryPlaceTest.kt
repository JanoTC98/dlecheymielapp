package com.grupo4.dlecheymielapp.data.fulfillment

import org.junit.Assert.assertEquals
import org.junit.Test

/** The address shown to the delivery person adds the district only when the address does not already end with it. */
class DeliveryPlaceTest {
    @Test
    fun anAddressThatAlreadyEndsWithTheDistrictIsNotRepeated() {
        assertEquals("Av. Los Alisos 123, Los Olivos", deliveryPlace("Av. Los Alisos 123, Los Olivos", "Los Olivos"))
    }

    @Test
    fun theComparisonIgnoresCaseSpacesAndTrailingPunctuation() {
        assertEquals("Av. Los Alisos 123, los olivos.", deliveryPlace("Av. Los Alisos 123, los olivos.", "Los Olivos "))
    }

    @Test
    fun anAddressWithoutTheDistrictGetsItAppended() {
        assertEquals("Av. Los Alisos 123, Los Olivos", deliveryPlace("Av. Los Alisos 123", "Los Olivos"))
    }

    @Test
    fun aStreetNameThatContainsTheDistrictKeepsEveryPart() {
        assertEquals("Calle Los Olivos 123, Los Olivos", deliveryPlace("Calle Los Olivos 123", "Los Olivos"))
        assertEquals("Jr. Santa Rosa de Lima, Lima", deliveryPlace("Jr. Santa Rosa de Lima", "Lima"))
    }

    @Test
    fun missingPiecesAreSkipped() {
        assertEquals("Av. Los Alisos 123", deliveryPlace("Av. Los Alisos 123", null))
        assertEquals("Los Olivos", deliveryPlace(null, "Los Olivos"))
        assertEquals("Los Olivos", deliveryPlace("  ", "Los Olivos"))
        assertEquals("", deliveryPlace(null, null))
    }
}
