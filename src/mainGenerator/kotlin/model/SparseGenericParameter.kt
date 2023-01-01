package model

import com.beust.klaxon.Json

data class SparseGenericParameter(
    @Json("Name")
    val name: String,
    @Json("Position")
    val position:Int,
    @Json("Type")
    val type: SparseTypeReference?
){
    fun projectType(typeVariable: String, newTypeReference: SparseTypeReference): SparseGenericParameter {
        return copy(
            type = type?.projectType(typeVariable,newTypeReference)
        )
    }
}