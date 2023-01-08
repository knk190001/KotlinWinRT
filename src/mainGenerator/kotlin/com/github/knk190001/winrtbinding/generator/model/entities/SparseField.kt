package com.github.knk190001.winrtbinding.generator.model.entities

data class SparseField(
    val name: String,
    val index: Int,
    val type: SparseTypeReference
)
