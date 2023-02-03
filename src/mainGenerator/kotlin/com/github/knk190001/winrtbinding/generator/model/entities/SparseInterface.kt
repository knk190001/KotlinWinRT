package com.github.knk190001.winrtbinding.generator.model.entities

import com.beust.klaxon.Json
import com.github.knk190001.winrtbinding.generator.model.traits.Trait

data class SparseInterface(
    @Json("Name")
    override val name:String,
    @Json("Namespace")
    override val namespace: String,
    @Json("Guid")
    override val guid: String,
    @Json("Methods")
    val methods: List<SparseMethod>,
    @Json("GenericParameters")
    val genericParameters: List<SparseGenericParameter>?,
    @Json("Traits")
    val traits: List<Trait>
): SparseEntity("Interface"), DirectProjectable<SparseInterface>{
    override fun projectType(typeVariable: String, newTypeReference: SparseTypeReference): SparseInterface {
        return this.copy(
            methods = methods.map { it.projectType(typeVariable, newTypeReference) },
            genericParameters = genericParameters!!.map { it.projectType(typeVariable, newTypeReference) }
        )
    }

    override fun withName(newName: String): SparseInterface {
        return copy(name = newName)
    }

    fun asTypeReference(): SparseTypeReference {
        return SparseTypeReference(
            name,
            namespace,
            genericParameters
        )
    }
}

