package com.thelads.core.v1_8_9.pvp;

import com.thelads.core.client.ColorGrade;
import com.thelads.core.client.ScreenEffects;
import com.thelads.core.modules.ColorSaturationModule;
import com.thelads.core.modules.MenuBlurModule;
import com.thelads.core.modules.MotionBlurModule;
import com.thelads.core.v1_8_9.feature.GlState189;
import com.thelads.core.v1_8_9.feature.RenderScale189;
import java.nio.ByteBuffer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiIngameMenu;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import org.apache.logging.log4j.LogManager;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GLContext;

/**
 * Color Saturation, Motion Blur and Menu Blur on 1.8.9. Lads renders 1.8.9 without Minecraft's framebuffer by default (Fast Render,
 * with OptiFine or Ionium), so these work on whatever framebuffer is bound, the window's included: the picture is copied
 * (glBlitFramebuffer) into a texture of their own and drawn back through a small GLSL 1.20 program. Color and motion blur run once the
 * world is drawn, before the hand and HUD (Forge's RenderHandEvent, after Better Resolution's composite); Menu Blur when a menu draws
 * its world background, over the world and the HUD. Off with an OptiFine shader pack (it owns the picture), in 3D anaglyph and
 * without OpenGL 3.0 (logged once).
 */
public final class ScreenEffects189 {
    private static final String VERTEX = "#version 120\nvoid main() { gl_Position = gl_Vertex; gl_TexCoord[0] = gl_MultiTexCoord0; }\n";
    private static final String COLOR = "#version 120\nuniform sampler2D scene;\nuniform vec4 red, green, blue;\nuniform float vibrance;\n"
        + "void main() {\n"
        + "    vec4 c = vec4(texture2D(scene, gl_TexCoord[0].st).rgb, 1.0);\n"
        + "    vec3 g = vec3(dot(red, c), dot(green, c), dot(blue, c));\n"
        + "    float luma = dot(g, vec3(0.213, 0.715, 0.072));\n"
        + "    float chroma = clamp(max(g.r, max(g.g, g.b)) - min(g.r, min(g.g, g.b)), 0.0, 1.0);\n"
        + "    gl_FragColor = vec4(clamp(mix(vec3(luma), g, 1.0 + vibrance * (1.0 - chroma)), 0.0, 1.0), 1.0);\n"
        + "}\n";
    /** The last frame, drawn over this one keeping {@code blend} of it (GL blending does the mix). */
    private static final String MOTION = "#version 120\nuniform sampler2D scene;\nuniform float blend;\n"
        + "void main() { gl_FragColor = vec4(texture2D(scene, gl_TexCoord[0].st).rgb, blend); }\n";
    /** One direction of a box blur, {@code step} = one pixel along it. */
    private static final String BLUR = "#version 120\nuniform sampler2D scene;\nuniform vec2 step;\nuniform float radius;\n"
        + "void main() {\n"
        + "    vec3 sum = vec3(0.0);\n"
        + "    for (float r = -radius; r <= radius; r += 1.0) sum += texture2D(scene, gl_TexCoord[0].st + step * r).rgb;\n"
        + "    gl_FragColor = vec4(sum / (2.0 * radius + 1.0), 1.0);\n"
        + "}\n";

    private static final ColorGrade GRADE = new ColorGrade();
    private static int width, height, sceneFbo, sceneTex, historyFbo, historyTex, blurFbo, blurTex;
    private static int colorProgram, motionProgram, blurProgram;
    private static int uRed, uGreen, uBlue, uVibrance, uBlend, uStep, uRadius;
    private static boolean unsupported, colorStale = true, historyFresh = true;
    private static long frameStart, frames, blurredFrame = -1;
    private static ColorSaturationModule colorModule;
    private static MotionBlurModule motionModule;
    private static MenuBlurModule menuModule;

    private ScreenEffects189() {}

    static void modules(ColorSaturationModule color, MotionBlurModule motion, MenuBlurModule menu) {
        colorModule = color;
        motionModule = motion;
        menuModule = menu;
    }

    /** RenderTickEvent start: a new frame. */
    static void frame() {
        frames++;
    }

    /** RenderHandEvent: the world is done, the hand comes next. */
    static void afterWorld(Minecraft mc) {
        long now = System.nanoTime(), frame = now - frameStart;
        frameStart = now;
        boolean color = colorModule.isEnabled(), motion = motionModule.isEnabled();
        if (color && GRADE.update(colorModule)) colorStale = true;
        color &= GRADE.active;
        if (!motion) historyFresh = true;
        if (!color && !motion || !usable(mc)) return;
        int source = GL11.glGetInteger(GL30.GL_FRAMEBUFFER_BINDING);
        boolean blend = GlState189.blend(), alpha = GlState189.alpha(), depth = GlState189.depth();
        GlStateManager.disableDepth();
        GlStateManager.disableAlpha();
        GlStateManager.disableBlend();
        GlStateManager.setActiveTexture(OpenGlHelper.defaultTexUnit);
        GlStateManager.enableTexture2D();
        if (color) {
            copy(source, sceneFbo);
            GL20.glUseProgram(colorProgram);
            if (colorStale) {
                float[] m = GRADE.matrix;
                GL20.glUniform4f(uRed, m[0], m[1], m[2], m[3]);
                GL20.glUniform4f(uGreen, m[4], m[5], m[6], m[7]);
                GL20.glUniform4f(uBlue, m[8], m[9], m[10], m[11]);
                GL20.glUniform1f(uVibrance, GRADE.vibrance);
                colorStale = false;
            }
            draw(sceneTex);
        }
        if (motion) {
            if (!historyFresh) {
                GL20.glUseProgram(motionProgram);
                GL20.glUniform1f(uBlend, ScreenEffects.motionRetain(motionModule.strength.getValue(), frame));
                GlStateManager.enableBlend();
                GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ZERO, GL11.GL_ONE);
                draw(historyTex);
                GlStateManager.disableBlend();
            }
            copy(source, historyFbo);
            historyFresh = false;
        }
        GL20.glUseProgram(0);
        restore(blend, alpha, depth);
    }

    /** GuiScreen.drawWorldBackground in a world: blurs what is on screen so far (the world and the HUD), once a frame. */
    public static void menuBackground(Minecraft mc, GuiScreen screen) {
        if (menuModule == null || blurredFrame == frames) return;
        int kind = screen instanceof GuiContainer ? ScreenEffects.INVENTORY : screen instanceof GuiIngameMenu ? ScreenEffects.PAUSE : ScreenEffects.OTHER;
        int radius = menuModule.radius(kind);
        if (radius <= 0 || !usable(mc)) return;
        blurredFrame = frames;
        int source = GL11.glGetInteger(GL30.GL_FRAMEBUFFER_BINDING);
        boolean blend = GlState189.blend(), alpha = GlState189.alpha(), depth = GlState189.depth();
        GlStateManager.disableDepth();
        GlStateManager.disableAlpha();
        GlStateManager.disableBlend();
        GlStateManager.setActiveTexture(OpenGlHelper.defaultTexUnit);
        GlStateManager.enableTexture2D();
        copy(source, sceneFbo);
        GL20.glUseProgram(blurProgram);
        GL20.glUniform1f(uRadius, radius);
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, blurFbo);
        GL20.glUniform2f(uStep, 1F / width, 0);
        draw(sceneTex);
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, source);
        GL20.glUniform2f(uStep, 0, 1F / height);
        draw(blurTex);
        GL20.glUseProgram(0);
        restore(blend, alpha, depth);
    }

    /** OpenGL 3.0 and 2.0, no shader pack, no anaglyph, a world; the copies sized to the window. */
    private static boolean usable(Minecraft mc) {
        if (unsupported || mc.theWorld == null || mc.gameSettings.anaglyph || RenderScale189.shaders()) return false;
        if (colorProgram == 0) {
            try {
                if (!GLContext.getCapabilities().OpenGL30) throw new IllegalStateException("OpenGL 3.0 is not available");
                colorProgram = program(COLOR);
                motionProgram = program(MOTION);
                blurProgram = program(BLUR);
                uRed = GL20.glGetUniformLocation(colorProgram, "red");
                uGreen = GL20.glGetUniformLocation(colorProgram, "green");
                uBlue = GL20.glGetUniformLocation(colorProgram, "blue");
                uVibrance = GL20.glGetUniformLocation(colorProgram, "vibrance");
                uBlend = GL20.glGetUniformLocation(motionProgram, "blend");
                uStep = GL20.glGetUniformLocation(blurProgram, "step");
                uRadius = GL20.glGetUniformLocation(blurProgram, "radius");
                for (int program : new int[]{colorProgram, motionProgram, blurProgram}) {
                    GL20.glUseProgram(program);
                    GL20.glUniform1i(GL20.glGetUniformLocation(program, "scene"), 0);
                }
                GL20.glUseProgram(0);
            } catch (Exception failed) {
                LogManager.getLogger("TheLadsCore").error("Screen effects (Color Saturation, Motion Blur, Menu Blur) cannot run here", failed);
                unsupported = true;
                return false;
            }
        }
        if (mc.displayWidth != width || mc.displayHeight != height) resize(mc.displayWidth, mc.displayHeight);
        return true;
    }

    private static void resize(int w, int h) {
        if (sceneFbo != 0) {
            for (int fbo : new int[]{sceneFbo, historyFbo, blurFbo}) GL30.glDeleteFramebuffers(fbo);
            for (int tex : new int[]{sceneTex, historyTex, blurTex}) GL11.glDeleteTextures(tex);
        }
        width = w;
        height = h;
        int bound = GL11.glGetInteger(GL30.GL_FRAMEBUFFER_BINDING);
        sceneTex = texture(w, h);
        sceneFbo = framebuffer(sceneTex);
        historyTex = texture(w, h);
        historyFbo = framebuffer(historyTex);
        blurTex = texture(w, h);
        blurFbo = framebuffer(blurTex);
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, bound);
        historyFresh = true;
    }

    private static int texture(int w, int h) {
        int tex = GL11.glGenTextures();
        GlStateManager.bindTexture(tex);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, w, h, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, (ByteBuffer) null);
        return tex;
    }

    private static int framebuffer(int tex) {
        int fbo = GL30.glGenFramebuffers();
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, fbo);
        GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, tex, 0);
        return fbo;
    }

    /** The bound picture into one of ours; {@code source} stays bound for drawing. */
    private static void copy(int source, int target) {
        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, source);
        GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, target);
        GL30.glBlitFramebuffer(0, 0, width, height, 0, 0, width, height, GL11.GL_COLOR_BUFFER_BIT, GL11.GL_NEAREST);
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, source);
    }

    /** {@code texture} over the whole bound framebuffer through the program in use (it takes gl_Vertex as is). */
    private static void draw(int texture) {
        GlStateManager.bindTexture(texture);
        GlStateManager.viewport(0, 0, width, height);
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glTexCoord2f(0, 0);
        GL11.glVertex2f(-1, -1);
        GL11.glTexCoord2f(1, 0);
        GL11.glVertex2f(1, -1);
        GL11.glTexCoord2f(1, 1);
        GL11.glVertex2f(1, 1);
        GL11.glTexCoord2f(0, 1);
        GL11.glVertex2f(-1, 1);
        GL11.glEnd();
    }

    private static void restore(boolean blend, boolean alpha, boolean depth) {
        if (blend) GlStateManager.enableBlend();
        if (alpha) GlStateManager.enableAlpha();
        if (depth) GlStateManager.enableDepth();
    }

    private static int program(String fragmentSource) {
        int vertex = shader(GL20.GL_VERTEX_SHADER, VERTEX), fragment = shader(GL20.GL_FRAGMENT_SHADER, fragmentSource);
        int program = GL20.glCreateProgram();
        GL20.glAttachShader(program, vertex);
        GL20.glAttachShader(program, fragment);
        GL20.glLinkProgram(program);
        GL20.glDeleteShader(vertex);
        GL20.glDeleteShader(fragment);
        if (GL20.glGetProgrami(program, GL20.GL_LINK_STATUS) != GL11.GL_TRUE) throw new IllegalStateException(GL20.glGetProgramInfoLog(program, 4096));
        return program;
    }

    private static int shader(int type, String source) {
        int shader = GL20.glCreateShader(type);
        GL20.glShaderSource(shader, source);
        GL20.glCompileShader(shader);
        if (GL20.glGetShaderi(shader, GL20.GL_COMPILE_STATUS) != GL11.GL_TRUE) throw new IllegalStateException(GL20.glGetShaderInfoLog(shader, 4096));
        return shader;
    }
}
