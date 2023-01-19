package com.github.knk190001.winrtbinding.interfaces

import Windows.Data.Json.JsonObject
import Windows.Foundation.Collections.CollectionChange
import Windows.Foundation.Collections.IVector_IJsonValue_
import com.sun.jna.Pointer
import com.sun.jna.win32.StdCallLibrary.StdCallCallback

class DelegateTest : Delegate<DelegateTest.DelegateTest_Native>() {
    //}
//    return newDelegate
//    newDelegate.delegateStruct.fn = native
//    val newDelegate = Delegate.createDelegate(emptyList(), native)
//    }
//        retVal.setValue(result)
//        val result = callback(thisObj, event, eventSource)
//        val thisObj = DelegateTest(thisPtr)
//    val native = DelegateTest_Native { thisPtr, event, eventSource, retVal ->
//fun delegateTest(callback: DelegateTest.(event: CollectionChange, eventSource: IVector_IJsonValue_) -> JsonObject): DelegateTest {
//
//}
//    return returnValue.getValue()
//    this.delegateStruct.fn?.invoke(this.pointer, event, eventSource, returnValue)
//    val returnValue = JsonObject.ByReference()
//operator fun DelegateTest.invoke(event: CollectionChange, eventSource: IVector_IJsonValue_): JsonObject {
//
//
//typealias DelegateTest = Delegate<DelegateTest_Native>
//
//fun ByReference(): Delegate.ByReference<DelegateTest_Native> {
//    return Delegate.ByReference()
//}
    fun interface DelegateTest_Native : StdCallCallback {
        fun invoke(
            thisPtr: Pointer,
            event: CollectionChange,
            eventSource: IVector_IJsonValue_,
            retVal: JsonObject.ByReference
        )
    }


}