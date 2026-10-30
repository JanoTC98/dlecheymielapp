package com.grupo4.dlecheymielapp.data.admin

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The same table as `incidentRules.ts` on the web (docs/development/REGLAS_INCIDENCIAS_Y_REEMBOLSOS.md, section 15). */
class IncidentRulesTest {
    private fun incident(
        id: Long = 1,
        cause: IncidentCause = IncidentCause.POR_DETERMINAR,
        level: Int = 0,
        status: AdminIncidentStatus = AdminIncidentStatus.OPEN,
    ) = AdminIncident(id = id, orderId = 9, status = status, cause = cause, escalationLevel = level)

    @Test
    fun aPaidOrderTheBusinessFailedCanBeRefundedAndNeverWrittenOff() {
        val open = incident(cause = IncidentCause.NEGOCIO)
        val rules = incidentRules(open, listOf(open), paid = true)
        assertTrue(rules.canRefund)
        assertTrue(rules.lossBlocked)
        assertFalse(rules.rescheduleBlocked)
    }

    @Test
    fun aPaidOrderTheCustomerFailedCanBeWrittenOffButNotRefunded() {
        val open = incident(cause = IncidentCause.CLIENTE, level = 3)
        val rules = incidentRules(open, listOf(open), paid = true)
        assertFalse(rules.canRefund)
        assertFalse(rules.lossBlocked)
        assertFalse(rules.rescheduleBlocked) // the 7-day window only closes when the business is at fault or undecided
        assertFalse(rules.overdue)
    }

    @Test
    fun anUndecidedCauseBlocksBothTheRefundAndTheWriteOff() {
        val open = incident(cause = IncidentCause.POR_DETERMINAR)
        val rules = incidentRules(open, listOf(open), paid = true)
        assertFalse(rules.canRefund)
        assertTrue(rules.lossBlocked)
    }

    @Test
    fun aBusinessFaultRescheduledOnceCannotBeRescheduledAgain() {
        val earlier = incident(id = 1, cause = IncidentCause.NEGOCIO, status = AdminIncidentStatus.RESOLVED_REASSIGNED)
        val open = incident(id = 2, cause = IncidentCause.NEGOCIO)
        val rules = incidentRules(open, listOf(open, earlier), paid = true)
        assertTrue(rules.rescheduleBlocked)
        assertEquals(RescheduleBlock.LIMIT, rules.rescheduleBlockedBy)
    }

    @Test
    fun sevenDaysAfterTheMissedSlotOnlyTheRefundRemains() {
        val open = incident(cause = IncidentCause.NEGOCIO, level = 3)
        val rules = incidentRules(open, listOf(open), paid = true)
        assertTrue(rules.rescheduleBlocked)
        assertEquals(RescheduleBlock.WINDOW, rules.rescheduleBlockedBy)
        assertTrue(rules.refundRecommended)
    }

    @Test
    fun theRemindersMarkOverdueAt24HoursAndRefundRecommendedAt72() {
        val day = incident(cause = IncidentCause.NEGOCIO, level = 1)
        assertTrue(incidentRules(day, listOf(day), paid = true).overdue)
        assertFalse(incidentRules(day, listOf(day), paid = true).refundRecommended)
        val three = incident(cause = IncidentCause.NEGOCIO, level = 2)
        assertTrue(incidentRules(three, listOf(three), paid = true).refundRecommended)
    }

    @Test
    fun anUnpaidOrderKeepsEveryOptionAndNothingMoneyRelated() {
        val open = incident(cause = IncidentCause.NEGOCIO, level = 3)
        val rules = incidentRules(open, listOf(open), paid = false)
        assertFalse(rules.canRefund)
        assertFalse(rules.lossBlocked)
        assertFalse(rules.rescheduleBlocked)
        assertNull(rules.rescheduleBlockedBy)
        assertFalse(rules.overdue)
    }

    @Test
    fun anUnknownCauseFromANewerBackendIsTreatedAsUndecided() {
        val open = incident(cause = IncidentCause.UNKNOWN)
        assertEquals(IncidentCause.POR_DETERMINAR, incidentRules(open, listOf(open), paid = true).cause)
    }
}
