package com.github.knk190001.winrtbinding.generator.model.entities

import com.beust.klaxon.Json
import com.github.knk190001.winrtbinding.generator.model.traits.Trait

data class SparseInterface(
    @Json("Name")
    val name:String,
    @Json("Namespace")
    val namespace: String,
    @Json("Guid")
    val guid: String,
    @Json("Methods")
    val methods: List<SparseMethod>,
    @Json("GenericParameters")
    val genericParameters: List<SparseGenericParameter>?,
    @Json("Traits")
    val traits: List<Trait>
): SparseEntity("Interface") {
    fun projectType(typeVariable: String, newTypeReference: SparseTypeReference): SparseInterface {
        return this.copy(
            methods = methods.map { it.projectType(typeVariable, newTypeReference) },
            genericParameters = genericParameters!!.map { it.projectType(typeVariable, newTypeReference) }
        )
    }
}

