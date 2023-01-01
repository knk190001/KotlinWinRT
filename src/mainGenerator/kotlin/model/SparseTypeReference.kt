package model

import com.beust.klaxon.Json

data class SparseTypeReference(
    @Json("Name")
    val name: String,
    @Json("Namespace")
    val namespace: String,
    @Json("GenericParameters")
    val genericParameters: List<SparseGenericParameter>?
) {
    fun projectType(typeVariable: String, newTypeReference: SparseTypeReference): SparseTypeReference {
        if (name == typeVariable) {
            return newTypeReference
        }
        return copy(genericParameters = genericParameters?.map {
            it.projectType(typeVariable, newTypeReference)
        })
    }
}
