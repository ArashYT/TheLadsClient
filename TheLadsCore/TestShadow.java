public class TestShadow {
    public static void main(String[] args) {
        int color = 0x55FF55;
        
        // Simulating Minecraft's Font.java behavior
        // Main color alpha fix:
        int mainColor = (color & 0xFC000000) == 0 ? color | 0xFF000000 : color;
        System.out.println("Main color: " + Integer.toHexString(mainColor));
        
        // Shadow color calculation (uses original color's alpha!):
        int j = (int)(((color >> 16) & 255) * 0.25);
        int k = (int)(((color >> 8) & 255) * 0.25);
        int l = (int)((color & 255) * 0.25);
        int shadowColor = (color & 0xFF000000) | (j << 16) | (k << 8) | l;
        System.out.println("Shadow color: " + Integer.toHexString(shadowColor));
    }
}
