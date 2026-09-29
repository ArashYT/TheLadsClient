package com.thelads.core.v26_2.feature;
import java.io.File;
public final class GlobalScreenshots {
    private GlobalScreenshots() {}
    public static File gameDirectory(){
        String os=System.getProperty("os.name","").toLowerCase(java.util.Locale.ROOT);
        String home=System.getProperty("user.home");
        String appdata=System.getenv("APPDATA");
        return os.contains("win") ? new File(appdata==null?home:appdata,".minecraft")
            : os.contains("mac") ? new File(home,"Library/Application Support/minecraft") : new File(home,".minecraft");
    }
    public static File directory(){return new File(gameDirectory(),"screenshots");}
}
