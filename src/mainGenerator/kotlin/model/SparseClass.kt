package model

import com.beust.klaxon.Json

data class SparseClass(
    @Json("Name")
    val name: String,
    @Json("Namespace")
    val namespace: String,
    @Json("Interfaces")
    val interfaces: ArrayList<SparseTypeReference>,
    @Json("Methods")
    val methods: ArrayList<SparseMethod>,
    @Json("Traits")
    val traits: ArrayList<Trait>
): SparseEntity("Class")
