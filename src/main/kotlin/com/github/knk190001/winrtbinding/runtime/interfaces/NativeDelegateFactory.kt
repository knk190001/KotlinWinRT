package com.github.knk190001.winrtbinding.runtime.interfaces

import com.sun.jna.win32.StdCallLibrary.StdCallCallback
import kotlin.reflect.KType

interface NativeDelegateFactory {
    fun create(type: KType,body: Any): StdCallCallback
}