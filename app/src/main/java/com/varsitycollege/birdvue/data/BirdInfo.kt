package com.varsitycollege.birdvue.data

data class BirdInfo(
    val prompt: String ?= null,
    val answer: String ?= null,
    val cached: Boolean ?= null
)
