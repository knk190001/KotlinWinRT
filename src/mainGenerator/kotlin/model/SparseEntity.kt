package model

import com.beust.klaxon.Json
import com.beust.klaxon.TypeAdapter
import com.beust.klaxon.TypeFor
import kotlin.reflect.KClass

@TypeFor(field = "type", EntityAdapter::class)
open class SparseEntity(
    @Json("Type")
    val type: String
)

class EntityAdapter : TypeAdapter<SparseEntity> {
    override fun classFor(type: Any): KClass<out SparseEntity> {
        return when (type as String) {
            "Class" -> SparseClass::class
            "Interface" -> SparseInterface::class
            else -> throw IllegalArgumentException("type must be either \"Class\" or \"Interface\"")
        }
    }
}