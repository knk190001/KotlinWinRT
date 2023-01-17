package com.github.knk190001.winrtbinding.interfaces

import com.sun.jna.Memory
import com.sun.jna.Pointer
import com.sun.jna.PointerType
import com.sun.jna.Structure
import com.sun.jna.Structure.FieldOrder
import com.sun.jna.platform.win32.Guid.GUID
import com.sun.jna.platform.win32.WinDef.UINT
import com.sun.jna.platform.win32.WinNT.HRESULT
import com.sun.jna.win32.StdCallLibrary.StdCallCallback

open class Delegate<T : StdCallCallback>(ptr: Pointer? = Memory(12), val uuids: List<GUID>) : PointerType(ptr) {
    val delegateStruct by lazy { DelegateVtbl<T>(pointer.getPointer(0)) }

    companion object {
        fun <T : StdCallCallback> createDelegate(uuids: List<GUID>, fn: T): Delegate<T> {
            val vtbl = DelegateVtbl<T>(Pointer.NULL)
            val newDelegate = Delegate<T>(uuids = uuids)
            newDelegate.pointer.setPointer(0, vtbl.pointer)
            newDelegate.pointer.setInt(8, 1)
            vtbl.fn = fn
            val unknown = vtbl.iUnknownVtbl
            unknown!!.queryInterface = IUnknownVtbl.QueryInterface { thisPtr, iid, returnValue ->
                returnValue.value = Pointer.NULL
                if (thisPtr == Pointer.NULL) {
                    return@QueryInterface HRESULT(UINT(0x80070057).toInt())
                }
                if (uuids.contains(iid.value)) {
                    unknown.addRef(thisPtr)
                    returnValue.value = newDelegate.pointer
                    return@QueryInterface HRESULT(0)
                }

                return@QueryInterface HRESULT(-2147467262)
            }
            unknown.addRef = IUnknownVtbl.AddRef {
                val refCount = newDelegate.pointer.getInt(8)
                newDelegate.pointer.setInt(8, refCount + 1)
                return@AddRef HRESULT(0)
            }
            unknown.release = IUnknownVtbl.Release {
                val refCount = newDelegate.pointer.getInt(8)
                newDelegate.pointer.setInt(8, refCount - 1)
                return@Release HRESULT(0)
            }
            return newDelegate
        }
    }

    @FieldOrder("iUnknown", "fn")
    class DelegateVtbl<T : StdCallCallback>(ptr: Pointer?) : Structure(ptr) {
        init {
            read()
            autoRead = true
            autoWrite = true
        }

        @JvmField
        var iUnknownVtbl: IUnknownVtbl? = null

        @JvmField
        var fn: T? = null
    }
}