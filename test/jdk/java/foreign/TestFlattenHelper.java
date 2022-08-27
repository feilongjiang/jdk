import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.foreign.*;
import java.util.*;
import java.util.stream.Collectors;

import static java.lang.foreign.ValueLayout.*;
import static org.testng.Assert.*;

class Permutation {
    static private class FieldRecord {
        private final ArrayList<Component> fields = new ArrayList<>();

        Component[] toArray() {
            return fields.toArray(Component[]::new);
        }

        FieldRecord addField(Component component) {
            var newRecord = new FieldRecord();
            newRecord.fields.addAll(fields);
            newRecord.fields.add(component);
            return newRecord;
        }
    }

    static private class StructPermutation {
        ArrayList<FieldRecord> ret;
        List<Component> candidates;
        int fieldCnt;

        StructPermutation(List<Component> candidates, int fieldCnt) {
            this.candidates = candidates;
            this.fieldCnt = fieldCnt;
        }

        private void doPermutation(int cnt, FieldRecord record) {
            if (cnt == 0) {
                ret.add(record);
                return;
            }
            for (Component candidate : candidates) {
                if (cnt == fieldCnt) record = new FieldRecord();
                doPermutation(cnt - 1, record.addField(candidate));
            }
        }

        ArrayList<FieldRecord> getAll() {
            ret = new ArrayList<>();
            doPermutation(fieldCnt, new FieldRecord());
            return ret;
        }


    }


    static List<Component> getCandidate(int arrLength, int fieldCnt, int depth) {
        var a = new ArrayList<Component>(Value.all());
        if (depth >= 0) a.addAll(permutationSeq(arrLength, fieldCnt, depth));
        if (depth <= 0) return a;
        a.addAll(permutationStruct(arrLength, fieldCnt, depth - 1));
        return a;
    }

    static List<Component> permutationSeq(int arrLength, int fieldCnt, int depth) {
        var elementCandidate = getCandidate(arrLength, fieldCnt, depth - 1);
        var list = new ArrayList<Component>();
        for (Component component : elementCandidate) {
            for (int i = 0; i < arrLength; i++) {
                list.add(Sequence.c(i, component));
            }
        }
        return list;
    }

    static List<Component> permutationStruct(int arrLength, int fieldCnt, int depth) {
        var list = new ArrayList<Component>();
        var fieldsCandidate = getCandidate(arrLength, fieldCnt, depth - 1);
        for (int i = 0; i < fieldCnt; i++) {
            var tt = (new StructPermutation(fieldsCandidate, i)).getAll();
            for (var components : tt) {
                list.add(Struct.c(components.toArray()));
            }
        }
        return list;
    }
}

sealed interface Component permits Value, Sequence, Struct {
    long getAlignment();

    long getByteSize();

    MemoryLayout toMemoryLayout();

    String toName();

    String toCDecl(String name);

    String toCType();

    default MemorySegment getMemorySegment(MemorySession session) {
        return session.allocate(toMemoryLayout());
    }

    void initValue(MemorySegment segment);

    String dumpValue(MemorySegment segment);

    void check(MemorySegment segment);
}


enum Value implements Component {
    C_BOOL, C_CHAR, C_SHORT, C_INT, C_LONG,
    C_FLOAT, C_DOUBLE;

    @Override
    public String toString() {
        return switch (this) {
            case C_BOOL -> "b";
            case C_CHAR -> "c";
            case C_SHORT -> "s";
            case C_INT -> "i";
            case C_LONG -> "l";
            case C_FLOAT -> "f";
            case C_DOUBLE -> "d";
        };
    }

    @Override
    public String toName() {
        return toString();
    }

    @Override
    public String toCDecl(String name) {
        return toCType() + " " + name;
    }

    @Override
    public String toCType() {
        return switch (this) {
            case C_BOOL, C_CHAR -> "char";
            case C_SHORT -> "short";
            case C_INT -> "int";
            case C_LONG -> "long long";
            case C_FLOAT -> "float";
            case C_DOUBLE -> "double";
        };
    }

    @Override
    public long getAlignment() {
        return switch (this) {
            case C_CHAR, C_BOOL -> 1;
            case C_SHORT -> 2;
            case C_INT, C_FLOAT -> 4;
            case C_LONG, C_DOUBLE -> 8;
        };
    }

    @Override
    public long getByteSize() {
        return switch (this) {
            case C_CHAR, C_BOOL -> 1;
            case C_SHORT -> 2;
            case C_INT, C_FLOAT -> 4;
            case C_LONG, C_DOUBLE -> 8;
        };
    }

    @Override
    public MemoryLayout toMemoryLayout() {
        return switch (this) {
            case C_BOOL -> JAVA_BOOLEAN;
            case C_CHAR -> JAVA_BYTE;
            case C_SHORT -> JAVA_SHORT;
            case C_INT -> JAVA_INT;
            case C_LONG -> JAVA_LONG;
            case C_FLOAT -> JAVA_FLOAT;
            case C_DOUBLE -> JAVA_DOUBLE;
        };
    }

    @Override
    public void initValue(MemorySegment segment) {
        switch (this) {
            case C_BOOL -> segment.set(JAVA_BOOLEAN, 0, true);
            case C_CHAR -> segment.set(JAVA_BYTE, 0L, (byte) 98);
            case C_SHORT -> segment.set(JAVA_SHORT, 0L, (short) 1888);
            case C_INT -> segment.set(JAVA_INT, 0L, 188888);
            case C_LONG -> segment.set(JAVA_LONG, 0L, 1888888);
            case C_FLOAT -> segment.set(JAVA_FLOAT, 0L, 12);
            case C_DOUBLE -> segment.set(JAVA_DOUBLE, 0L, 24);
        }
    }

    @Override
    public String dumpValue(MemorySegment segment) {
        StringBuilder sb = new StringBuilder();
        switch (this) {
            case C_BOOL -> sb.append(segment.get(JAVA_BOOLEAN, 0));
            case C_CHAR -> sb.append(segment.get(JAVA_BYTE, 0));
            case C_SHORT -> sb.append(segment.get(JAVA_SHORT, 0));
            case C_INT -> sb.append(segment.get(JAVA_INT, 0));
            case C_LONG -> sb.append(segment.get(JAVA_LONG, 0));
            case C_FLOAT -> sb.append(segment.get(JAVA_FLOAT, 0));
            case C_DOUBLE -> sb.append(segment.get(JAVA_DOUBLE, 0));
        }
        return sb.toString();
    }

    @Override
    public void check(MemorySegment segment) {
        switch (this) {
            case C_BOOL -> assertTrue(segment.get(JAVA_BOOLEAN, 0));
            case C_CHAR -> assertEquals(segment.get(JAVA_BYTE, 0), 98);
            case C_SHORT -> assertEquals(segment.get(JAVA_SHORT, 0), 1888);
            case C_INT -> assertEquals(segment.get(JAVA_INT, 0), 188888);
            case C_LONG -> assertEquals(segment.get(JAVA_LONG, 0), 1888888);
            case C_FLOAT -> assertEquals(segment.get(JAVA_FLOAT, 0), 12);
            case C_DOUBLE -> assertEquals(segment.get(JAVA_DOUBLE, 0), 24);
        }
    }

    static public List<Component> all() {
        return List.of(C_INT, C_LONG, C_FLOAT, C_DOUBLE);
    }
}


record Sequence(long length, Component subType) implements Component {
    public static Sequence c(long length, Component elementType) {
        return new Sequence(length, elementType);
    }

    Component elementaryType() {
        if (subType instanceof Sequence seq) return seq.elementaryType();
        else return subType;
    }

    ArrayList<Integer> lengths() {
        if (subType instanceof Sequence seq) {
            var list = new ArrayList<Integer>(List.of((int) length));
            list.addAll(seq.lengths());
            return list;
        } else return new ArrayList<>(List.of((int) length));
    }

    @Override
    public String toString() {
        return elementaryType() + lengths().stream().map(l -> "[" + l + "]").collect(Collectors.joining());
    }

    @Override
    public String toName() {
        return "Qx" + length + subType.toName();
    }

    @Override
    public String toCDecl(String name) {
        return elementaryType().toCType() + " " + name + lengths().stream()
                                                                  .map(l -> "[" + l + "]")
                                                                  .collect(Collectors.joining());
    }

    @Override
    public String toCType() {
        throw new UnsupportedOperationException("???");
    }

    @Override
    public void initValue(MemorySegment segment) {
        for (long i = 0; i < length; i++) {
            subType.initValue(segment.asSlice(i * subType().getByteSize()));
        }
    }

    @Override
    public String dumpValue(MemorySegment segment) {
        StringBuilder sb = new StringBuilder();
        sb.append("[");
        for (long i = 0; i < length; i++) {
            sb.append(subType.dumpValue(segment.asSlice(i * subType().getByteSize())));
            if (i != length - 1) {
                sb.append(", ");
            }
        }
        sb.append("]");
        return sb.toString();
    }

    @Override
    public void check(MemorySegment segment) {
        for (long i = 0; i < length; i++) {
            subType.check(segment.asSlice(i * subType().getByteSize()));
        }
    }

    @Override
    public long getAlignment() {
        return subType.getAlignment();
    }

    @Override
    public long getByteSize() {
        return length * subType.getByteSize();
    }

    @Override
    public MemoryLayout toMemoryLayout() {
        return MemoryLayout.sequenceLayout(length, subType.toMemoryLayout());
    }

}

final class Struct implements Component {
    static HashSet<String> allNames = new HashSet<>();
    static ArrayList<Struct> allStructs = new ArrayList<>();
    Component[] components;
    long maxComponentSize = -1;
    long byteSize = -1;
    GroupLayout layout = null;

    private Struct(Component[] components) {
        this.components = components;
    }

    static String forwardDecls() {
        return allStructs.stream()
                         .map(s -> s.toCType() + ";")
                         .collect(Collectors.joining("\n"));
    }

    static String decls() {
        return allStructs.stream()
                         .map(Struct::getTypeDecl)
                         .collect(Collectors.joining("\n"));
    }

    public static Struct c(Component... components) {
        var cc = new Struct(components);
        if (!allNames.contains(cc.toName())) {
            allNames.add(cc.toName());
            allStructs.add(cc);
        }
        return cc;
    }

    public static long alignUp(long offset, long alignment) {
        return ((offset - 1) | (alignment - 1)) + 1;
    }

    @Override
    public String toString() {
        String s = "{";
        s += Arrays.stream(components)
                   .map(Object::toString)
                   .collect(Collectors.joining(", "));
        s += "}";
        return s;
    }

    @Override
    public String toName() {
        return "S1_" + Arrays.stream(components)
                             .map(Component::toName).collect(Collectors.joining()) + "_2";
    }

    @Override
    public String toCDecl(String name) {
        return toCType() + " " + name;
    }

    @Override
    public String toCType() {
        return "struct" + " " + toName();
    }

    @Override
    public long getAlignment() {
        if (maxComponentSize != -1) return maxComponentSize;
        return Arrays.stream(components)
                     .map(Component::getAlignment)
                     .max(Comparator.comparingLong(o -> o)).orElse(1L);
    }

    @Override
    public long getByteSize() {
        return toMemoryLayout().byteSize();
    }

    @Override
    public MemoryLayout toMemoryLayout() {
        if (layout != null) return layout;
        var list = new ArrayList<MemoryLayout>();
        var offset = 0;
        for (Component component : components) {
            var diff = alignUp(offset, component.getAlignment()) - offset;
            if (diff > 0) {
                offset += diff;
                list.add(MemoryLayout.paddingLayout(diff * 8));
            }
            list.add(component.toMemoryLayout());
            offset += component.getByteSize();
        }
        // 补充后面的填充。
        var diff = alignUp(offset, getAlignment()) - offset;
        if (diff > 0) {
            offset += diff;
            list.add(MemoryLayout.paddingLayout(diff * 8));
            this.byteSize = offset;
        }
        layout = MemoryLayout.structLayout(list.toArray(MemoryLayout[]::new));
        return layout;
    }

    String getTypeDecl() {
        StringBuilder sb = new StringBuilder();
        sb.append("struct").append(" ").append(toName()).append("{");
        for (int i = 0; i < components.length; i++) {
            var component = components[i];
            sb.append(component.toCDecl("f" + i)).append(";");
        }
        sb.append("}").append(";");
        return sb.toString();
    }

    @Override
    public void initValue(MemorySegment segment) {
        if (layout == null) toMemoryLayout();
        long offset = 0;
        long cnt = 0;
        for (MemoryLayout memberLayout : layout.memberLayouts()) {
            if (memberLayout.isPadding()) {
                offset += memberLayout.byteSize();
                continue;
            }
            components[(int) cnt].initValue(segment.asSlice(offset));
            offset += memberLayout.byteSize();
            cnt++;
        }
    }

    @Override
    public String dumpValue(MemorySegment segment) {
        StringBuilder sb = new StringBuilder();
        sb.append("struct").append(" ").append(toName()).append("{");
        long offset = 0;
        long cnt = 0;
        var list = new ArrayList<String>();
        for (MemoryLayout memberLayout : layout.memberLayouts()) {
            if (memberLayout.isPadding()) {
                offset += memberLayout.byteSize();
                continue;
            }
            list.add(components[(int) cnt].dumpValue(segment.asSlice(offset)));
            offset += memberLayout.byteSize();
            cnt++;
        }
        sb.append(String.join(", ", list)).append("}");
        return sb.toString();
    }

    @Override
    public void check(MemorySegment segment) {
        long offset = 0;
        long cnt = 0;
        for (MemoryLayout memberLayout : layout.memberLayouts()) {
            if (memberLayout.isPadding()) {
                offset += memberLayout.byteSize();
                continue;
            }
            components[(int) cnt].check(segment.asSlice(offset));
            offset += memberLayout.byteSize();
            cnt++;
        }
    }
}


abstract class CFunction {
    abstract void invoke() throws Throwable;

    abstract String getFuncName();

    abstract FunctionDescriptor getDesc();
}


class DowncallFunction extends CFunction {

    static Linker LINKER = Linker.nativeLinker();
    static SymbolLookup LOOKUP = SymbolLookup.loaderLookup();

    Component retLayout;
    Component[] argLayouts;

    DowncallFunction(Component retLayout, Component... argLayouts) {
        if (!retLayout.toMemoryLayout().equals(argLayouts[0].toMemoryLayout()))
            throw new IllegalArgumentException("???");
        this.retLayout = retLayout;
        this.argLayouts = argLayouts;
    }

    public String getDef() {
        var s = Arrays.stream(argLayouts).map(Component::toName)
                      .collect(Collectors.joining("_"));
        StringBuilder sb = new StringBuilder();
        sb.append(retLayout.toCType()).append(" ").append(getFuncName());
        var argList = new ArrayList<String>();
        for (int i = 0; i < argLayouts.length; i++) {
            var arg = argLayouts[i];
            argList.add(arg.toCDecl("a" + i));
        }
        sb.append("(").append(String.join(", ", argList)).append(")");
        sb.append("{return a0;}");
        return sb.toString();
    }

    @Override
    public String toString() {
        return getFuncName();
    }

    @Override
    void invoke() throws Throwable {
        var target = LOOKUP.lookup(getFuncName()).get();
        var funcDesc = getDesc();
        var mh = LINKER.downcallHandle(target, funcDesc);
        try (var session = MemorySession.openConfined()) {
            var list = new ArrayList<Object>();
            list.add(session);
            for (Component argLayout : argLayouts) {
                var seg = argLayout.getMemorySegment(session);
                argLayout.initValue(seg);
                list.add(seg);
            }
            var retValue = (MemorySegment) mh.invokeWithArguments(list);
            retLayout.check(retValue);
        }
    }

    @Override
    String getFuncName() {
        var s = Arrays.stream(argLayouts).map(Component::toName)
                      .collect(Collectors.joining("_"));
        if (s.hashCode() < 0) {
            return ("func" + s.hashCode()).replace('-', 'm');
        }
        return "func" + s.hashCode();
    }

    @Override
    FunctionDescriptor getDesc() {
        var args = Arrays.stream(argLayouts).map(Component::toMemoryLayout).toArray(MemoryLayout[]::new);
        return FunctionDescriptor.of(retLayout.toMemoryLayout(), args);
    }
}

public class TestFlattenHelper {

    static void writeToFile(File f, String content) throws IOException {
        try (var writer = new FileWriter(f)) {
            writer.write(content);
        }
    }

    static List<Struct> shrink(List<Struct> list, int factor) {
        var newList = new ArrayList<Struct>();
        for (int i = 0; i < list.size(); i++) {
            if (i % factor == 0) newList.add(list.get(i));
        }
        return newList;
    }

    public static void main(String[] args) throws Throwable {
        var headFile = new File("libTestFlatten.h");
        var sourceFile = new File("libTestFlatten.c");
        Permutation.permutationStruct(1, 3, 3);
        var funcList = Struct.allStructs.stream()
                                        .map(component -> new DowncallFunction(component, component))
                                        .toList();
        var source = funcList.stream().map(f -> "EXPORT " + f.getDef())
                             .collect(Collectors.joining("\n"));
        var compilerCommands = """
                #ifdef __clang__
                #pragma clang optimize off
                #elif defined __GNUC__
                #pragma GCC optimize ("O0")
                #elif defined _MSC_BUILD
                #pragma optimize( "", off )
                #endif
                                
                #ifdef _WIN64
                #define EXPORT __declspec(dllexport)
                #else
                #define EXPORT
                #endif
                """;
        writeToFile(headFile, compilerCommands + "\n" + Struct.decls());
        writeToFile(sourceFile, "#include \"libTestFlatten.h\"\n\n" + source);
    }
}
