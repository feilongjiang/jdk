package jdk.internal.foreign.abi.rv64.linux;


import java.lang.foreign.*;

public enum TypeClass {
    SEQUENCE,
    STRUCT_REFERENCE,
    STRUCT_REGISTER,
    STRUCT_SFA, // simple float aggregate.
    STRUCT_IAF, // a Integer And a Float.
    POINTER,
    INTEGER,
    FLOAT;

    private static final int MAX_AGGREGATE_REGS_SIZE = 2;

    private static TypeClass classifyValueType(ValueLayout type) {
        Class<?> carrier = type.carrier();
        if (carrier == boolean.class || carrier == byte.class || carrier == char.class ||
                carrier == short.class || carrier == int.class || carrier == long.class) {
            return INTEGER;
        } else if (carrier == float.class || carrier == double.class) {
            return FLOAT;
        } else if (carrier == MemoryAddress.class) {
            return POINTER;
        } else {
            throw new IllegalStateException("Cannot get here: " + carrier.getName());
        }
    }


    static boolean isRegisterAggregate(MemoryLayout type) {
        return type.bitSize() <= MAX_AGGREGATE_REGS_SIZE * 64;
    }

    /*
     * A struct containing two floating-point reals is passed in two floating-point registers,
     * if neither real is more than ABI_FLEN bits wide and
     * at least two floating-point argument registers are available.
     * (The registers need not be an aligned pair.)
     * Otherwise, it is passed according to the integer calling convention.
     * */
    static boolean isHomogeneousFloatAggregate(GroupLayout groupLayout) {
        int totalBitSize = 0;
        int floatCnt = 0;
        for (MemoryLayout elem : groupLayout.memberLayouts()) {
            totalBitSize += elem.bitSize();
            if (totalBitSize > 64 * MAX_AGGREGATE_REGS_SIZE) return false;
            if (elem.isPadding()) continue;
            if (!(elem instanceof ValueLayout)) return false;
            if (classifyValueType((ValueLayout) elem) == FLOAT)
                floatCnt += 1;
            else
                return false;
            if (floatCnt > 2) return false;
        }
        return floatCnt > 0;
    }

    // pointer is not a integer.
    static boolean isIAFAggregate(GroupLayout groupLayout) {
        int floatCnt = 0;
        int intCnt = 0;
        for (MemoryLayout elem : groupLayout.memberLayouts()) {
            if (elem.isPadding()) continue;
            switch (classifyElementLayout(elem)){
                case INTEGER -> intCnt++;
                case FLOAT -> floatCnt++;
                default -> {
                    return false;
                }
            }
        }
        return floatCnt == 1 && intCnt == 1;
    }


    private static TypeClass classifyStructType(GroupLayout layout) {
        if (layout.isUnion()) {
            if (isRegisterAggregate(layout)) {
                return STRUCT_REGISTER;
            }
            return STRUCT_REFERENCE;
        }
        if (isHomogeneousFloatAggregate(layout)) {
            return STRUCT_SFA;
        } else if(isIAFAggregate(layout)){
            return STRUCT_IAF;
        } else if (isRegisterAggregate(layout)) {
            return STRUCT_REGISTER;
        }
        return STRUCT_REFERENCE;
    }


    static TypeClass classifyLayout(MemoryLayout type) {
        if (type instanceof ValueLayout vt) {
            return classifyValueType(vt);
        } else if (type instanceof GroupLayout gt) {
            return classifyStructType(gt);
        } else {
            throw new IllegalArgumentException("Unhandled type " + type);
        }
    }

    static TypeClass classifyElementLayout(MemoryLayout type){
        if (type instanceof ValueLayout vt) {
            return classifyValueType(vt);
        } else if (type instanceof GroupLayout gt) {
            return classifyStructType(gt);
        } else if (type instanceof SequenceLayout){
            return SEQUENCE;
        }else {
            throw new IllegalArgumentException("Unhandled type " + type);
        }

    }

}
