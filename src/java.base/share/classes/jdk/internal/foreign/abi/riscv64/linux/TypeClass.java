package jdk.internal.foreign.abi.riscv64.linux;


import java.lang.foreign.*;
import java.util.ArrayList;
import java.util.List;

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

    private static record FlattenCounter(long integerCnt, long floatCnt, long pointerCnt) {
        static final FlattenCounter EMPTY = new FlattenCounter(0, 0, 0);
        static final FlattenCounter OneInteger = new FlattenCounter(1, 0, 0);
        static final FlattenCounter OneFloat = new FlattenCounter(0, 1, 0);
        static final FlattenCounter OnePointer = new FlattenCounter(0, 0, 1);

        FlattenCounter mul(long m) {
            return new FlattenCounter(integerCnt * m,
                                      floatCnt * m,
                                      pointerCnt * m);
        }

        FlattenCounter add(FlattenCounter other) {
            return new FlattenCounter(integerCnt + other.integerCnt,
                                      floatCnt + other.floatCnt,
                                      pointerCnt + other.pointerCnt);
        }

        boolean isSFA() {
            return integerCnt == 0 && pointerCnt == 0 &&
                    (floatCnt == 1 || floatCnt == 2);
        }

        boolean isIAF() {
            return integerCnt == 1 && floatCnt == 1 && pointerCnt == 0;
        }
    }

    public static record FlattenedField(TypeClass typeClass, int offset, int byteSize) {

    }

    private static List<FlattenedField> getFlattenedFieldsInner(int offset, MemoryLayout layout) {
        if (layout instanceof ValueLayout valueLayout) {
            return List.of(switch (classifyValueType(valueLayout)) {
                case INTEGER -> new FlattenedField(INTEGER, offset, (int) valueLayout.byteSize());
                case FLOAT -> new FlattenedField(FLOAT, offset, (int) valueLayout.byteSize());
                case POINTER -> new FlattenedField(POINTER, offset, (int) valueLayout.byteSize());
                default -> {
                    assert false : "should not reach here.";
                    yield null; /* should not reach here. */
                }
            });
        } else if (layout instanceof GroupLayout groupLayout) {
            List<FlattenedField> fields = new ArrayList<>();
            for (MemoryLayout memberLayout : groupLayout.memberLayouts()) {
                if (memberLayout.isPadding()) {
                    offset += memberLayout.byteSize();
                    continue;
                }
                fields.addAll(getFlattenedFieldsInner(offset, memberLayout));
                offset += memberLayout.byteSize();
            }
            return fields;
        } else if (layout instanceof SequenceLayout sequenceLayout) {
            List<FlattenedField> fields = new ArrayList<>();
            MemoryLayout elementLayout = sequenceLayout.elementLayout();
            for (long i = 0; i < sequenceLayout.elementCount(); i++) {
                fields.addAll(getFlattenedFieldsInner(offset, elementLayout));
                offset += elementLayout.byteSize();
            }
            return fields;
        } else {
            throw new IllegalStateException("Cannot get here: " + layout);
        }
    }

    public static List<FlattenedField> getFlattenedFields(GroupLayout layout) {
        assert classifyLayout(layout) == TypeClass.STRUCT_SFA || classifyLayout(layout) == TypeClass.STRUCT_IAF :
                "Unexpected argument: " + layout;
        return getFlattenedFieldsInner(0, layout);
    }

    private static class AggregateClassifier {
        private FlattenCounter countFlattenedLayout(MemoryLayout layout) {
            if (layout instanceof ValueLayout valueLayout) {
                return switch (classifyValueType(valueLayout)) {
                    case INTEGER -> FlattenCounter.OneInteger;
                    case FLOAT -> FlattenCounter.OneFloat;
                    case POINTER -> FlattenCounter.OnePointer;
                    default -> {
                        assert false : "should not reach here.";
                        yield null; /* should not reach here. */
                    }
                };
            } else if (layout instanceof GroupLayout groupLayout) {
                FlattenCounter currCounter = FlattenCounter.EMPTY;
                for (MemoryLayout memberLayout : groupLayout.memberLayouts()) {
                    if (memberLayout.isPadding()) continue;
                    currCounter = currCounter.add(countFlattenedLayout(memberLayout));
                }
                return currCounter;
            } else if (layout instanceof SequenceLayout sequenceLayout) {
                long elementCount = sequenceLayout.elementCount();
                return countFlattenedLayout(sequenceLayout.elementLayout()).mul(elementCount);
            } else {
                throw new IllegalStateException("Cannot get here: " + layout);
            }
        }

        /*
         * A struct containing two floating-point reals is passed in two floating-point registers,
         * if neither real is more than ABI_FLEN bits wide and
         * at least two floating-point argument registers are available.
         * (The registers need not be an aligned pair.)
         * Otherwise, it is passed according to the integer calling convention.
         * */
        private boolean isSimpleFloatAggregate(GroupLayout groupLayout) {
            if (groupLayout.byteSize() > 8 * MAX_AGGREGATE_REGS_SIZE) return false;
            return countFlattenedLayout(groupLayout).isSFA();
        }

        // pointer is not a integer.
        private boolean isIAFAggregate(GroupLayout groupLayout) {
            if (groupLayout.byteSize() > 8 * MAX_AGGREGATE_REGS_SIZE) return false;
            return countFlattenedLayout(groupLayout).isIAF();
        }

        private boolean isRegisterAggregate(MemoryLayout type) {
            return type.byteSize() <= MAX_AGGREGATE_REGS_SIZE * 8;
        }

        private TypeClass classifyStructType(GroupLayout layout) {
            if (layout.isUnion()) {
                if (isRegisterAggregate(layout)) {
                    return STRUCT_REGISTER;
                }
                return STRUCT_REFERENCE;
            }
            if (isSimpleFloatAggregate(layout)) {
                return STRUCT_SFA;
            } else if (isIAFAggregate(layout)) {
                return STRUCT_IAF;
            } else if (isRegisterAggregate(layout)) {
                return STRUCT_REGISTER;
            }
            return STRUCT_REFERENCE;
        }
    }


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


    private static TypeClass classifyStructType(GroupLayout layout) {
        return (new AggregateClassifier()).classifyStructType(layout);
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
}
