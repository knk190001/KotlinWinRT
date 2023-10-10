package com.github.knk190001.winrtbinding.runtime.base

import com.github.knk190001.winrtbinding.runtime.com.IUnknownVtbl
import com.sun.jna.*
import com.sun.jna.Structure.FieldOrder
import com.sun.jna.platform.win32.Guid.GUID
import com.sun.jna.platform.win32.WinDef
import com.sun.jna.platform.win32.WinDef.UINT
import com.sun.jna.platform.win32.WinNT.HRESULT

open class Delegate(ptr: Pointer? = Memory(12)) : PointerType(ptr) {
    val delegateStruct by lazy { DelegateVtbl.ByReference(pointer.getPointer(0)) }

    class ByReference  : com.sun.jna.ptr.ByReference(Native.POINTER_SIZE) {
        fun setValue(delegate: Delegate) {
            this.pointer.setPointer(0, delegate.pointer)
        }

        fun getValue(): Delegate {
            return Delegate(this.pointer.getPointer(0))
        }
    }

    fun init(uuids: List<GUID>, fn: Callback) {
        init(uuids, CallbackReference.getFunctionPointer(fn))
    }

    fun init(uuids: List<GUID>, fn: Pointer) {
        val memory = Memory(Native.getNativeSize(DelegateVtbl.ByValue::class.java).toLong())
        val vtbl = DelegateVtbl.ByReference(memory)

        pointer.setPointer(0, memory)
        pointer.setInt(Native.POINTER_SIZE.toLong(), 1)
        vtbl.fn = fn
        val unknown = vtbl.iUnknownVtbl
        unknown!!.queryInterface = IUnknownVtbl.QueryInterface { thisPtr, iid, returnValue ->
            returnValue.value = Pointer.NULL
            if (thisPtr == Pointer.NULL) {
                return@QueryInterface HRESULT(UINT(0x80070057).toInt())
            }
            if (uuids.contains(iid.value)) {
                unknown.addRef(thisPtr)
                returnValue.value = pointer
                return@QueryInterface HRESULT(0)
            }

            return@QueryInterface HRESULT(-2147467262)
        }
        unknown.addRef = IUnknownVtbl.AddRef {
            val refCount = pointer.getInt(8)
            pointer.setInt(8, refCount + 1)
            return@AddRef WinDef.ULONG(refCount+1L)
        }
        unknown.release = IUnknownVtbl.Release {
            val refCount = pointer.getInt(8)
            pointer.setInt(8, refCount - 1)
            return@Release WinDef.ULONG(refCount-1L)
        }
        vtbl.write()
    }

    @FieldOrder("iUnknownVtbl", "fn")
    sealed class DelegateVtbl(ptr: Pointer?) : Structure(ptr) {
        class ByValue(ptr: Pointer?) : DelegateVtbl(ptr), Structure.ByValue
        class ByReference(ptr: Pointer?) : DelegateVtbl(ptr), Structure.ByReference
        init {
            autoRead = true
            autoWrite = true
        }

        @JvmField
        var iUnknownVtbl: IUnknownVtbl? = null

        @JvmField
        var fn: Pointer? = null
    }
}