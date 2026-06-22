import com.google.gson.Gson;
public class TestNull {
    public static class ModConfig {
        private boolean capesEnabled = true;
        public boolean isCapesEnabled() { return capesEnabled; }
    }
    public static void main(String[] args) {
        Gson GSON = new Gson();
        ModConfig parsed = GSON.fromJson("{\"capesEnabled\":null}", ModConfig.class);
        System.out.println("capesEnabled=" + parsed.isCapesEnabled());
    }
}
