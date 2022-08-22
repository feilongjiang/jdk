package jdk.internal.foreign.abi.riscv64.linux;

import jdk.internal.foreign.abi.AbstractLinker;

import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodType;
import java.util.function.Consumer;

public final class LinuxRISCV64Linker extends AbstractLinker {
    static LinuxRISCV64Linker instance = null;

    public static LinuxRISCV64Linker getInstance() {
        if (instance == null) {
            instance = new LinuxRISCV64Linker();
        }
        return instance;
    }

    @Override
    protected MethodHandle arrangeDowncall(MethodType inferredMethodType, FunctionDescriptor function) {
        return CallArranger.arrangeDowncall(inferredMethodType, function);
    }

    @Override
    protected MemorySegment arrangeUpcall(MethodHandle target, MethodType targetType, FunctionDescriptor function, MemorySession scope) {
        return CallArranger.arrangeUpcall(target, targetType, function, scope);
    }

    public static VaList newVaList(Consumer<VaList.Builder> actions, MemorySession scope) {
        LinuxRISCV64VaList.Builder builder = new LinuxRISCV64VaList.Builder(scope);
        actions.accept(builder);
        return builder.build();
    }

    public static VaList newVaListOfAddress(MemoryAddress ma, MemorySession session) {
        MemorySegment segment = MemorySegment.ofAddress(ma, Long.MAX_VALUE, session); // size unknown
        return new LinuxRISCV64VaList(segment, 0);
    }

    public static VaList emptyVaList() {
        return LinuxRISCV64VaList.empty();
    }
}
