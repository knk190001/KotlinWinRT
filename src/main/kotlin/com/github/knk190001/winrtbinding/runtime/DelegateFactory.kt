package com.github.knk190001.winrtbinding.runtime

import com.github.knk190001.winrtbinding.runtime.interfaces.Delegate
import com.github.knk190001.winrtbinding.runtime.interfaces.NativeDelegateFactory
import com.sun.jna.win32.StdCallLibrary.StdCallCallback
import kotlin.reflect.KClass
import kotlin.reflect.KType
import kotlin.reflect.typeOf

object DelegateFactory {
    val delegateFactories = mutableMapOf<KType, NativeDelegateFactory>()

    fun register(type: KType, factory: NativeDelegateFactory) {
        delegateFactories[type] = factory
    }

    inline fun <reified T : Any> createNative(fn: T): StdCallCallback {
        val delegateType = typeOf<T>()
        var delegateFactory = delegateFactories[delegateType]
        if (delegateFactory == null) {
            Class.forName("${delegateType.packageName()}.${delegateType.fullName()}\$NativeFactory")
        }
        delegateFactory = delegateFactories[delegateType] ?: throw IllegalArgumentException("Delegate not found: $delegateType")
        return delegateFactory.create(delegateType, fn)
    }

    fun KType.simpleName(): String {
        if (this.classifier !is KClass<*>) {
            throw IllegalArgumentException("Expected KClass<*>, got ${this.classifier}")
        }
        return (this.classifier as KClass<*>).simpleName!!
    }
    fun KType.packageName():String {
        if (this.classifier !is KClass<*>) {
            throw IllegalArgumentException("Expected KClass<*>, got ${this.classifier}")
        }
        return (this.classifier as KClass<*>).java.packageName
    }

    fun KType.fullName(): String {
        if (this.classifier !is KClass<*>) {
            throw IllegalArgumentException("Expected KClass<*>, got ${this.classifier}")
        }
        val typeParams =
            if (this.arguments.isEmpty()) ""
            else this.arguments.joinToString(separator = "_",prefix = "_", postfix = "_") { it.type!!.fullName() }

        val name = this.simpleName()
        return "${name}${typeParams}"
    }
}

