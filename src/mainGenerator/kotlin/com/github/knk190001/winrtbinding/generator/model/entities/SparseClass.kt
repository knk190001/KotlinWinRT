package com.github.knk190001.winrtbinding.generator.model.entities

import com.beust.klaxon.Json
import com.github.knk190001.winrtbinding.generator.model.traits.DefaultInterfaceTrait
import com.github.knk190001.winrtbinding.generator.model.traits.Trait

data class SparseClass(
    @Json("Name")
    override val name: String,
    @Json("Namespace")
    override val namespace: String,
    @Json("Interfaces")
    val interfaces: List<SparseTypeReference>,
    @Json("Methods")
    val methods: List<SparseMethod>,
    @Json("Traits")
    val traits: List<Trait>
) : SparseEntity("Class"), INamedEntity {

    val defaultInterface by lazy {
        (traits.single { it.traitType == "DefaultInterface" } as DefaultInterfaceTrait).defaultInterface
    }
}