import com.google.gson.Gson;
import com.google.gson.JsonObject;
public class TestGson {
    public static class ModConfig {
        private boolean capesEnabled = true;
        private boolean uiScalingEnabled = false;
        public boolean isCapesEnabled() { return capesEnabled; }
    }
    public static void main(String[] args) {
        Gson GSON = new Gson();
        JsonObject jo = new JsonObject();
        jo.addProperty("uiScalingEnabled", true);
        ModConfig parsed = GSON.fromJson(jo, ModConfig.class);
        System.out.println("capesEnabled=" + parsed.isCapesEnabled());
    }
}
