public class Test {
    private String name;

    public Test(String name) {
        this.name = name;
    }

    @Override
    protected void finalize() throws Throwable {
        try {
            System.out.println("Finalizing " + name);
        } finally {
            super.finalize();
        }
    }

    public static void main(String[] args) {
        new Thread(() -> {
            Test obj = new Test("Example Object");
            for (int i = 0; i < 200000; i++) {
                obj = new Test("Example Object" + i);
            }
            obj = null;
        }).start();

        Test obj = new Test("Example Object");
        for (int i = 0; i < 200000; i++) {
            obj = new Test("Example Object" + i);
        }
        obj = null;
        System.gc();
    }
}