package com.github.knk190001.winrtbinding.generator.model.entities

interface DirectProjectable<T : DirectProjectable<T>> : IProjectable {
    fun projectType(typeVariable: String, newTypeReference: SparseTypeReference): T

    fun withName(newName: String):T
}