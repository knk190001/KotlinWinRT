package com.github.knk190001.winrtbinding.interfaces

import Windows.Data.Json.JsonObject
import Windows.Foundation.Collections.CollectionChange
import Windows.Foundation.Collections.IVectorChangedEventArgs
import Windows.Foundation.Collections.IVector_IJsonValue_
import com.sun.jna.Memory
import com.sun.jna.Pointer
import com.sun.jna.PointerType
import com.sun.jna.win32.StdCallLibrary.StdCallCallback

class DelegateTest(ptr: Pointer? = Memory(12)) : Delegate<DelegateTest.DelegateTest_Native>(ptr, emptyList()) {
    interface DelegateTest_Native : StdCallCallback {
        fun invoke(
            thisPtr: Pointer,
            event: CollectionChange,
            eventSource: IVector_IJsonValue_,
            retVal: JsonObject.ByReference
        )
    }

    operator fun invoke(event: CollectionChange, eventSource: IVector_IJsonValue_): JsonObject {
        val returnValue = JsonObject.ByReference()
        this.delegateStruct.fn?.invoke(this.pointer,event,eventSource,returnValue)
        return returnValue.getValue()
    }
}