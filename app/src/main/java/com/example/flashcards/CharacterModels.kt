package com.example.flashcards

data class DrawingPoint(val x: Float, val y: Float)
data class DrawingStroke(val points: List<DrawingPoint>)

data class CharacterData(
    val name: String,
    val strokes: List<DrawingStroke>,
    val checkStrokeOrder: Boolean,
    val checkStrokeDirection: Boolean = false,
    val strokeWidth: Float = 10f
)

data class CharacterDeck(
    val name: String,
    val characters: List<CharacterData>
)
