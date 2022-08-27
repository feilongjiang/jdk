/*
 * @test
 * @enablePreview
 * @requires ((os.arch == "amd64" | os.arch == "x86_64") & sun.arch.data.model == "64") | os.arch == "aarch64" | os.arch == "riscv64"
 * @build TestFlattenHelper
 *
 * @run testng/othervm -XX:+IgnoreUnrecognizedVMOptions -XX:-VerifyDependencies
 *   --enable-native-access=ALL-UNNAMED
 *   TestFlatten
 */

import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;


public class TestFlatten {
    static int counter = 0;
    static int factor = 3;
    static {
        System.loadLibrary("TestFlatten");
    }

    @DataProvider(name = "functions")
    Object[][] functions() {
        Permutation.permutationStruct(1, 3, 3);
        var funcList = Struct.allStructs.stream()
                                        .filter(c -> c.toMemoryLayout().byteSize() != 0)
                                        .map(c -> new DowncallFunction(c, c))
                                        .toList();
        var ret = new Object[funcList.size()][1];
        for (int i = 0; i < funcList.size(); i++) {
            ret[i][0] = funcList.get(i);
        }
        return ret;
    }

    @Test(dataProvider = "functions")
    void testDowncall(DowncallFunction function) throws Throwable {
        if (counter % factor == 0)
            function.invoke();
        counter++;
    }
}
