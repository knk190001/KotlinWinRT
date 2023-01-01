package model

import com.beust.klaxon.Json

data class SparseMethod(
    @Json("Name")
    val name: String,
    @Json("Parameters")
    val parameters: ArrayList<SparseParameter>,
    @Json("ReturnType")
    val returnType: SparseTypeReference
) {
    fun projectType(typeVariable: String, newTypeReference: SparseTypeReference): SparseMethod {
        return copy(
            parameters = parameters.map { it.projectType(typeVariable, newTypeReference) }.toCollection(ArrayList()),
            returnType = returnType.projectType(typeVariable, newTypeReference)
        )
    }
}