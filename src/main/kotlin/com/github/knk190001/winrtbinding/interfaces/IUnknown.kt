package com.github.knk190001.winrtbinding.interfaces

import com.sun.jna.Callback
import com.sun.jna.Pointer
import com.sun.jna.Structure
import com.sun.jna.Structure.FieldOrder
import com.sun.jna.platform.win32.Guid
import com.sun.jna.platform.win32.WinNT.HRESULT
import com.sun.jna.ptr.PointerByReference
@FieldOrder("queryInterface","addRef","release")
class IUnknown(ptr: Pointer? = Pointer.NULL) : Structure(ptr) {
    init {
        autoRead = true
        read()
    }
    @JvmField
    var queryInterface: QueryInterface? = null

    @JvmField
    var addRef: AddRef? = null

    @JvmField
    var release: Release? = null

    fun interface QueryInterface : Callback {
        operator fun invoke(thisPtr: Pointer, iid: Guid.REFIID, returnValue: PointerByReference): HRESULT
    }

    fun interface AddRef : Callback {
        operator fun invoke(thisPtr: Pointer): HRESULT
    }

    fun interface Release : Callback {
        operator fun invoke(thisPtr: Pointer): HRESULT
    }

    fun queryInterface(thisPtr: Pointer, iid: Guid.REFIID, returnValue: PointerByReference):HRESULT {
        return queryInterface!!(thisPtr,iid, returnValue)
    }

    fun addRef(thisPtr: Pointer): HRESULT {
        return addRef!!(thisPtr)
    }
    fun release(thisPtr: Pointer): HRESULT {
        return release!!(thisPtr)
    }
}