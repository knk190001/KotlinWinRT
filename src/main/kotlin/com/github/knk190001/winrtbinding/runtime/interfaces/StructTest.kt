package com.github.knk190001.winrtbinding.runtime.interfaces

import com.sun.jna.Pointer
import com.sun.jna.Structure
import com.sun.jna.Structure.FieldOrder
import com.sun.jna.platform.win32.WinDef.UINT


@FieldOrder("field1","field2","field3")
class StructTest(ptd: Pointer? = Pointer.NULL) : Structure(), Structure.ByValue {
    @JvmField
    var field1: UINT? = null
    @JvmField
    var field2: UINT? = null
    @JvmField
    var field3: UINT? = null
}
