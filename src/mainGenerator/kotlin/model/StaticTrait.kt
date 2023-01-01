package model

import com.beust.klaxon.Json

data class StaticTrait(
    @Json("Name")
    val name: String,
    @Json("Namespace")
    val namespace: String
): Trait("Static")
