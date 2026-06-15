import com.google.gson.JsonPrimitive;

public class TestUnsafe {
    public static void main(String[] args) {
        JsonPrimitive jp = new JsonPrimitive("invalid");
        System.out.println("isBoolean: " + jp.isBoolean());
        try {
            System.out.println("getAsBoolean: " + jp.getAsBoolean());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
