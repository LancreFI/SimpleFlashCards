package com.example.flashcards

import com.google.gson.annotations.SerializedName

data class WordList(
    @SerializedName("source language") val source: String?,
    @SerializedName("destination language") val dest: String?,
    val words: Map<String, String>?
)

data class Card(val front: String, val back: String)