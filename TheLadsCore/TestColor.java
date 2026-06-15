public class TestColor {
    public static void main(String[] args) {
        int color = 0x55FF55;
        if ((color & 0xFC000000) == 0) {
            System.out.println("Alpha is 0, typically adjusted by MC font renderer to " + Integer.toHexString(color | 0xFF000000));
        } else {
            System.out.println("Alpha is present");
        }
    }
}
