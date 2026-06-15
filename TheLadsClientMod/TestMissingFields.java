import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.io.File;

public class TestMissingFields {
    public static class ModConfig {
        private boolean capesEnabled = true;
        private boolean uiScalingEnabled = false;
        public boolean isCapesEnabled() { return capesEnabled; }
    }
    public static void main(String[] args) throws Exception {
        Gson GSON = new Gson();
        JsonObject jo = new JsonObject();
        jo.addProperty("uiScalingEnabled", true);
        ModConfig parsed = GSON.fromJson(jo, ModConfig.class);
        System.out.println("Result capesEnabled = " + parsed.isCapesEnabled());
        System.out.println("Is it true (default)? " + (parsed.isCapesEnabled() == true));
    }
}
