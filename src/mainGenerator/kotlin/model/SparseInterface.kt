package model

import com.beust.klaxon.Json

data class SparseInterface(
    @Json("Name")
    val name:String,
    @Json("Namespace")
    val namespace: String,
    @Json("Guid")
    val guid: String,
    @Json("Methods")
    val methods: ArrayList<SparseMethod>,
    @Json("GenericParameters")
    val genericParameters: ArrayList<SparseGenericParameter>?,
    @Json("Traits")
    val traits: ArrayList<Trait>
): SparseEntity("Interface") {
    fun projectType(typeVariable: String, newTypeReference: SparseTypeReference): SparseInterface {
        return this.copy(
            methods = methods.map { it.projectType(typeVariable, newTypeReference) }.toCollection(ArrayList()),
        )
    }
}

