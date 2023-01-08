package com.github.knk190001.winrtbinding.generator.model.entities

data class SparseStruct(
    val name: String,
    val namespace: String,
    val fields: List<SparseField>
): SparseEntity("Struct")
