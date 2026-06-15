import java.lang.reflect.Method;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;

public class TestDump {
    public static void main(String[] args) {
        for (Method m : PayloadTypeRegistry.class.getMethods()) {
            System.out.println(m.getName());
        }
    }
}
