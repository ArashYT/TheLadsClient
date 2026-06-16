package com.thelads.core.features.alwayson.raisesoundlimit;

import java.io.FileNotFoundException;
import java.io.InputStream;
import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileAttribute;

public class RSLSInjectorFFM {
    private static MemorySegment buf;

    public static void init() {
    }

    static {
        try {
            String name = "assets/theladscore/rsls-alsoft.conf";
            InputStream resource = RSLSInjectorFFM.class.getClassLoader().getResourceAsStream(name);
            if (resource != null) {
                try {
                    String[] split = "rsls-alsoft.conf".split("\\.");
                    Path current = Path.of(".");
                    Path cacheDir = Files.createDirectories(current.resolve("cache"), new FileAttribute[0]);
                    if (Files.exists(cacheDir)) {
                        try (java.util.stream.Stream<Path> stream = Files.list(cacheDir)) {
                            stream.filter(path -> path.getFileName().toString().startsWith("rsls-alsoft-") && path.getFileName().toString().endsWith(".conf"))
                                  .forEach(path -> {
                                      try {
                                          Files.deleteIfExists(path);
                                      } catch (Exception ignored) {}
                                  });
                        } catch (Exception ignored) {}
                    }
                    Path tempFile = Files.createTempFile(cacheDir, split[0] + "-", "." + split[1], new FileAttribute[0]);
                    Files.copy(resource, tempFile, StandardCopyOption.REPLACE_EXISTING);
                    Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                        try {
                            Files.deleteIfExists(tempFile);
                        } catch (Throwable t) {
                            t.printStackTrace();
                        }
                    }));
                    String envDefinition = "ALSOFT_CONF=" + String.valueOf(current.relativize(tempFile));
                    System.out.println(String.format("Attempting to invoke putenv(\"%s\")", envDefinition));
                    
                    buf = Arena.global().allocateFrom(envDefinition);
                    boolean isWindows = System.getProperty("os.name").toLowerCase().contains("win");
                    MemorySegment putenv = Linker.nativeLinker().defaultLookup().findOrThrow(isWindows ? "_putenv" : "putenv");
                    MethodHandle putenvHandle = Linker.nativeLinker().downcallHandle(putenv, FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS));
                    int result = (int) putenvHandle.invokeExact(buf);
                    if (result != 0) {
                        throw new RuntimeException("Error " + result + " when setting env");
                    }
                } finally {
                    resource.close();
                }
            } else {
                throw new FileNotFoundException(name);
            }
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }
}
