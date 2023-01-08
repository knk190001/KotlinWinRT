package com.github.knk190001.winrtbinding.generator.model.entities

data class SparseDelegate(
    val name:String,
    val namespace: String,
    val guid: String,
    val parameter: List<SparseParameter>,
    val returnType: SparseTypeReference,
    val genericParameters: List<SparseGenericParameter>? = null
): SparseEntity("Delegate") {
    fun projectType(typeVariable: String, newTypeReference: SparseTypeReference): SparseDelegate {
        if (genericParameters == null) return this
        return copy(
            parameter = parameter.map { it.projectType(typeVariable, newTypeReference) },
            returnType = returnType.projectType(typeVariable, newTypeReference),
            genericParameters = genericParameters.map { it.projectType(typeVariable, newTypeReference) }
        )
    }
}
