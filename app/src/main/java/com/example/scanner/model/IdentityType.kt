package com.example.scanner.model

enum class IdentityType {
    DRIVERS_LICENSE,
    STATE_ID,
    PASSPORT,
    OTHER_CARD,
    ;

    val isPassport: Boolean get() = this == PASSPORT

    val isCard: Boolean get() = !isPassport

    fun defaultTitleLabel(): String = when (this) {
        DRIVERS_LICENSE -> "Driver License"
        STATE_ID -> "State ID"
        PASSPORT -> "Passport"
        OTHER_CARD -> "ID Card"
    }
}
