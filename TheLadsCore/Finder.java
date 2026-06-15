public class Finder {
    public static void main(String[] args) throws Exception {
        Class<?> clz = Class.forName("net.minecraft.client.player.AbstractClientPlayer");
        for (java.lang.reflect.Method m : clz.getDeclaredMethods()) {
            if (m.getName().equals("getSkin")) {
                System.out.println("Method: " + m);
                System.out.println("Return type: " + m.getReturnType().getName());
            }
        }
    }
}
