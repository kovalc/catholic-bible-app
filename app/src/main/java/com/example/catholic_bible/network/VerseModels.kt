package com.example.catholic_bible.network

import com.google.gson.annotations.SerializedName

data class VerseResponse(
    val date: String,
    val book: String,
    val chapter: Int,
    val verse: Int,
    val text: String,
    @SerializedName("image_url")
    val imageUrl: String? = null
)