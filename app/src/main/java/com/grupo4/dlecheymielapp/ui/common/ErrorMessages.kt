package com.grupo4.dlecheymielapp.ui.common

import com.grupo4.dlecheymielapp.core.network.ApiException
import com.grupo4.dlecheymielapp.core.network.NetworkUnavailableException

/** Spanish text for field operations. Codes come from section 6 of docs/MAPA_DE_PROCESOS.md; wording mirrors the web. */
fun Throwable.toHandoffMessage(): String = when {
    this is ApiException -> when (code) {
        "HANDOFF_CODE_INVALID" -> "El código no es válido. Pídale al cliente el código de 6 caracteres de su correo."
        "HANDOFF_STATE_INVALID" -> "El pedido todavía no está en un estado que permita la entrega."
        "HANDOFF_PICKUP_EXPIRED" -> "El plazo de recojo de 24 horas venció; este pedido ya no se puede entregar."
        "HANDOFF_ALREADY_COMPLETED" -> "Este pedido ya fue entregado."
        "PAYMENT_REQUIRED" -> "Este pedido es de pago contra entrega: se cierra solo cuando el pago esté confirmado."
        "HANDOFF_CODE_REQUIRED" -> "Este pedido es de pago contra entrega: ciérrelo con el código de entrega del cliente."
        "HANDOFF_CODE_UNAVAILABLE" -> "Este pedido es antiguo y no tiene código de entrega. Avise a la administración."
        else -> toGenericMessage()
    }
    else -> toGenericMessage()
}

fun Throwable.toIncidentMessage(): String = when {
    this is ApiException && code == "INCIDENT_TEXT_INVALID" -> "Describa la incidencia (máximo 2000 caracteres)."
    this is ApiException && code == "ORDER_NOT_OPERATIONAL" -> "El pedido ya terminó; no se pueden registrar más incidencias."
    else -> toGenericMessage()
}

fun Throwable.toPaymentLinkMessage(): String = when {
    this is ApiException && code == "PAYMENT_LINK_NOT_YET" -> "El enlace de pago se habilita cuando empieza la franja del pedido."
    this is ApiException && (code == "PAYMENT_LINK_DISABLED" || status == 503) -> "Los enlaces de pago no están configurados. Avise a la administración."
    this is ApiException && code == "PAYMENT_ALREADY_PAID" -> "El pedido ya está pagado."
    this is ApiException && code == "PAYMENT_LINK_UNAVAILABLE" -> "Este pedido ya no se puede pagar."
    this is ApiException && (status == 403 || status == 404) -> "No tiene acceso al pago de este pedido."
    this is ApiException && status == 409 -> "Este pedido ya no admite un enlace de pago."
    else -> toGenericMessage()
}

fun Throwable.toGenericMessage(): String = when (this) {
    is NetworkUnavailableException -> "No hay conexión con el servidor. Revise su red e inténtelo otra vez."
    is ApiException -> when {
        status == 401 -> "Su sesión venció. Ingrese de nuevo."
        status == 403 && code == "HANDLER_TYPE_MISMATCH" -> "Esta vista no corresponde a su tipo de encargado."
        status == 403 -> "No tiene permiso para esta acción."
        status == 404 -> "El pedido ya no está asignado a su cuenta."
        code == "ORDER_STATE_CHANGED" -> "El pedido cambió de estado. Se actualizó la lista."
        status == 429 -> "Demasiados intentos. Espere un momento."
        status in 500..599 -> "El servidor tuvo un problema. Inténtelo de nuevo en un momento."
        else -> message ?: "No se pudo completar la operación."
    }
    else -> "No se pudo completar la operación."
}

/** wa.me link; 9-digit Peruvian numbers get the 51 prefix. Null when there is no usable number. */
fun whatsappUrl(phone: String?): String? {
    val digits = (phone ?: "").filter { it.isDigit() }
    if (digits.length < 7) return null
    return "https://wa.me/${if (digits.length == 9) "51$digits" else digits}"
}

/** Admin actions; falls back to the generic text for anything not listed. */
fun Throwable.toAdminMessage(): String {
    if (this !is ApiException) return toGenericMessage()
    return when (code) {
        "ORDER_STATE_CHANGED" -> "El pedido cambió de estado mientras lo miraba. Se actualizó el detalle."
        "ORDER_TRANSITION_INVALID" -> "Ese cambio de estado no está permitido para este pedido."
        "ORDER_ALREADY_TERMINAL" -> "El pedido ya terminó; esta acción ya no aplica."
        "INCIDENT_NOT_OPEN" -> "La incidencia ya fue resuelta. Se actualizó el detalle."
        "RESCHEDULE_SLOT_UNAVAILABLE" -> "Esa franja ya no tiene cupo. Elija otra."
        "RESCHEDULE_SLOT_INVALID" -> "Elija una franja válida."
        "STOCK_ALREADY_RESTORED" -> "El stock de este pedido ya fue repuesto; ya no se puede reabrir."
        "PAYMENT_REQUIRED" -> "El pedido es de pago contra entrega y todavía no está pagado."
        "HANDLER_SLOT_BUSY" -> "Ese encargado ya tiene otra entrega que se cruza con esta franja."
        "HANDLER_TYPE_MISMATCH" -> "Ese encargado no corresponde al tipo de este pedido."
        "ORDER_HANDLER_INVALID" -> "Ese encargado ya no está disponible. Elija otro."
        "ORDER_ASSIGNMENT_USE_GROUP" -> "Los pedidos de recojo se asignan por grupo, no por pedido."
        "ORDER_ASSIGNMENT_STATE_INVALID" -> "El pedido ya no está en un estado que permita asignarlo."
        "ORDER_ASSIGNMENT_MODE_INVALID" -> "Este pedido no admite esa asignación."
        "ORDER_NOT_FOUND", "GROUP_NOT_FOUND" -> "No se encontró el pedido o grupo. Actualice la lista."
        "NOTE_INVALID" -> "La nota debe tener entre 1 y 2000 caracteres."
        else -> if (status == 404) "No se encontró el registro. Actualice la lista." else toGenericMessage()
    }
}

/** Delivery / Operador preparation steps and provider data. */
fun Throwable.toActionMessage(): String = when {
    this is ApiException && code == "ORDER_STATE_CHANGED" -> "El pedido cambió de estado. Se actualizó el detalle."
    this is ApiException && code == "ORDER_DELIVERY_TRANSITION_INVALID" -> "Ese paso no está permitido para su rol."
    this is ApiException && status == 400 -> "Revise los datos: nombre y teléfono son obligatorios."
    else -> toGenericMessage()
}
