package com.example.scanner.model

data class BusinessCard(
    val fullName: String,
    val title: String,
    val phone: String,
    val email: String,
    val website: String,
    val address: String,
    val services: List<String>,
    val qrValue: String,
    val logoFileName: String?,
    val primaryColor: Int,
    val accentColor: Int,
    val template: CardTemplate = CardTemplate.QUANTUM,
    val fontScale: Float = 1f,
    val blankBack: Boolean = false,
)

data class ColorPreset(val primary: Int, val accent: Int) {
    companion object {
        val PRESETS = listOf(
            ColorPreset(0xFF16324F.toInt(), 0xFFD4A017.toInt()), // Quantum navy / gold
            ColorPreset(0xFF16324F.toInt(), 0xFF2A9D8F.toInt()), // Navy / teal
            ColorPreset(0xFF1F2937.toInt(), 0xFFB45309.toInt()), // Charcoal / amber
            ColorPreset(0xFF0F5132.toInt(), 0xFFD4A017.toInt()), // Forest / gold
            ColorPreset(0xFF7C2D12.toInt(), 0xFFEAB308.toInt()), // Brick / yellow
            ColorPreset(0xFF1E293B.toInt(), 0xFF3B82F6.toInt()), // Slate / blue
        )
    }
}
