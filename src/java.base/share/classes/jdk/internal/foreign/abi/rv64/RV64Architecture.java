package jdk.internal.foreign.abi.rv64;

import jdk.internal.foreign.abi.ABIDescriptor;
import jdk.internal.foreign.abi.Architecture;
import jdk.internal.foreign.abi.VMStorage;


public class RV64Architecture implements Architecture {
    public static final Architecture INSTANCE = new RV64Architecture();

    private static final int INTEGER_REG_SIZE = 8; // bytes
    private static final int FLOAT_REG_SIZE = 8;
    private static final int STACK_SLOT_SIZE = 8;
    // https://github.com/riscv-non-isa/riscv-elf-psabi-doc/issues/21#issuecomment-292990546
    // https://riscv.org/wp-content/uploads/2015/01/riscv-calling.pdf

    public interface StorageClasses {
        int INTEGER = 0;
        int FLOAT = 1;
        int STACK = 3;
    }

    public static final VMStorage x0 = integerRegister(0, "zero");    // zero
    public static final VMStorage x1 = integerRegister(1, "ra");      // ra
    public static final VMStorage x2 = integerRegister(2, "sp");      // sp
    public static final VMStorage x3 = integerRegister(3, "gp");      // gp
    public static final VMStorage x4 = integerRegister(4, "tp");      // tp
    public static final VMStorage x5 = integerRegister(5, "t0");      // t0
    public static final VMStorage x6 = integerRegister(6, "t1");      // t1
    public static final VMStorage x7 = integerRegister(7, "t2");      // t2
    public static final VMStorage x8 = integerRegister(8, "s0/fp");   // s0/fp
    public static final VMStorage x9 = integerRegister(9, "s1");      // s1
    public static final VMStorage x10 = integerRegister(10, "a0");    // a0
    public static final VMStorage x11 = integerRegister(11, "a1");    // a1
    public static final VMStorage x12 = integerRegister(12, "a2");    // a2
    public static final VMStorage x13 = integerRegister(13, "a3");    // a3
    public static final VMStorage x14 = integerRegister(14, "a4");    // a4
    public static final VMStorage x15 = integerRegister(15, "a5");    // a5
    public static final VMStorage x16 = integerRegister(16, "a6");    // a6
    public static final VMStorage x17 = integerRegister(17, "a7");    // a7
    public static final VMStorage x18 = integerRegister(18, "s2");    // s2
    public static final VMStorage x19 = integerRegister(19, "s3");    // s3
    public static final VMStorage x20 = integerRegister(20, "s4");    // s4
    public static final VMStorage x21 = integerRegister(21, "s5");    // s5
    public static final VMStorage x22 = integerRegister(22, "s6");    // s6
    public static final VMStorage x23 = integerRegister(23, "s7");    // s7
    public static final VMStorage x24 = integerRegister(24, "s8");    // s8
    public static final VMStorage x25 = integerRegister(25, "s9");    // s9
    public static final VMStorage x26 = integerRegister(26, "s10");   // s10
    public static final VMStorage x27 = integerRegister(27, "s11");   // s11
    public static final VMStorage x28 = integerRegister(28, "t3");    // t3
    public static final VMStorage x29 = integerRegister(29, "t4");    // t4
    public static final VMStorage x30 = integerRegister(30, "t5");    // t5
    public static final VMStorage x31 = integerRegister(31, "t6");    // t6

    public static final VMStorage f0 = floatRegister(0, "ft0");     // ft0
    public static final VMStorage f1 = floatRegister(1, "ft1");     // ft1
    public static final VMStorage f2 = floatRegister(2, "ft2");     // ft2
    public static final VMStorage f3 = floatRegister(3, "ft3");     // ft3
    public static final VMStorage f4 = floatRegister(4, "ft4");     // ft4
    public static final VMStorage f5 = floatRegister(5, "ft5");     // ft5
    public static final VMStorage f6 = floatRegister(6, "ft6");     // ft6
    public static final VMStorage f7 = floatRegister(7, "ft7");     // ft7
    public static final VMStorage f8 = floatRegister(8, "fs0");     // fs0
    public static final VMStorage f9 = floatRegister(9, "fs1");     // fs1
    public static final VMStorage f10 = floatRegister(10, "fa0");    // fa0
    public static final VMStorage f11 = floatRegister(11, "fa1");    // fa1
    public static final VMStorage f12 = floatRegister(12, "fa2");    // fa2
    public static final VMStorage f13 = floatRegister(13, "fa3");    // fa3
    public static final VMStorage f14 = floatRegister(14, "fa4");    // fa4
    public static final VMStorage f15 = floatRegister(15, "fa5");    // fa5
    public static final VMStorage f16 = floatRegister(16, "fa6");    // fa6
    public static final VMStorage f17 = floatRegister(17, "fa7");    // fa7
    public static final VMStorage f18 = floatRegister(18, "fs2");    // fs2
    public static final VMStorage f19 = floatRegister(19, "fs3");    // fs3
    public static final VMStorage f20 = floatRegister(20, "fs4");    // fs4
    public static final VMStorage f21 = floatRegister(21, "fs5");    // fs5
    public static final VMStorage f22 = floatRegister(22, "fs6");    // fs6
    public static final VMStorage f23 = floatRegister(23, "fs7");     // fs7
    public static final VMStorage f24 = floatRegister(24, "fs8");     // fs8
    public static final VMStorage f25 = floatRegister(25, "fs9");     // fs9
    public static final VMStorage f26 = floatRegister(26, "fs10");    // fs10
    public static final VMStorage f27 = floatRegister(27, "fs11");    // fs11
    public static final VMStorage f28 = floatRegister(28, "ft8");     // ft8
    public static final VMStorage f29 = floatRegister(29, "ft9");     // ft9
    public static final VMStorage f30 = floatRegister(30, "ft10");    // ft10
    public static final VMStorage f31 = floatRegister(31, "ft11");    // ft11

    private static VMStorage integerRegister(int index, String debugName) {
        return new VMStorage(RV64Architecture.StorageClasses.INTEGER, index, debugName);
    }

    private static VMStorage floatRegister(int index, String debugName) {
        return new VMStorage(RV64Architecture.StorageClasses.FLOAT, index, debugName);
    }

    public static VMStorage stackStorage(int index) {
        return new VMStorage(RV64Architecture.StorageClasses.STACK, index, "Stack@" + index);
    }

    @Override
    public boolean isStackType(int cls) {
        return cls == StorageClasses.STACK;
    }

    @Override
    public int typeSize(int cls) {
        switch (cls) {
            case StorageClasses.INTEGER: return INTEGER_REG_SIZE;
            case StorageClasses.FLOAT: return FLOAT_REG_SIZE;
            case StorageClasses.STACK: return STACK_SLOT_SIZE;
        }

        throw new IllegalArgumentException("Invalid Storage Class: " + cls);

    }

    @Override
    public int stackType() {
        return StorageClasses.STACK;
    }

    public static ABIDescriptor abiFor(VMStorage[] inputIntRegs, VMStorage[] inputFloatRegs, VMStorage[] outputIntRegs,
                                       VMStorage[] outputFloatRegs, VMStorage[] volatileIntRegs,
                                       VMStorage[] volatileFloatRegs, int stackAlignment, int shadowSpace,
                                       VMStorage targetAddrStorage, VMStorage retBufAddrStorage) {
        return new ABIDescriptor(
                INSTANCE,
                new VMStorage[][]{
                        inputIntRegs,
                        inputFloatRegs,
                },
                new VMStorage[][]{
                        outputIntRegs,
                        outputFloatRegs,
                },
                new VMStorage[][]{
                        volatileIntRegs,
                        volatileFloatRegs,
                },
                stackAlignment,
                shadowSpace,
                targetAddrStorage, retBufAddrStorage);
    }

}
