package com.github.knk190001.winrtbinding.generator.model.entities

import com.beust.klaxon.Json

data class SparseEnum(
    @Json("Name")
    val name:String,
    @Json("Namespace")
    val namespace: String,
    @Json("Values")
    val values: Map<String,Int>
): SparseEntity("Enum")
