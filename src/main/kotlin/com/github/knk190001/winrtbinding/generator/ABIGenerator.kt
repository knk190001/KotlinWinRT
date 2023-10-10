package com.github.knk190001.winrtbinding.generator

import com.beust.klaxon.JsonObject
import com.beust.klaxon.Klaxon
import com.beust.klaxon.Parser
import com.github.knk190001.winrtbinding.generator.model.entities.*
import com.squareup.kotlinpoet.FileSpec
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.internal.synchronized
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.io.InvalidObjectException
import kotlin.io.path.Path
import kotlin.io.path.forEachDirectoryEntry
import kotlin.io.path.inputStream

typealias LookUp = (SparseTypeReference) -> SparseEntity

lateinit var lookUpTypeReference: LookUp

@OptIn(InternalCoroutinesApi::class)
fun generateProjection(): Collection<FileSpec> {
    val jsonObjects = mutableListOf<JsonObject>()
    runBlocking {

        Path("${System.getProperty("user.dir")}/json").forEachDirectoryEntry {
            launch {
                val json = Parser.default().parse(it.inputStream())
                synchronized(jsonObjects) {
                    jsonObjects.add(json as JsonObject)
                }
            }
        }
    }
    val entities = jsonObjects.parallelStream().map {
        println("Parsing: " + it["Name"])
        Klaxon().parse<SparseEntity>(it.toJsonString())
    }.toList().filterNotNull()


    val entityMap = entities.associateBy { it.fullName() }

    val lookUp: LookUp = { typeReference ->
        val tr = typeReference.normalize()
        //println("Lookup: " + typeReference.fullName())
        if (entityMap[tr.fullName()] == null) {
            println("Not found: ${tr.fullName()}")
            throw IllegalArgumentException("Entity not found: ${tr.fullName()}")
        }
        entityMap[tr.fullName()]!!
    }
    lookUpTypeReference = lookUp

        return entities.parallelStream().map {
        println("Generating: " + it.fullName())
        when (it) {
            is SparseClass -> generateClass(it, lookUp)
            is SparseInterface -> generateInterface(it, lookUp)
            is SparseEnum -> generateEnum(it)
            is SparseStruct -> generateStruct(it)
            is SparseDelegate -> generateDelegate(it)
            else -> throw InvalidObjectException("Object is not of type sparse class or sparse interface.")
        }
    }.toList()
}

fun String.fixSpaces(): String {
    return this.replace(" ", nbsp)
}