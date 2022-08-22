package jdk.internal.foreign.abi.riscv64.linux;


public enum ArgumentClassImpl {
    POINTER, INTEGER, FLOAT;

    public boolean isIntegral() {
        return this == INTEGER || this == POINTER;
    }

    public boolean isPointer() {
        return this == POINTER;
    }

    public boolean isFloat() {
        return this == FLOAT;
    }
}