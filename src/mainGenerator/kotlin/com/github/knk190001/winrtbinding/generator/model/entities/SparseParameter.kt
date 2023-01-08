package com.github.knk190001.winrtbinding.generator.model.entities

import com.beust.klaxon.Json

data class SparseParameter(
    @Json("Name")
    val name:String,
    @Json("Type")
    val type: SparseTypeReference
){
    fun projectType(typeVariable: String, newTypeReference: SparseTypeReference): SparseParameter {
        return copy(
            type = type.projectType(typeVariable, newTypeReference)
        )
    }
}
