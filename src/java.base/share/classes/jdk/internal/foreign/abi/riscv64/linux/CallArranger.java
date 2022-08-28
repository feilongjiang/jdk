package jdk.internal.foreign.abi.riscv64.linux;

import jdk.internal.foreign.PlatformLayouts;
import jdk.internal.foreign.Utils;
import jdk.internal.foreign.abi.*;


import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodType;
import java.util.List;
import java.util.Optional;


import static jdk.internal.foreign.abi.riscv64.RISCV64Architecture.*;
import static jdk.internal.foreign.abi.riscv64.linux.TypeClass.*;



public class CallArranger {
    private static final int STACK_SLOT_SIZE = 8;
    // https://github.com/riscv-non-isa/riscv-elf-psabi-doc/blob/master/riscv-cc.adoc

    private static final ABIDescriptor CLinux = abiFor(
            new VMStorage[]{x10, x11, x12, x13, x14, x15, x16, x17},
            new VMStorage[]{f10, f11, f12, f13, f14, f15, f16, f17},
            new VMStorage[]{x10, x11},
            new VMStorage[]{f10, f11},
            new VMStorage[]{x5, x6, x7, x28, x29, x30, x31},
            new VMStorage[]{f0, f1, f2, f3, f4, f5, f6, f7, f28, f29, f30, f31},
            16,
            0, //no shadow space
            x29, // target addr reg
            x30  // ret buf addr reg
    );

    // record
    public static class Bindings {
        public final CallingSequence callingSequence;
        public final boolean isInMemoryReturn;
        public final boolean lengthSensitive;
        public final long[] offset;
        public final long[] length;

        Bindings(CallingSequence callingSequence, boolean isInMemoryReturn) {
            this.callingSequence = callingSequence;
            this.isInMemoryReturn = isInMemoryReturn;
            lengthSensitive = false;
            offset = new long[0];
            length = new long[0];
        }

        Bindings(CallingSequence callingSequence, boolean isInMemoryReturn, boolean lengthSensitive,
                 long[] offset, long[] length) {
            this.callingSequence = callingSequence;
            this.isInMemoryReturn = isInMemoryReturn;
            this.lengthSensitive = lengthSensitive;
            this.offset = offset;
            this.length = length;
        }

    }

    public static CallArranger.Bindings getBindings(MethodType mt, FunctionDescriptor cDesc, boolean forUpcall) {
        CallingSequenceBuilder csb = new CallingSequenceBuilder(CLinux, forUpcall);
        BindingCalculator argCalc = forUpcall ? new BoxBindingCalculator(true) : new UnboxBindingCalculator(true);
        BindingCalculator retCalc = forUpcall ? new UnboxBindingCalculator(false) : new BoxBindingCalculator(false);

        boolean returnInMemory = isInMemoryReturn(cDesc.returnLayout());
        if (returnInMemory) {
            Class<?> carrier = MemoryAddress.class;
            MemoryLayout layout = PlatformLayouts.LinuxRISCV64.C_POINTER;
            csb.addArgumentBindings(carrier, layout, argCalc.getBindings(carrier, layout, false));
        } else if (cDesc.returnLayout().isPresent()) {
            Class<?> carrier = mt.returnType();
            MemoryLayout layout = cDesc.returnLayout().get();
            csb.setReturnBindings(carrier, layout, retCalc.getBindings(carrier, layout, false));
        }

        for (int i = 0; i < mt.parameterCount(); i++) {
            Class<?> carrier = mt.parameterType(i);
            MemoryLayout layout = cDesc.argumentLayouts().get(i);
            boolean isVar = cDesc.firstVariadicArgumentIndex() != -1 && i >= cDesc.firstVariadicArgumentIndex();
            csb.addArgumentBindings(carrier, layout, argCalc.getBindings(carrier, layout, isVar));
        }

        if (forUpcall){
            UnboxBindingCalculator unboxCalc = (UnboxBindingCalculator) retCalc;
            if (unboxCalc.lengthSensitive) {
                return new CallArranger.Bindings(csb.build(), returnInMemory, true,
                                                 unboxCalc.offset, unboxCalc.length);
            }
        }

        return new CallArranger.Bindings(csb.build(), returnInMemory);
    }

    public static MethodHandle arrangeDowncall(MethodType mt, FunctionDescriptor cDesc) {
        CallArranger.Bindings bindings = getBindings(mt, cDesc, false);

        MethodHandle handle = new DowncallLinker(CLinux, bindings.callingSequence).getBoundMethodHandle();

        if (bindings.isInMemoryReturn) {
            handle = SharedUtils.adaptDowncallForIMR(handle, cDesc);
        }

        return handle;
    }

    public static MemorySegment arrangeUpcall(MethodHandle target, MethodType mt, FunctionDescriptor cDesc, MemorySession session) {
        CallArranger.Bindings bindings = getBindings(mt, cDesc, true);

        if (bindings.isInMemoryReturn) {
            target = SharedUtils.adaptUpcallForIMR(target, true /* drop return, since we don't have bindings for it */);
        }
        if (bindings.lengthSensitive)
            return UpcallLinker.make(CLinux, target, bindings.callingSequence, session, true, bindings.offset, bindings.length);
        return UpcallLinker.make(CLinux, target, bindings.callingSequence, session,false, null, null);
    }

    private static boolean isInMemoryReturn(Optional<MemoryLayout> returnLayout) {
        return returnLayout
                .filter(GroupLayout.class::isInstance)
                .filter(g -> TypeClass.classifyLayout(g) == TypeClass.STRUCT_REFERENCE)
                .isPresent();
    }

    static class StorageCalculator {

        private final boolean forArguments;

        private final int[] nRegs = {0, 0}; // integerRegIdx=0, floatRegIdx=1
        private long stackOffset = 0;
        final static int MAX_REGISTER_ARGUMENTS = 8;

        @Override
        public String toString() {
            var nreg = "iReg: " + nRegs[0] + ", fReg: " + nRegs[1];
            var stack = ", stackOffset: " + stackOffset;
            return "{" + nreg + stack + "}";
        }

        public StorageCalculator(boolean forArguments) {
            this.forArguments = forArguments;
        }

        VMStorage stackAlloc(long size, long alignment) {
            assert forArguments : "no stack returns";
            // Implementation limit: each arg must take up at least an 8 byte stack slot (on the Java side)
            // There is currently no way to address stack offsets that are not multiples of 8 bytes
            // The VM can only address multiple-of-4-bytes offsets, which is also not good enough for some ABIs
            // see JDK-8283462 and related issues
            long stackSlotAlignment = Math.max(alignment, STACK_SLOT_SIZE);

            stackOffset = Utils.alignUp(stackOffset, stackSlotAlignment);

            VMStorage storage =
                    stackStorage((int) (stackOffset / STACK_SLOT_SIZE));
            stackOffset += size;
            return storage;
        }

        VMStorage[] regAlloc(int type, int count, boolean isSFA, boolean variadic) {
            VMStorage[] result = new VMStorage[0];
            var space = MAX_REGISTER_ARGUMENTS - nRegs[type];
            if (variadic && space < count) {
                // In the base integer calling convention,
                // variadic arguments are passed in the same manner as named arguments, with one exception.
                // Variadic arguments with 2xXLEN-bit alignment and
                // size at most 2xXLEN bits are passed in an aligned register pair
                // (i.e., the first register in the pair is even-numbered),
                // or on the stack by value if none is available.
                assert false : "should not reach here.";
                // nRegs[type] = MAX_REGISTER_ARGUMENTS;
            }

            if (isSFA && space < count) {
                return result;
            }

            if (space > 0) {
                var allocCnt = Math.min(space, count);
                VMStorage[] source =
                        (forArguments ? CLinux.inputStorage : CLinux.outputStorage)[type];
                result = new VMStorage[allocCnt];
                for (int i = 0; i < allocCnt; i++) {
                    result[i] = source[nRegs[type] + i];
                }
                if (forArguments) nRegs[type] += allocCnt;

                return result;
            }
            return result;
        }

        private VMStorage[] mergeVMStorageArray(VMStorage[] p1, VMStorage[] p2) {
            VMStorage[] ret = new VMStorage[p1.length + p2.length];
            System.arraycopy(p1, 0, ret, 0, p1.length);
            System.arraycopy(p2, 0, ret, p1.length, p2.length);
            return ret;
        }

        VMStorage[] allocStorage(int type, MemoryLayout layout, boolean isSFA) {
            int regCnt;
            if (isSFA) {
                regCnt = getFlattenedFields((GroupLayout) layout).size();
            } else {
                regCnt = ((int) Utils.alignUp(layout.byteSize(), 8)) / 8;
            }
            VMStorage[] first = regAlloc(type, regCnt, isSFA, false);
            int spillCnt = regCnt - first.length;
            if (spillCnt == 0 || isSFA) return first;
            VMStorage[] second;
            if (type == StorageClasses.FLOAT) {
                second = regAlloc(StorageClasses.INTEGER, spillCnt, false, false);
                int lastCnt = spillCnt - second.length;
                if (lastCnt > 0) {
                    VMStorage[] last = new VMStorage[lastCnt];
                    for (int i = 0; i < lastCnt; ++i)
                        last[i] = stackAlloc(STACK_SLOT_SIZE, STACK_SLOT_SIZE);
                    second = mergeVMStorageArray(second, last);
                }
            } else {
                second = new VMStorage[spillCnt];
                for (int i = 0; i < spillCnt; ++i)
                    second[i] = stackAlloc(STACK_SLOT_SIZE, STACK_SLOT_SIZE);
            }
            return mergeVMStorageArray(first, second);
        }

        VMStorage nextStorage(int type, MemoryLayout layout) {
            if (!(layout instanceof ValueLayout))
                throw new IllegalArgumentException("this method only allocate stroage for ValueLayout");
            return allocStorage(type, layout, false)[0];
        }

    }

    abstract static class BindingCalculator {
        protected final StorageCalculator storageCalculator;

        @Override
        public String toString() {
            return storageCalculator.toString();
        }

        protected BindingCalculator(boolean forArguments) {
            this.storageCalculator = new CallArranger.StorageCalculator(forArguments);
        }

        abstract List<Binding> getBindings(Class<?> carrier, MemoryLayout layout, boolean isVariadicArg);
    }

    static class UnboxBindingCalculator extends BindingCalculator {

        boolean lengthSensitive;
        boolean forArguments;
        long[] offset;
        long[] length;
        UnboxBindingCalculator(boolean forArguments) {
            super(forArguments);
            this.forArguments = forArguments;
        }

        @Override
        List<Binding> getBindings(Class<?> carrier, MemoryLayout layout, boolean isVariadicArg) {
            TypeClass typeClass = TypeClass.classifyLayout(layout);
            if (isVariadicArg) {
                typeClass = typeClass == FLOAT ? INTEGER : typeClass;
                typeClass = typeClass == STRUCT_SFA ? STRUCT_REGISTER : typeClass;
                typeClass = typeClass == STRUCT_IAF ? STRUCT_REGISTER : typeClass;
            }
            return getBindings(carrier, layout, typeClass);
        }

        List<Binding> getBindings(Class<?> carrier, MemoryLayout layout, TypeClass argumentClass) {
            Binding.Builder bindings = Binding.builder();
            switch (argumentClass) {
                case POINTER -> {
                    bindings.unboxAddress(carrier);
                    VMStorage storage = storageCalculator.nextStorage(StorageClasses.INTEGER, layout);
                    bindings.vmStore(storage, long.class);
                }

                case INTEGER -> {
                    VMStorage storage = storageCalculator.nextStorage(StorageClasses.INTEGER, layout);
                    bindings.vmStore(storage, carrier);
                }

                case FLOAT -> {
                    VMStorage storage = storageCalculator.nextStorage(StorageClasses.FLOAT, layout);
                    bindings.vmStore(storage, carrier);
                }

                case STRUCT_SFA -> {
                    assert carrier == MemorySegment.class;
                    List<FlattenedField> offsetArr = getFlattenedFields((GroupLayout) layout);
                    int fieldCnt = offsetArr.size();
                    VMStorage[] locations = storageCalculator.allocStorage(
                            StorageClasses.FLOAT, layout, true);
                    if (fieldCnt == locations.length) {
                        if (!forArguments) {
                            lengthSensitive = true;
                            this.length = new long[fieldCnt];
                            this.offset = new long[fieldCnt];
                        }
                        for (int i = 0; i < locations.length; ++i) {
                            int offset = offsetArr.get(i).offset();
                            Class<?> type = SharedUtils.primitiveCarrierForSize(offsetArr.get(i).byteSize(),
                                                                                true);
                            VMStorage storage = locations[i];
                            if (i == 0 && locations.length > 1) bindings.dup();
                            bindings.bufferLoad(offset, type)
                                    .vmStore(storage, type);
                            if (!forArguments) {
                                this.offset[i] = i * 8L;
                                if (type == float.class) {
                                    this.length[i] = 4;
                                } else if (type == double.class) {
                                    this.length[i] = 8;
                                } else {
                                    throw new IllegalArgumentException("can not handle field type: " + type);
                                }
                            }
                        }
                    } else {
                        assert locations.length == 0 : "allocate float reg fail, must be zero.";
                        return getBindings(carrier, layout, STRUCT_REGISTER);
                    }
                }
                case STRUCT_IAF -> {
                    assert carrier == MemorySegment.class;
                    if (storageCalculator.nRegs[0] < StorageCalculator.MAX_REGISTER_ARGUMENTS &&
                            storageCalculator.nRegs[1] < StorageCalculator.MAX_REGISTER_ARGUMENTS) {
                        if (!forArguments) {
                            lengthSensitive = true;
                            this.length = new long[2];
                            this.offset = new long[2];
                        }
                        VMStorage[] locations = new VMStorage[2];
                        locations[0] = storageCalculator.regAlloc(StorageClasses.INTEGER, 1, false, false)[0];
                        locations[1] = storageCalculator.regAlloc(StorageClasses.FLOAT, 1, false, false)[0];
                        int reminder = 2;
                        int i = 0;
                        List<FlattenedField> flattenedFields = getFlattenedFields((GroupLayout) layout);
                        for (FlattenedField field : flattenedFields) {
                            if (field.typeClass() == INTEGER){
                                Class<?> type = SharedUtils.primitiveCarrierForSize(field.byteSize(),
                                                                                    false);
                                if (--reminder > 0) bindings.dup();
                                bindings.bufferLoad(field.offset(), type)
                                        .vmStore(locations[0], type);
                            } else if (field.typeClass() == FLOAT) {
                                Class<?> type = SharedUtils.primitiveCarrierForSize(field.byteSize(),
                                                                                    true);
                                if (--reminder > 0) bindings.dup();
                                bindings.bufferLoad(field.offset(), type)
                                        .vmStore(locations[1], type);
                            } else {
                                throw new UnsupportedOperationException("Unhandled field " + field.typeClass());
                            }

                            assert reminder >= 0;
                            if (!forArguments) {
                                this.offset[i] = i * 8L;
                                this.length[i] = field.byteSize();
                            }
                            i += 1;
                        }
                    } else
                        return getBindings(carrier, layout, STRUCT_REGISTER);
                }
                case STRUCT_REGISTER -> {
                    assert carrier == MemorySegment.class;
                    VMStorage[] locations = storageCalculator.allocStorage(
                            StorageClasses.INTEGER, layout, false);
                    int locIndex = 0;
                    long offset = 0;
                    while (offset < layout.byteSize()) {
                        final long copy = Math.min(layout.byteSize() - offset, 8);
                        VMStorage storage = locations[locIndex++];
                        boolean useFloat = storage.type() == StorageClasses.FLOAT;
                        Class<?> type = SharedUtils.primitiveCarrierForSize(copy, useFloat);
                        if (offset + copy < layout.byteSize()) {
                            bindings.dup();
                        }
                        bindings.bufferLoad(offset, type)
                                .vmStore(storage, type);
                        offset += copy;
                    }
                }
                case STRUCT_REFERENCE -> {
                    assert carrier == MemorySegment.class;
                    bindings.copy(layout)
                            .unboxAddress(MemorySegment.class);
                    VMStorage storage = storageCalculator.nextStorage(
                            StorageClasses.INTEGER, PlatformLayouts.LinuxRISCV64.C_POINTER);
                    bindings.vmStore(storage, long.class);
                }
                default -> throw new UnsupportedOperationException("Unhandled class " + argumentClass);
            }
            return bindings.build();
        }
    }

    static class BoxBindingCalculator extends BindingCalculator {

        BoxBindingCalculator(boolean forArguments) {
            super(forArguments);
        }

        @Override
        List<Binding> getBindings(Class<?> carrier, MemoryLayout layout, boolean isVariadicArg) {
            TypeClass typeClass = TypeClass.classifyLayout(layout);
            if (isVariadicArg) {
                typeClass = typeClass == TypeClass.FLOAT ? TypeClass.INTEGER : typeClass;
                typeClass = typeClass == TypeClass.STRUCT_SFA ? STRUCT_REGISTER : typeClass;
            }
            return getBindings(carrier, layout, typeClass);
        }

        List<Binding> getBindings(Class<?> carrier, MemoryLayout layout, TypeClass argumentClass) {
            Binding.Builder bindings = Binding.builder();
            switch (argumentClass) {
                case STRUCT_REGISTER -> {
                    assert carrier == MemorySegment.class;
                    bindings.allocate(layout);
                    VMStorage[] locations = storageCalculator.allocStorage(
                            StorageClasses.INTEGER, layout, false);
                    int locIndex = 0;
                    long offset = 0;
                    while (offset < layout.byteSize()) {
                        final long copy = Math.min(layout.byteSize() - offset, 8);
                        VMStorage storage = locations[locIndex++];
                        boolean useFloat = storage.type() == StorageClasses.FLOAT;
                        Class<?> type = SharedUtils.primitiveCarrierForSize(copy, useFloat);
                        bindings.dup()
                                .vmLoad(storage, type)
                                .bufferStore(offset, type);
                        offset += copy;
                    }
                }
                case STRUCT_REFERENCE -> {
                    assert carrier == MemorySegment.class;
                    VMStorage storage = storageCalculator.nextStorage(
                            StorageClasses.INTEGER, PlatformLayouts.LinuxRISCV64.C_POINTER);
                    bindings.vmLoad(storage, long.class)
                            .boxAddress()
                            .toSegment(layout);
                }
                case STRUCT_IAF -> {
                    assert carrier == MemorySegment.class;
                    bindings.allocate(layout);
                    if (storageCalculator.nRegs[0] < StorageCalculator.MAX_REGISTER_ARGUMENTS &&
                            storageCalculator.nRegs[1] < StorageCalculator.MAX_REGISTER_ARGUMENTS) {
                        List<FlattenedField> flattenedFields = getFlattenedFields((GroupLayout) layout);
                        VMStorage[] locations = new VMStorage[2];
                        locations[0] = storageCalculator.regAlloc(StorageClasses.INTEGER, 1, false, false)[0];
                        locations[1] = storageCalculator.regAlloc(StorageClasses.FLOAT, 1, false, false)[0];
                        for (FlattenedField field : flattenedFields) {
                            if (field.typeClass() == INTEGER) {
                                Class<?> type = SharedUtils.primitiveCarrierForSize(field.byteSize(), false);
                                bindings.dup()
                                        .vmLoad(locations[0], type)
                                        .bufferStore(field.offset(), type);
                            } else if (field.typeClass() == FLOAT) {
                                Class<?> type = SharedUtils.primitiveCarrierForSize(field.byteSize(), true);
                                bindings.dup()
                                        .vmLoad(locations[1], type)
                                        .bufferStore(field.offset(), type);
                            } else {
                                throw new UnsupportedOperationException("Unhandled field " + field.typeClass());
                            }
                        }
                    } else
                        return getBindings(carrier, layout, STRUCT_REGISTER);
                }
                case STRUCT_SFA -> {
                    assert carrier == MemorySegment.class;
                    bindings.allocate(layout);
                    List<FlattenedField> offsetArr = getFlattenedFields((GroupLayout) layout);
                    int fieldCnt = offsetArr.size();
                    VMStorage[] locations = storageCalculator.allocStorage(
                            StorageClasses.FLOAT, layout, true);
                    if (fieldCnt == locations.length) {
                        for (int i = 0; i < locations.length; ++i) {
                            int offset = offsetArr.get(i).offset();
                            Class<?> type = SharedUtils.primitiveCarrierForSize(offsetArr.get(i).byteSize(),
                                                                                true);
                            VMStorage storage = locations[i];
                            bindings.dup()
                                    .vmLoad(storage, type)
                                    .bufferStore(offset, type);
                        }
                    } else {
                        assert locations.length == 0 : "allocate float reg fail, must be zero.";
                        return getBindings(carrier, layout, STRUCT_REGISTER);
                    }

                }
                case POINTER -> {
                    VMStorage storage = storageCalculator.nextStorage(StorageClasses.INTEGER, layout);
                    bindings.vmLoad(storage, long.class)
                            .boxAddress();
                }
                case INTEGER -> {
                    VMStorage storage = storageCalculator.nextStorage(StorageClasses.INTEGER, layout);
                    bindings.vmLoad(storage, carrier);
                }
                case FLOAT -> {
                    VMStorage storage = storageCalculator.nextStorage(StorageClasses.FLOAT, layout);
                    bindings.vmLoad(storage, carrier);
                }
                default -> throw new UnsupportedOperationException("Unhandled class " + argumentClass);
            }
            return bindings.build();
        }
    }

}

