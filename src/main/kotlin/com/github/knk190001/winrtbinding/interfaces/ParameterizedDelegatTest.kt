package com.github.knk190001.winrtbinding.interfaces

import Windows.Data.Json.JsonObject
import Windows.Foundation.Collections.CollectionChange
import Windows.Foundation.Collections.IVectorChangedEventArgs
import Windows.Foundation.Collections.IVector_IJsonValue_
import com.sun.jna.Memory
import com.sun.jna.Pointer
import com.sun.jna.PointerType
import com.sun.jna.win32.StdCallLibrary.StdCallCallback


typealias ParameterizedDelegateTest<T> = Delegate<ParameterizedDelegateTest_Native<T>>

fun interface ParameterizedDelegateTest_Native<T> : StdCallCallback {
    fun invoke(
        thisPtr: Pointer,
        event: T,
        eventSource: IVector_IJsonValue_,
        retVal: JsonObject.ByReference
    )
}

inline operator fun <reified T> ParameterizedDelegateTest<T>.invoke(event: T, eventSource: IVector_IJsonValue_): JsonObject {
    val returnValue = JsonObject.ByReference()

    this.delegateStruct.fn?.invoke(this.pointer, event, eventSource, returnValue)
    return returnValue.getValue()
}

fun parameterizedDelegateTest(callback: DelegateTest.(event: CollectionChange, eventSource: IVector_IJsonValue_) -> JsonObject): DelegateTest {
    val native = DelegateTest_Native { thisPtr, event, eventSource, retVal ->
        val thisObj = DelegateTest(thisPtr, emptyList())
        val result = callback(thisObj, event, eventSource)
        retVal.setValue(result)
    }
    val newDelegate = Delegate.createDelegate(emptyList(), native)
    newDelegate.delegateStruct.fn = native
    return newDelegate
}

