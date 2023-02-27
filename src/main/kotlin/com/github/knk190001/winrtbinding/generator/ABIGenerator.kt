package com.github.knk190001.winrtbinding.generator

import com.beust.klaxon.JsonObject
import com.beust.klaxon.Klaxon
import com.beust.klaxon.Parser
import com.github.knk190001.winrtbinding.generator.model.entities.*
import com.squareup.kotlinpoet.*
import java.io.InvalidObjectException
import kotlin.io.path.Path
import kotlin.io.path.forEachDirectoryEntry
import kotlin.io.path.inputStream

typealias  LookUpProjectable = (SparseTypeReference) -> IDirectProjectable<*>
typealias  LookUp = (SparseTypeReference) -> SparseEntity

typealias ProjectInterface = (IDirectProjectable<*>, List<SparseGenericParameter>) -> Unit

lateinit var lookUpTypeReference: LookUp


fun generateProjection(): Collection<FileSpec> {
    val jsonObjects = mutableListOf<JsonObject>()
    Path("${System.getProperty("user.dir")}/json").forEachDirectoryEntry {
        jsonObjects.add(Parser.default().parse(it.inputStream()) as JsonObject)
    }

    val entities = jsonObjects.map {
        println(it["Name"])
        Klaxon().parse<SparseEntity>(it.toJsonString())
    }.filterNotNull()

    val lookUp: LookUp = { typeReference ->
        val tr = typeReference.normalize()
        println(typeReference.fullName())
        if(entities.none {
                tr.equals(it)
            }) {
            println("Not found: ${tr.fullName()}")
        }
        entities.first {
            tr.equals(it)
        }
    }
    lookUpTypeReference = lookUp

    val projections = mutableListOf<Pair<IDirectProjectable<*>, Collection<SparseGenericParameter>>>()
    val projectInterface: (IDirectProjectable<*>, Collection<SparseGenericParameter>) -> Unit =
        { projectable: IDirectProjectable<*>, genericParameters: Collection<SparseGenericParameter> ->
            if (projections.none {
                    isProjectionEquivalent(it, projectable to genericParameters)
                }) {
                projections.add(projectable to genericParameters)
            }

        }

    return entities.map {
        when (it) {
            is SparseClass -> generateClass(it, lookUp, projectInterface)
            is SparseInterface -> generateInterface(it, lookUp, projectInterface)
            is SparseEnum -> generateEnum(it)
            is SparseStruct -> generateStruct(it)
            is SparseDelegate -> generateDelegate(it, lookUp, projectInterface)

            else -> throw InvalidObjectException("Object is not of type sparse class or sparse interface.")
        }
    }.toMutableList().apply {
        addAll(generateProjectedTypes(projections, lookUp) { projectable, parameters ->
            projections.contains(projectable to parameters)
        })
    }
}

private fun isProjectionEquivalent(
    a: Pair<IDirectProjectable<*>, Collection<SparseGenericParameter>>,
    b: Pair<IDirectProjectable<*>, Collection<SparseGenericParameter>>
): Boolean {
    return a.first.namespace == b.first.namespace && a.first.name == b.first.name && equalsIgnoreOrder(
        a.second, b.second
    )
}

private fun generateProjectedTypes(
    projections: List<Pair<IDirectProjectable<*>, Collection<SparseGenericParameter>>>,
    lookUpTypeReference: LookUp,
    checkIfExists: (IDirectProjectable<*>, List<SparseGenericParameter>) -> Boolean
): List<FileSpec> {
    val secondaryProjections = mutableListOf<Pair<IDirectProjectable<*>, List<SparseGenericParameter>>>()
    return projections.filter {
        it.second.none { gParam ->
            gParam.type == null
        }
    }.map {
        val (sInterface, genericParameters) = it
        val projectedInterface = genericParameters.fold(sInterface) { acc, sparseGenericParameter ->
            acc.projectType(sparseGenericParameter.name, sparseGenericParameter.type!!)
        }.withProjectedName()

        val projectInterface: ProjectInterface = { secondaryProjectionInterface, parameters ->
            if (!checkIfExists(secondaryProjectionInterface, parameters)) {
                secondaryProjections.add(secondaryProjectionInterface to parameters)
            }
        }
        when (projectedInterface) {
            is SparseInterface -> generateInterface(projectedInterface, lookUpTypeReference, projectInterface)
            is SparseDelegate -> generateDelegate(projectedInterface, lookUpTypeReference, projectInterface)
            else -> throw NotImplementedError()
        }
    }.toMutableList().apply {
        val distinctProjections =
            secondaryProjections.distinctBy { it.first.toString() + it.second.joinToString(transform = SparseGenericParameter::toString) }
        if (distinctProjections.isNotEmpty()) {
            addAll(generateProjectedTypes(
                distinctProjections, lookUpTypeReference
            ) { sInterface, params ->
                distinctProjections.containsProjection(sInterface, params) || checkIfExists(sInterface, params)
            })
        }
    }
}

private fun List<Pair<IDirectProjectable<*>, List<SparseGenericParameter>>>.containsProjection(
    projectable: IDirectProjectable<*>, params: List<SparseGenericParameter>
): Boolean {
    return this.any {
        val (otherProjectable, otherParams) = it
        projectable.name == otherProjectable.name && projectable.namespace == otherProjectable.namespace && params.joinToString() == otherParams.joinToString()

    }
}


fun <T> equalsIgnoreOrder(list1: Collection<T>, list2: Collection<T>) =
    list1.size == list2.size && list1.toSet() == list2.toSet()

