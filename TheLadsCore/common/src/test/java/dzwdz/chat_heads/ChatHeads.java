package dzwdz.chat_heads;

/** Test stand-in for the Chat Heads state ChatHistory reaches by reflection (names as in Chat Heads 1.3.1). */
public final class ChatHeads {
    public static final Object EMPTY = "EMPTY";
    public static boolean refreshing;
    public static Object lineData = EMPTY, refreshingLineData = EMPTY;

    /** What its GuiMessageLineMixin does as a chat line is created: take getLineData(), then setLineData(EMPTY). */
    public static Object newLine() {
        Object data = refreshing ? refreshingLineData : lineData;
        if (refreshing) refreshingLineData = EMPTY;
        else lineData = EMPTY;
        return data;
    }
}
