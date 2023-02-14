package com.github.knk190001.winrtbinding

import com.sun.jna.Pointer
import com.sun.jna.platform.win32.WinDef.UINT
import com.sun.jna.ptr.PointerByReference
import kotlin.reflect.KClass
import kotlin.reflect.full.createInstance
import java.lang.reflect.Array.newInstance as jvmNewArrayInstance

class OutArray<T>(val clazz: Class<T>) : PointerByReference() {
    val arrayPtr: Pointer
        get() = value

    var initialized = false
    lateinit var array: Array<T>

    val size
        get() = array.size

    operator fun get(idx: Int): T {
        return array[idx]
    }

    operator fun set(idx: Int, newValue: T) {
        array[idx] = newValue
    }
}

inline fun <reified T> OutArray<T>.initialize(length: UINT) {
    @Suppress("UNCHECKED_CAST")
    array = jvmNewArrayInstance(clazz, length.toInt()) as Array<T>
    arrayPtr.getValue(0, array)
    initialized = true
}

inline fun <reified T> makeOutArray() :OutArray<T>{
    return OutArray(T::class.java)
}