package com.github.knk190001.winrtbinding

import com.sun.jna.Function.ALT_CONVENTION
import com.sun.jna.Function
import com.sun.jna.Native.POINTER_SIZE
import com.sun.jna.Pointer
import com.sun.jna.platform.win32.WinNT

interface TestInterface {
    val pointer: Pointer

    val vtblPtr
        get() = pointer.getPointer(0)

    public fun get_Text(): String {
        val fnPtr = vtblPtr.getPointer(6L * POINTER_SIZE)
        val fn = Function.getFunction(fnPtr, ALT_CONVENTION)
        val result = WinNT.HANDLEByReference()
        val hr = fn.invokeHR(arrayOf(pointer,  result))
        if (hr.toInt() != 0) {
            throw RuntimeException(hr.toString())
        }
        val resultValue = result.getValue()
        val resultValue_Managed = resultValue.handleToString()
        return resultValue_Managed
    }
}