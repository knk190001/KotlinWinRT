package com.github.knk190001.winrtbinding.interfaces

import Windows.Data.Json.JsonObject
import Windows.Foundation.Collections.CollectionChange
import Windows.Foundation.Collections.IVector_IJsonValue_
import com.sun.jna.Pointer
import com.sun.jna.platform.win32.WinNT.HRESULT
import com.sun.jna.win32.StdCallLibrary.StdCallCallback


typealias DelegateTestBody = DelegateTest.(CollectionChange, IVector_IJsonValue_) -> JsonObject

class DelegateTest(ptr: Pointer? = Pointer.NULL) : Delegate<DelegateTest.Native>(ptr) {
    companion object {
        fun create(fn: DelegateTestBody): DelegateTest {
            val nativeFn = Native { thisPtr: Pointer,
                                    event: CollectionChange,
                                    eventSource: IVector_IJsonValue_,
                                    retVal: JsonObject.ByReference ->

                val thisObj = DelegateTest(thisPtr)
                retVal.setValue(fn(thisObj, event, eventSource))
                HRESULT(0)
            }

            val newDelegate = DelegateTest()
            newDelegate.init(emptyList(), nativeFn)

            return newDelegate
        }
    }

    fun interface Native : StdCallCallback {
        fun invoke(
            thisPtr: Pointer,
            event: CollectionChange,
            eventSource: IVector_IJsonValue_,
            retVal: JsonObject.ByReference
        ): HRESULT
    }

    operator fun invoke(event: CollectionChange, eventSource: IVector_IJsonValue_): JsonObject {
        val result = JsonObject.ByReference()
        delegateStruct.fn!!.invoke(this.pointer, event, eventSource, result)
        return result.getValue()
    }

    class ByReference : com.sun.jna.ptr.ByReference(com.sun.jna.Native.POINTER_SIZE) {
        fun getValue(): DelegateTest {
            return DelegateTest(pointer.getPointer(0))
        }

        fun setValue(delegate: DelegateTest) {
            pointer.setPointer(0, delegate.pointer)
        }
    }
}