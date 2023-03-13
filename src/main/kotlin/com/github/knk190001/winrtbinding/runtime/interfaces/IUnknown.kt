package com.github.knk190001.winrtbinding.runtime.interfaces

import com.github.knk190001.winrtbinding.runtime.invokeHR
import com.sun.jna.Function
import com.sun.jna.Native
import com.sun.jna.NativeMapped
import com.sun.jna.Pointer
import com.sun.jna.PointerType
import com.sun.jna.platform.win32.Guid
import com.sun.jna.platform.win32.WinDef
import com.sun.jna.ptr.PointerByReference
import java.lang.RuntimeException

interface IUnknown : NativeMapped, IWinRTInterface {
    val iUnknown_Vtbl: Pointer
    val iUnknown_Ptr: Pointer

    fun QueryInterface(iid: Guid.REFIID): Pointer {
        val fnPtr = iUnknown_Vtbl!!.getPointer(0)
        val fn = Function.getFunction(fnPtr, Function.ALT_CONVENTION)
        val result = PointerByReference()
        val hr = fn.invokeHR(arrayOf(iUnknown_Ptr, iid, result))
        if (hr.toInt() != 0) {
            throw RuntimeException(hr.toString())
        }
        return result.value
    }

    fun AddRef(): WinDef.ULONG {
        val fnPtr = iUnknown_Vtbl!!.getPointer(1)
        val fn = Function.getFunction(fnPtr, Function.ALT_CONVENTION)
        return WinDef.ULONG(fn.invokeLong(arrayOf(iUnknown_Ptr)))
    }

    fun Release(): WinDef.ULONG {
        val fnPtr = iUnknown_Vtbl!!.getPointer(2)
        val fn = Function.getFunction(fnPtr, Function.ALT_CONVENTION)
        return WinDef.ULONG(fn.invokeLong(arrayOf(iUnknown_Ptr)))
    }

    public object ABI {
        public val IID: Guid.IID = Guid.IID("0000000000000000C000000000000046")

        public fun makeIUnknown(ptr: Pointer?): IUnknown =
            IUnknown_Impl(ptr)

    }

    public class ByReference : com.sun.jna.ptr.ByReference(Native.POINTER_SIZE) {
        public fun getValue() = ABI.makeIUnknown(pointer.getPointer(0))

        public fun setValue(value: IUnknown_Impl): Unit {
            pointer.setPointer(0, value.pointer)
        }
    }
    public class IUnknown_Impl(ptr: Pointer? = Pointer.NULL) : PointerType(ptr), IUnknown {
        override val iUnknown_Ptr: Pointer
            get() = pointer

        override val iUnknown_Vtbl: Pointer
            get() = pointer.getPointer(0)


    }
}