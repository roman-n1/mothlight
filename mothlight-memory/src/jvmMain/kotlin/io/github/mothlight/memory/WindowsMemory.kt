package io.github.mothlight.memory

import com.sun.jna.Native
import com.sun.jna.Structure
import com.sun.jna.platform.win32.BaseTSD.SIZE_T
import com.sun.jna.platform.win32.Kernel32
import com.sun.jna.platform.win32.WinNT
import com.sun.jna.win32.StdCallLibrary
import com.sun.jna.win32.W32APIOptions

internal fun windowsWorkingSet(): Long? {
    val counters = ProcessMemoryCounters()
    counters.cb = counters.size()
    return if (ProcessMemoryApi.instance.GetProcessMemoryInfo(Kernel32.INSTANCE.GetCurrentProcess(), counters, counters.size())) {
        counters.WorkingSetSize.toLong().takeIf { it >= 0 }
    } else null
}

@Structure.FieldOrder(
    "cb", "PageFaultCount", "PeakWorkingSetSize", "WorkingSetSize",
    "QuotaPeakPagedPoolUsage", "QuotaPagedPoolUsage", "QuotaPeakNonPagedPoolUsage",
    "QuotaNonPagedPoolUsage", "PagefileUsage", "PeakPagefileUsage",
)
internal class ProcessMemoryCounters : Structure() {
    @JvmField var cb: Int = 0
    @JvmField var PageFaultCount: Int = 0
    @JvmField var PeakWorkingSetSize: SIZE_T = SIZE_T(0)
    @JvmField var WorkingSetSize: SIZE_T = SIZE_T(0)
    @JvmField var QuotaPeakPagedPoolUsage: SIZE_T = SIZE_T(0)
    @JvmField var QuotaPagedPoolUsage: SIZE_T = SIZE_T(0)
    @JvmField var QuotaPeakNonPagedPoolUsage: SIZE_T = SIZE_T(0)
    @JvmField var QuotaNonPagedPoolUsage: SIZE_T = SIZE_T(0)
    @JvmField var PagefileUsage: SIZE_T = SIZE_T(0)
    @JvmField var PeakPagefileUsage: SIZE_T = SIZE_T(0)
}

internal interface ProcessMemoryApi : StdCallLibrary {
    fun GetProcessMemoryInfo(process: WinNT.HANDLE, counters: ProcessMemoryCounters, cb: Int): Boolean
    companion object {
        val instance: ProcessMemoryApi by lazy {
            Native.load("psapi", ProcessMemoryApi::class.java, W32APIOptions.DEFAULT_OPTIONS)
        }
    }
}
