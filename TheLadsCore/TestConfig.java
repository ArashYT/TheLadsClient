import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.File;

public class TestConfig {
    public static void main(String[] args) {
        Gson GSON = new GsonBuilder().setPrettyPrinting().create();
        File CONFIG_FILE = new File("run/config/thelads_config.json");
        try (FileReader reader = new FileReader(CONFIG_FILE)) {
            JsonObject json = GSON.fromJson(reader, JsonObject.class);
            System.out.println("Parsed: " + json);
        } catch (IOException e) {
            System.out.println("IOException caught");
        }
    }
}
