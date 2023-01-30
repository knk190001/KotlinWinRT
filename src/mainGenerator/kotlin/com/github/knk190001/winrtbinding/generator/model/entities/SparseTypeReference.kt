package com.github.knk190001.winrtbinding.generator.model.entities

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

    fun normalize(): SparseTypeReference {
        return if (this.name.endsWith("&")) {
            copy(name = name.dropLast(1))
        } else if (this.name.contains("_")) {
            copy(name = "${name.replaceAfter('_', "").dropLast(1)}`${genericParameters!!.count()}")
        } else {
            this
        }
    }

    override operator fun equals(other: Any?): Boolean {
        if (other !is INamedEntity) {
            return false
        }
        return name == other.name && namespace == other.namespace
    }

    override fun hashCode(): Int {
        var result = name.hashCode()
        result = 31 * result + namespace.hashCode()
        result = 31 * result + (genericParameters?.hashCode() ?: 0)
        return result
    }
}
