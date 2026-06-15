public class BobbingTest {
    public static void main(String[] args) {
        float yOld = 10.0f;
        float y = 11.0f; // Velocity of 1.0 per tick

        for (float pt = 0.0f; pt <= 1.0f; pt += 0.2f) {
            float interpolatedY = yOld + pt * (y - yOld);
            float vy = interpolatedY - yOld;
            System.out.println("pt=" + pt + " => vy=" + vy);
        }
    }
}
