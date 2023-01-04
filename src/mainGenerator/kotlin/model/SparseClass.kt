package model

import com.beust.klaxon.Json

data class SparseClass(
    @Json("Name")
    val name: String,
    @Json("Namespace")
    val namespace: String,
    @Json("Interfaces")
    val interfaces: List<SparseTypeReference>,
    @Json("Methods")
    val methods: List<SparseMethod>,
    @Json("Traits")
    val traits: List<Trait>
): SparseEntity("Class")
