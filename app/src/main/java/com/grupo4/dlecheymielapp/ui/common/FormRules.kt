package com.grupo4.dlecheymielapp.ui.common

/** The backend rule for a phone number: E.164, a plus sign and 8 to 15 digits, the first one non-zero. */
private val E164_PHONE = Regex("""\+[1-9]\d{7,14}""")

const val PROVIDER_PHONE_FORMAT_MESSAGE = "Use el formato +51987654321"

/** True when [phone], once trimmed, is an E.164 number. Kept pure so the form and its tests share one rule. */
fun isValidProviderPhone(phone: String): Boolean = E164_PHONE.matches(phone.trim())
