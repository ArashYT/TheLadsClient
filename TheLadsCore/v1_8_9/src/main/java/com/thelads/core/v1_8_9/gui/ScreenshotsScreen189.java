package com.thelads.core.v1_8_9.gui;

import com.thelads.core.client.util.FileDialogs;
import com.thelads.core.shared.ScreenshotSources;
import com.thelads.core.shared.SharedContentPaths;
import com.thelads.core.v1_8_9.feature.Screenshots189;
import java.awt.Desktop;
import java.awt.Toolkit;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.gui.GuiYesNo;
import net.minecraft.client.gui.GuiYesNoCallback;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.util.MathHelper;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.Sys;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

/**
 * The 1.8.9 screenshot manager, matching ManageScreenshotsScreen on 1.20.1/1.21.11/26.2/26.3:
 * thumbnail grid, header (Configure..., Manage Screenshots..., Add folder), status line, bottom controls
 * (Order, Open Folder, Done / Delete N, Fast Delete, Refresh), right-click properties menu, confirm delete,
 * rename dialog, and enlarged viewer with zoom/pan and prev/next navigation.
 */
public final class ScreenshotsScreen189 extends GuiScreen implements GuiYesNoCallback {
    private static final ResourceLocation PROPERTIES_BG =
        new ResourceLocation("lads_screenshots", "textures/gui/sprites/screenshot_properties_background.png");
    private static final ResourceLocation PROP_BTN_DISABLED =
        new ResourceLocation("lads_screenshots", "textures/gui/sprites/widget/properties_button.png");
    private static final ResourceLocation PROP_BTN_ENABLED =
        new ResourceLocation("lads_screenshots", "textures/gui/sprites/widget/properties_button_enabled.png");
    private static final ResourceLocation PROP_BTN_HOVERED =
        new ResourceLocation("lads_screenshots", "textures/gui/sprites/widget/properties_button_hovered.png");

    private static final Icon ICON_CONFIG = new Icon("widget/icons/config", 20);
    private static final Icon ICON_REFRESH = new Icon("widget/icons/refresh", 20);
    private static final Icon ICON_ASCENDING = new Icon("widget/icons/ascending_order", 20);
    private static final Icon ICON_DESCENDING = new Icon("widget/icons/descending_order", 20);
    private static final Icon ICON_OPEN_FOLDER = new Icon("widget/icons/open_folder", 20);
    private static final Icon ICON_DELETE = new Icon("widget/icons/delete", 19);
    private static final Icon ICON_FAST_DELETE = new Icon("widget/icons/fast_delete_enabled", 19);
    private static final Icon ICON_COPY = new Icon("widget/icons/copy", 19);
    private static final Icon ICON_RENAME = new Icon("widget/icons/rename", 19);
    private static final Icon ICON_CLOSE = new Icon("widget/icons/close", 19);

    private static final int BTN_ADD_FOLDER = 100;
    private static final int BTN_CONFIG = 101;
    private static final int BTN_ORDER = 102;
    private static final int BTN_OPEN_FOLDER = 103;
    private static final int BTN_DONE = 104;
    private static final int BTN_FAST_DELETE = 105;
    private static final int BTN_REFRESH = 106;

    private static final int BTN_ENLARGED_DONE = 200;
    private static final int BTN_ENLARGED_PREV = 201;
    private static final int BTN_ENLARGED_NEXT = 202;
    private static final int BTN_ENLARGED_OPEN = 203;
    private static final int BTN_ENLARGED_COPY = 204;
    private static final int BTN_ENLARGED_DELETE = 205;
    private static final int BTN_ENLARGED_RENAME = 206;

    private static final int LIST_SPACING = 4;
    private static final int SCROLLBAR_WIDTH = 6;
    private static final int SCROLLBAR_SPACING = 2;

    private static final ExecutorService SCANNER = Executors.newSingleThreadExecutor(task -> {
        Thread t = new Thread(task, "Lads 1.8.9 screenshot scan");
        t.setDaemon(true);
        return t;
    });

    private static volatile File qaFolder;
    private static boolean cfgNewestFirst = true;
    private static int cfgPerRow = 4;
    private static int cfgScrollSpeed = 10;
    private static boolean cfgPromptDelete = true;
    private static boolean cfgEnlargeAnim = true;
    private static boolean cfgHideNames = false;
    private static boolean cfgTextShadow = true;

    public static void setQaFolder(File folder) {
        qaFolder = folder;
    }

    private static final class Icon {
        final ResourceLocation texture;
        final int size;

        Icon(String path, int size) {
            this.texture = new ResourceLocation("lads_screenshots", "textures/gui/sprites/" + path + ".png");
            this.size = size;
        }
    }

    private static final class Provenance {
        final String source;
        final boolean readOnly;

        Provenance(String source, boolean readOnly) {
            this.source = source;
            this.readOnly = readOnly;
        }
    }

    private final class Card {
        File file;
        int x;
        int baseY;
        int y;
        int width;
        int height;
        boolean hovered;
        float hoverTime;
        long hoverClock = System.nanoTime();
        boolean selectedForDeletion;
        boolean thumbRequested;
        ResourceLocation thumbTexture;
        int thumbW;
        int thumbH;
        int loadTicket;

        Card(File file) {
            this.file = file;
        }

        void startThumbnail() {
            if (thumbRequested) return;
            thumbRequested = true;
            final File target = this.file;
            final int ticket = ++loadTicket;
            Screenshots189.IO.execute(() -> {
                try {
                    final BufferedImage img = Screenshots189.read(target, 512);
                    mc().addScheduledTask(() -> {
                        if (closed || ticket != loadTicket) return;
                        releaseThumbnailTexture();
                        thumbTexture = Screenshots189.texture("lads_shot_thumb", img);
                        thumbW = img.getWidth();
                        thumbH = img.getHeight();
                    });
                } catch (Throwable ignored) {
                }
            });
        }

        void releaseThumbnailTexture() {
            if (thumbTexture != null) {
                mc().getTextureManager().deleteTexture(thumbTexture);
                thumbTexture = null;
            }
        }

        void close() {
            loadTicket++;
            thumbRequested = false;
            releaseThumbnailTexture();
        }
    }

    private static final class IconGuiButton extends GuiButton {
        private Icon icon;
        final String tooltip;

        IconGuiButton(int id, int x, int y, int width, int height, Icon icon, String tooltip) {
            super(id, x, y, width, height, "");
            this.icon = icon;
            this.tooltip = tooltip;
        }

        void setIcon(Icon icon) {
            this.icon = icon;
        }

        void draw(Minecraft mc, int mouseX, int mouseY) {
            if (!this.visible) return;
            super.drawButton(mc, mouseX, mouseY);
            if (this.icon != null) {
                GlStateManager.enableBlend();
                GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
                float a = this.enabled ? 1.0f : 0.5f;
                GlStateManager.color(1.0f, 1.0f, 1.0f, a);
                mc.getTextureManager().bindTexture(this.icon.texture);
                Gui.drawScaledCustomSizeModalRect(
                    this.xPosition, this.yPosition,
                    0.0f, 0.0f,
                    this.icon.size, this.icon.size,
                    this.width, this.height,
                    this.icon.size, this.icon.size
                );
                GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);
            }
        }

        @Override
        public void drawButton(Minecraft mc, int mouseX, int mouseY) {
            draw(mc, mouseX, mouseY);
        }
    }

    private final GuiScreen parent;
    private final List<Card> cards = new ArrayList<Card>();
    private List<File> files = Collections.emptyList();
    private Map<Path, Provenance> provenanceMap = Collections.emptyMap();
    private List<ScreenshotSources.Root> lastRoots = Collections.emptyList();
    private int selected = -1;
    private boolean newestFirst = cfgNewestFirst;
    private int screenshotsPerRow = cfgPerRow;
    private boolean fastDelete;
    private boolean confirming;
    private boolean closed;
    private String transientNotice = "";
    private long transientNoticeUntil;

    private int listX, listY, listW, listH;
    private int childWidth, childHeight;
    private int scrollY;
    private boolean scrollbarClicked;
    private int scrollbarDragLastY;

    private Future<?> scanFuture;
    private volatile int scanRevision;
    private boolean scanning;
    private boolean scanned;
    private boolean freshScan;
    private final Set<File> deletedDuringScan = new HashSet<File>();
    private final Map<File, File> renamedDuringScan = new HashMap<File, File>();

    private GuiButton addFolderBtn;
    private IconGuiButton configBtn;
    private IconGuiButton orderBtn;
    private IconGuiButton openFolderBtn;
    private GuiButton doneBtn;
    private IconGuiButton fastDeleteBtn;
    private IconGuiButton refreshBtn;

    // Enlarged screenshot modal state
    private Card enlargedCard;
    private ResourceLocation fullTexture;
    private File fullTextureFile;
    private int fullTexW, fullTexH;
    private int fullLoadTicket;
    private float enlargeAnim;
    private long animClock = System.nanoTime();
    private float zoom = 1.0f, panX, panY;
    private boolean panning;
    private int panLastX, panLastY;
    private final List<GuiButton> enlargedButtons = new ArrayList<GuiButton>();
    private GuiButton enlDoneBtn, enlPrevBtn, enlNextBtn;
    private IconGuiButton enlOpenBtn, enlCopyBtn, enlDeleteBtn, enlRenameBtn;

    // Right-click properties menu state
    private boolean propsOpen;
    private Card propsTarget;
    private int propsX, propsY, propsW, propsH;
    private static final String[] PROP_LABELS = {
        "Open file", "Copy Image", "Delete", "Rename screenshot", "Close"
    };
    private static final Icon[] PROP_ICONS = {
        ICON_OPEN_FOLDER, ICON_COPY, ICON_DELETE, ICON_RENAME, ICON_CLOSE
    };

    // Pending delete target(s)
    private List<Card> pendingDelete = Collections.emptyList();

    // Inline rename modal state
    private boolean renameOpen;
    private Card renameTarget;
    private String renameOriginalStem = "";
    private GuiTextField renameField;
    private GuiButton renameDoneBtn;
    private GuiButton renameBackBtn;

    // Inline config modal state
    private boolean configOpen;
    private final List<GuiButton> configButtons = new ArrayList<GuiButton>();

    public ScreenshotsScreen189(GuiScreen parent) {
        this.parent = parent;
        this.newestFirst = cfgNewestFirst;
        this.screenshotsPerRow = cfgPerRow;
        startScan();
    }

    /** QA & Probe150e: files in current display order, selected file, and whether the selected card has loaded its preview texture. */
    public List<File> files() {
        return files;
    }

    public File selectedFile() {
        return selected >= 0 && selected < files.size() ? files.get(selected) : null;
    }

    public boolean previewShown() {
        if (selected < 0 || selected >= cards.size()) return false;
        Card c = cards.get(selected);
        return c != null && c.thumbTexture != null;
    }

    @Override
    public void initGui() {
        closed = false;
        listX = 12;
        listY = 24;
        listW = Math.max(80, width - 24);
        listH = Math.max(40, height - 76);

        buttonList.clear();
        addFolderBtn = new GuiButton(BTN_ADD_FOLDER, width - 90, 2, 88, 20, "Add folder");
        configBtn = new IconGuiButton(BTN_CONFIG, 2, 2, 20, 20, ICON_CONFIG, "Configure...");
        int btnY = height - 28;
        orderBtn = new IconGuiButton(BTN_ORDER, 8, btnY, 20, 20, newestFirst ? ICON_DESCENDING : ICON_ASCENDING, "Sorting Order");
        openFolderBtn = new IconGuiButton(BTN_OPEN_FOLDER, 36, btnY, 20, 20, ICON_OPEN_FOLDER, "Open Screenshot Folder");
        doneBtn = new GuiButton(BTN_DONE, (width - 200) / 2, btnY, 200, 20, "Done");
        fastDeleteBtn = new IconGuiButton(BTN_FAST_DELETE, width - 56, btnY, 20, 20, fastDelete ? ICON_FAST_DELETE : ICON_DELETE, "Toggle Fast Delete");
        refreshBtn = new IconGuiButton(BTN_REFRESH, width - 28, btnY, 20, 20, ICON_REFRESH, "Refresh Screenshots (press F5)");

        buttonList.add(addFolderBtn);
        buttonList.add(configBtn);
        buttonList.add(orderBtn);
        buttonList.add(openFolderBtn);
        buttonList.add(doneBtn);
        buttonList.add(fastDeleteBtn);
        buttonList.add(refreshBtn);

        initEnlargedButtons();
        if (renameOpen && renameTarget != null) {
            initRenameModal(renameField != null ? renameField.getText() : renameOriginalStem);
        }
        if (configOpen) {
            initConfigModal();
        }
        propsOpen = false;
        if (!scanned && !scanning) {
            startScan();
        } else {
            layoutCards(false);
        }
    }

    private void initEnlargedButtons() {
        enlargedButtons.clear();
        int spacing = 4;
        int rightX = width - spacing * 2 - 20;
        enlDoneBtn = new GuiButton(BTN_ENLARGED_DONE, (width - 52) / 2, height - 20 - spacing * 2, 52, 20, "Done");
        enlPrevBtn = new GuiButton(BTN_ENLARGED_PREV, spacing * 2, (height - 20) / 2, 20, 20, "<");
        enlNextBtn = new GuiButton(BTN_ENLARGED_NEXT, rightX, (height - 20) / 2, 20, 20, ">");
        enlOpenBtn = new IconGuiButton(BTN_ENLARGED_OPEN, rightX, height - 100 - spacing * 6, 20, 20, ICON_OPEN_FOLDER, "Open file");
        enlCopyBtn = new IconGuiButton(BTN_ENLARGED_COPY, rightX, height - 80 - spacing * 5, 20, 20, ICON_COPY, "Copy Image");
        enlDeleteBtn = new IconGuiButton(BTN_ENLARGED_DELETE, rightX, height - 60 - spacing * 4, 20, 20, ICON_DELETE, "Delete");
        enlRenameBtn = new IconGuiButton(BTN_ENLARGED_RENAME, rightX, height - 40 - spacing * 3, 20, 20, ICON_RENAME, "Rename screenshot");
        enlargedButtons.add(enlDoneBtn);
        enlargedButtons.add(enlPrevBtn);
        enlargedButtons.add(enlNextBtn);
        enlargedButtons.add(enlOpenBtn);
        enlargedButtons.add(enlCopyBtn);
        enlargedButtons.add(enlDeleteBtn);
        enlargedButtons.add(enlRenameBtn);
        updateEnlargedButtons();
    }

    private void startScan() {
        cancelScan();
        final int revision = scanRevision;
        freshScan = true;
        scanned = false;
        deletedDuringScan.clear();
        renamedDuringScan.clear();

        final File forcedLocal = qaFolder;
        final List<ScreenshotSources.Root> customRoots = forcedLocal != null
            ? Collections.<ScreenshotSources.Root>emptyList()
            : safeCustomRoots();
        final boolean useSharedDiscovery = forcedLocal == null && (SharedContentPaths.redirectEnabled() || !customRoots.isEmpty());

        if (!useSharedDiscovery) {
            File dir = forcedLocal != null ? forcedLocal : Screenshots189.folder();
            File[] raw = dir.listFiles(Screenshots189::image);
            List<File> list = raw == null ? Collections.<File>emptyList() : Arrays.asList(raw);
            List<File> oldestFirst = sortOldestFirst(list, new HashMap<File, Long>());
            provenanceMap = Collections.emptyMap();
            lastRoots = Collections.emptyList();
            scanned = true;
            installOldestFirst(oldestFirst);
            return;
        }

        scanning = true;
        final Path gamePath = mc().mcDataDir.toPath();
        scanFuture = SCANNER.submit(() -> {
            final Map<File, Long> times = new HashMap<File, Long>();
            try {
                final Path sharedShots = canonical(SharedContentPaths.screenshotsDir());
                final Path localShots = canonical(gamePath.resolve("screenshots"));
                final List<ScreenshotSources.Root> roots = ScreenshotSources.discover(
                    SharedContentPaths.root(),
                    gamePath,
                    System.getenv(),
                    Paths.get(System.getProperty("user.home"))
                );
                lastRoots = roots;
                final Map<Path, Provenance> foundProv = new HashMap<Path, Provenance>();
                List<ScreenshotSources.Image> result = ScreenshotSources.scan(
                    roots,
                    () -> revision != scanRevision,
                    partial -> {
                        final List<File> partialFiles = toFiles(partial, sharedShots, localShots, foundProv);
                        final List<File> sorted = sortOldestFirst(partialFiles, times);
                        final Map<Path, Provenance> snap = new HashMap<Path, Provenance>(foundProv);
                        mc().addScheduledTask(() -> {
                            if (closed || revision != scanRevision) return;
                            provenanceMap = snap;
                            installOldestFirst(sorted);
                        });
                    }
                );
                if (revision != scanRevision) return;
                final List<File> finalFiles = toFiles(result, sharedShots, localShots, foundProv);
                final List<File> sorted = sortOldestFirst(finalFiles, times);
                final Map<Path, Provenance> snap = new HashMap<Path, Provenance>(foundProv);
                mc().addScheduledTask(() -> {
                    if (closed || revision != scanRevision) return;
                    scanning = false;
                    scanned = true;
                    provenanceMap = snap;
                    installOldestFirst(sorted);
                });
            } catch (Throwable error) {
                mc().addScheduledTask(() -> {
                    if (revision != scanRevision) return;
                    scanning = false;
                    scanned = true;
                    showNotice("Scan failed: " + error.getMessage());
                });
            }
        });
    }

    private static List<ScreenshotSources.Root> safeCustomRoots() {
        try {
            return ScreenshotSources.customRoots(SharedContentPaths.root());
        } catch (Throwable t) {
            return Collections.emptyList();
        }
    }

    private static List<File> toFiles(
        List<ScreenshotSources.Image> images,
        Path sharedShots,
        Path localShots,
        Map<Path, Provenance> outProv
    ) {
        List<File> list = new ArrayList<File>(images.size());
        for (ScreenshotSources.Image img : images) {
            Path p = img.path();
            list.add(p.toFile());
            if (!outProv.containsKey(p)) {
                boolean ro = !p.startsWith(sharedShots) && !p.startsWith(localShots);
                outProv.put(p, new Provenance(img.source(), ro));
            }
        }
        return list;
    }

    private static List<File> sortOldestFirst(List<File> input, Map<File, Long> times) {
        for (File f : input) {
            if (!times.containsKey(f)) {
                times.put(f, Long.valueOf(f.lastModified()));
            }
        }
        List<File> sorted = new ArrayList<File>(input);
        Collections.sort(sorted, new Comparator<File>() {
            @Override
            public int compare(File a, File b) {
                long ta = times.get(a).longValue();
                long tb = times.get(b).longValue();
                int cmp = Long.compare(ta, tb);
                return cmp != 0 ? cmp : a.getAbsolutePath().compareTo(b.getAbsolutePath());
            }
        });
        return sorted;
    }

    private void installOldestFirst(List<File> oldestFirst) {
        if (!deletedDuringScan.isEmpty() || !renamedDuringScan.isEmpty()) {
            List<File> adjusted = new ArrayList<File>(oldestFirst.size());
            Set<File> seen = new HashSet<File>();
            for (File f : oldestFirst) {
                if (deletedDuringScan.contains(f)) continue;
                File cur = renamedDuringScan.containsKey(f) ? renamedDuringScan.get(f) : f;
                if (seen.add(cur)) adjusted.add(cur);
            }
            oldestFirst = adjusted;
        }

        File previouslySelected = selectedFile();
        File previouslyEnlarged = enlargedCard != null ? enlargedCard.file : null;

        Map<File, Card> kept = new HashMap<File, Card>();
        if (freshScan) {
            closeAllCards();
        } else {
            for (Card c : cards) {
                kept.put(c.file, c);
            }
        }
        int savedScroll = freshScan ? 0 : scrollY;
        cards.clear();
        List<File> displayFiles = new ArrayList<File>(oldestFirst.size());
        for (int i = 0; i < oldestFirst.size(); i++) {
            File f = oldestFirst.get(newestFirst ? oldestFirst.size() - 1 - i : i);
            Card card = kept.remove(f);
            if (card == null) card = new Card(f);
            cards.add(card);
            displayFiles.add(f);
        }
        for (Card old : kept.values()) {
            old.close();
        }
        files = Collections.unmodifiableList(displayFiles);
        freshScan = false;

        if (files.isEmpty()) {
            selected = -1;
        } else if (previouslySelected != null && files.contains(previouslySelected)) {
            selected = files.indexOf(previouslySelected);
        } else {
            selected = 0;
        }

        if (previouslyEnlarged != null) {
            Card match = findCard(previouslyEnlarged);
            setEnlarged(match);
        }

        layoutCards(false);
        scrollY = clampScroll(savedScroll);

        // Kick off selected card's thumbnail immediately so Probe150e sees previewShown() without waiting on hover/render if needed.
        if (selected >= 0 && selected < cards.size()) {
            cards.get(selected).startThumbnail();
        }
    }

    private void cancelScan() {
        scanRevision++;
        scanning = false;
        if (scanFuture != null) {
            scanFuture.cancel(true);
            scanFuture = null;
        }
    }

    private void closeAllCards() {
        for (Card c : cards) {
            c.close();
        }
        cards.clear();
    }

    private void layoutCards(boolean resetScroll) {
        if (listW <= 0 || listH <= 0) return;
        if (resetScroll) scrollY = 0;
        Minecraft m = mc();
        float windowAspect = m.displayHeight > 0 ? (float) m.displayWidth / (float) m.displayHeight : (16.0f / 9.0f);
        if (windowAspect <= 0.1f) windowAspect = 16.0f / 9.0f;
        childWidth = Math.max(24, (listW - (screenshotsPerRow + 1) * LIST_SPACING - SCROLLBAR_WIDTH - SCROLLBAR_SPACING) / screenshotsPerRow);
        childHeight = Math.max(18, (int) ((cfgHideNames ? 1.0f : 1.08f) * childWidth / windowAspect));

        int maxCol = screenshotsPerRow - 1;
        int cx = listX + LIST_SPACING;
        int cy = listY + LIST_SPACING;
        int col = 0;
        for (Card card : cards) {
            card.x = cx;
            card.baseY = cy;
            card.width = childWidth;
            card.height = childHeight;
            if (col >= maxCol) {
                col = 0;
                cx = listX + LIST_SPACING;
                cy += childHeight + LIST_SPACING;
            } else {
                col++;
                cx += childWidth + LIST_SPACING;
            }
        }
        scrollY = clampScroll(scrollY);
    }

    private int totalChildrenHeight() {
        if (cards.isEmpty()) return 0;
        int rows = (cards.size() + screenshotsPerRow - 1) / screenshotsPerRow;
        return rows * childHeight + LIST_SPACING * (rows - 1);
    }

    private int viewHeight() {
        return Math.max(1, listH - 2 * LIST_SPACING);
    }

    private boolean canScroll() {
        return totalChildrenHeight() > viewHeight();
    }

    private int clampScroll(int value) {
        int max = Math.max(0, totalChildrenHeight() - viewHeight());
        return MathHelper.clamp_int(value, 0, max);
    }

    private void invertOrder() {
        Collections.reverse(cards);
        List<File> reversed = new ArrayList<File>(cards.size());
        for (Card c : cards) reversed.add(c.file);
        files = Collections.unmodifiableList(reversed);
        newestFirst = !newestFirst;
        if (orderBtn != null) {
            orderBtn.setIcon(newestFirst ? ICON_DESCENDING : ICON_ASCENDING);
        }
        int prevScroll = scrollY;
        layoutCards(false);
        scrollY = clampScroll(prevScroll);
        updateEnlargedButtons();
    }

    private List<Card> deletionList() {
        if (!fastDelete) return Collections.emptyList();
        List<Card> out = new ArrayList<Card>();
        for (Card c : cards) {
            if (c.selectedForDeletion) out.add(c);
        }
        return out;
    }

    private void resetDeleteSelection() {
        for (Card c : cards) c.selectedForDeletion = false;
    }

    private boolean isReadOnly(File file) {
        if (file == null) return true;
        if (qaFolder != null) return false;
        Provenance prov = provenanceMap.get(canonical(file.toPath()));
        if (prov != null) return prov.readOnly;
        Path p = canonical(file.toPath());
        Path shared = canonical(SharedContentPaths.screenshotsDir());
        Path local = canonical(mc().mcDataDir.toPath().resolve("screenshots"));
        return !p.startsWith(shared) && !p.startsWith(local);
    }

    private String sourceLabel(File file) {
        Provenance prov = provenanceMap.get(canonical(file.toPath()));
        if (prov != null) return prov.source;
        return isReadOnly(file) ? "External folder / " + file.getParent() : "Lads screenshots";
    }

    private static Path canonical(Path path) {
        try {
            return path.toRealPath();
        } catch (IOException ignored) {
            return path.toAbsolutePath().normalize();
        }
    }

    @Override
    public void updateScreen() {
        if (renameOpen && renameField != null) {
            renameField.updateCursorCounter();
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();

        long now = System.nanoTime();
        float elapsed = MathHelper.clamp_float((now - animClock) / 1_000_000_000f, 0.0f, 0.1f);
        animClock = now;

        boolean ctrlDown = isCtrlKeyDown();
        boolean modalUp = enlargedCard != null || propsOpen || renameOpen || configOpen;

        List<Card> marked = deletionList();
        if (doneBtn != null) {
            doneBtn.displayString = fastDelete && !marked.isEmpty()
                ? "\u00a7cDelete " + marked.size() + " screenshot(s)"
                : "Done";
        }
        if (fastDeleteBtn != null) {
            fastDeleteBtn.setIcon(fastDelete ? ICON_FAST_DELETE : ICON_DELETE);
        }
        if (orderBtn != null) {
            orderBtn.setIcon(newestFirst ? ICON_DESCENDING : ICON_ASCENDING);
        }

        renderGrid(mouseX, mouseY, !modalUp);

        drawCenteredString(fontRendererObj, "Manage Screenshots...", width / 2, 8, 0xFFFFFF);

        if (fastDelete) {
            drawCenteredString(fontRendererObj, "Fast Delete Mode", width / 2, height - 43, -1359820);
        } else if (ctrlDown) {
            drawCenteredString(fontRendererObj, "Zoom Mode (Hold CTRL)", width / 2, height - 43, -15147463);
        } else if (!transientNotice.isEmpty() && System.currentTimeMillis() < transientNoticeUntil) {
            drawCenteredString(fontRendererObj, fontRendererObj.trimStringToWidth(transientNotice, width - 24), width / 2, height - 43, 0xFFFF55);
        } else {
            String statusText = scanning
                ? "Scanning instance screenshots... " + cards.size() + " found"
                : cards.size() + " screenshots | external originals are read-only";
            drawCenteredString(fontRendererObj, statusText, width / 2, height - 43, 0xFFC5B7BF);
        }

        super.drawScreen(modalUp ? -1000 : mouseX, modalUp ? -1000 : mouseY, partialTicks);

        // Tooltips on main buttons when no modal is open
        String hoverTooltip = null;
        if (!modalUp) {
            hoverTooltip = buttonTooltipAt(mouseX, mouseY);
        }

        if (enlargedCard != null) {
            renderEnlarged(mouseX, mouseY, elapsed, !propsOpen && !renameOpen && !configOpen);
            if (!propsOpen && !renameOpen && !configOpen) {
                hoverTooltip = enlargedTooltipAt(mouseX, mouseY);
            }
        } else {
            enlargeAnim = 0.0f;
        }

        if (renameOpen) {
            renderRenameModal(mouseX, mouseY);
        } else if (configOpen) {
            renderConfigModal(mouseX, mouseY);
        } else if (propsOpen) {
            renderPropertiesMenu(mouseX, mouseY);
        }

        if (hoverTooltip != null && !propsOpen && !renameOpen && !configOpen) {
            drawHoveringText(Arrays.asList(hoverTooltip.split("\n")), mouseX, mouseY);
        }
    }

    private void renderGrid(int mouseX, int mouseY, boolean allowHover) {
        Gui.drawRect(listX, listY, listX + listW, listY + listH, 0xB2000000);
        if (cards.isEmpty()) {
            String emptyMsg = scanning
                ? "Scanning instance screenshots..."
                : "Looks like you have not taken any screenshot yet!";
            drawCenteredString(fontRendererObj, emptyMsg, listX + listW / 2, listY + (listH - 8) / 2, 0xFFFFFF);
            return;
        }

        int viewportY = listY + LIST_SPACING;
        int viewportBottom = listY + listH - LIST_SPACING;

        Card readOnlyHover = null;
        enableScissor(listX, viewportY, listW, Math.max(1, viewportBottom - viewportY));
        for (int i = 0; i < cards.size(); i++) {
            Card card = cards.get(i);
            card.y = card.baseY - scrollY;
            long now = System.nanoTime();
            float dt = Math.min(0.1f, (now - card.hoverClock) / 1_000_000_000f);
            card.hoverClock = now;

            card.hovered = allowHover
                && mouseX >= card.x
                && mouseX < card.x + card.width
                && mouseY >= Math.max(card.y, viewportY)
                && mouseY < Math.min(card.y + card.height, viewportBottom);
            card.hoverTime = card.hovered ? card.hoverTime + dt * 20.0f : 0.0f;

            if (card.y + card.height < listY || card.y > listY + listH) {
                if (card != enlargedCard && i != selected) {
                    card.close();
                }
                continue;
            }

            card.startThumbnail();

            int hoverAlpha = (int) (Math.min(card.hoverTime, 10.0f) / 10.0f * 255.0f);
            if (hoverAlpha > 0) {
                Gui.drawRect(card.x, card.y, card.x + card.width, card.y + card.height, (hoverAlpha << 24) | 0xFFFFFF);
            }

            int imgHeight = Math.max(1, (int) (card.height / (cfgHideNames ? 1.0f : 1.08f) - 6.0f));
            int imgX = card.x + 2;
            int imgY = card.y + 2;
            int imgW = Math.max(1, card.width - 4);
            if (card.thumbTexture != null && card.thumbW > 0 && card.thumbH > 0) {
                GlStateManager.enableTexture2D();
                GlStateManager.enableBlend();
                GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);
                mc.getTextureManager().bindTexture(card.thumbTexture);
                Gui.drawModalRectWithCustomSizedTexture(imgX, imgY, 0.0f, 0.0f, imgW, imgHeight, imgW, imgHeight);
            } else {
                Gui.drawRect(imgX, imgY, imgX + imgW, imgY + imgHeight, 0x44222222);
            }

            if (fastDelete && card.selectedForDeletion) {
                Gui.drawRect(imgX, imgY, imgX + imgW, imgY + imgHeight, 1358888960);
            }

            if (!cfgHideNames) {
                float scaleFactor = Math.max(1, height / 96) / (float) screenshotsPerRow;
                int textY = card.y + (int) (card.height / 1.08f) - 2;
                GlStateManager.pushMatrix();
                GlStateManager.translate(card.x + card.width / 2.0f, textY, 0.0f);
                GlStateManager.scale(scaleFactor, scaleFactor, 1.0f);
                String label = card.file.getName();
                int maxCharsWidth = (int) ((card.width - 4) / Math.max(0.2f, scaleFactor));
                String clipped = fontRendererObj.trimStringToWidth(label, maxCharsWidth);
                int tx = -fontRendererObj.getStringWidth(clipped) / 2;
                fontRendererObj.drawString(clipped, tx, 0, 0xFFFFFF, cfgTextShadow);
                GlStateManager.popMatrix();
            }

            if (card.hovered && card.hoverTime > 20.0f && !fastDelete && isReadOnly(card.file)) {
                readOnlyHover = card;
            }
        }
        disableScissor();

        if (canScroll()) {
            renderScrollbar(mouseX, mouseY, allowHover);
        }

        if (readOnlyHover != null && allowHover) {
            drawHoveringText(
                Arrays.asList(sourceLabel(readOnlyHover.file), "External original | read-only"),
                mouseX,
                mouseY
            );
        }
    }

    private int scrollbarThumbHeight() {
        int trackH = viewHeight();
        int spacedTrackH = trackH + 4;
        int total = totalChildrenHeight();
        return total <= 0 ? trackH : Math.max(12, trackH * spacedTrackH / total);
    }

    private int scrollbarThumbY() {
        int trackH = viewHeight();
        int spacedTrackH = trackH + 4;
        int total = Math.max(1, totalChildrenHeight());
        return MathHelper.ceiling_float_int((float) (scrollY * spacedTrackH) / total) + listY + 2;
    }

    private void renderScrollbar(int mouseX, int mouseY, boolean allowHover) {
        int barX = listX + listW - SCROLLBAR_SPACING - SCROLLBAR_WIDTH;
        int trackX = barX + 2;
        int trackY = listY + LIST_SPACING;
        int trackH = viewHeight();
        int thumbY = scrollbarThumbY();
        int thumbH = scrollbarThumbHeight();
        boolean hovered = allowHover && mouseX >= barX && mouseX < barX + SCROLLBAR_WIDTH && mouseY >= thumbY && mouseY < thumbY + thumbH;
        Gui.drawRect(trackX, trackY, trackX + 2, trackY + trackH, -1);
        int thumbColor = scrollbarClicked ? -1 : (hovered ? -9605779 : -14803426);
        Gui.drawRect(barX, thumbY, barX + SCROLLBAR_WIDTH, thumbY + thumbH, thumbColor);
    }

    private void setEnlarged(Card card) {
        this.enlargedCard = card;
        this.zoom = 1.0f;
        this.panX = 0.0f;
        this.panY = 0.0f;
        this.panning = false;
        if (card == null) {
            releaseFullTexture();
            return;
        }
        int idx = cards.indexOf(card);
        if (idx >= 0) selected = idx;
        updateEnlargedButtons();
        loadFullTexture(card.file);
    }

    private void loadFullTexture(final File file) {
        if (file == null || (file.equals(fullTextureFile) && fullTexture != null)) return;
        final int ticket = ++fullLoadTicket;
        Screenshots189.IO.execute(() -> {
            try {
                final BufferedImage img = Screenshots189.read(file, 4096);
                mc().addScheduledTask(() -> {
                    if (closed || ticket != fullLoadTicket) return;
                    releaseFullTexture();
                    fullTexture = Screenshots189.texture("lads_shot_full", img);
                    fullTextureFile = file;
                    fullTexW = img.getWidth();
                    fullTexH = img.getHeight();
                });
            } catch (Throwable failed) {
                mc().addScheduledTask(() -> {
                    if (ticket == fullLoadTicket) showNotice("Cannot read " + file.getName());
                });
            }
        });
    }

    private void releaseFullTexture() {
        if (fullTexture != null) {
            mc().getTextureManager().deleteTexture(fullTexture);
            fullTexture = null;
        }
        fullTextureFile = null;
    }

    private void updateEnlargedButtons() {
        if (enlargedCard == null || enlDeleteBtn == null) return;
        boolean ro = isReadOnly(enlargedCard.file);
        enlDeleteBtn.enabled = !ro;
        enlRenameBtn.enabled = !ro;
        int idx = cards.indexOf(enlargedCard);
        enlPrevBtn.enabled = idx > 0;
        enlNextBtn.enabled = idx >= 0 && idx < cards.size() - 1;
    }

    private void renderEnlarged(int mouseX, int mouseY, float elapsed, boolean allowHover) {
        drawGradientRect(0, 0, width, height, -1072689136, -804253680);
        float t = 1.0f;
        if (cfgEnlargeAnim && enlargeAnim < 1.0f) {
            enlargeAnim = Math.min(1.0f, enlargeAnim + elapsed * 4.0f);
            t = (float) (1.0 - Math.pow(1.0f - enlargeAnim, 3.0));
        }

        ResourceLocation tex = fullTexture != null && enlargedCard.file.equals(fullTextureFile)
            ? fullTexture
            : enlargedCard.thumbTexture;
        int iw = tex == fullTexture ? fullTexW : enlargedCard.thumbW;
        int ih = tex == fullTexture ? fullTexH : enlargedCard.thumbH;

        if (tex != null && iw > 0 && ih > 0) {
            float fit = Math.min((width - 16.0f) / iw, (height - 44.0f) / ih);
            int drawW = Math.max(1, (int) (iw * fit * zoom));
            int drawH = Math.max(1, (int) (ih * fit * zoom));
            int drawX = (int) ((width - drawW) / 2.0f + panX);
            int drawY = (int) ((height - 36 - drawH) / 2.0f + panY);
            GlStateManager.pushMatrix();
            GlStateManager.translate(width / 2.0f * (1.0f - t), height / 2.0f * (1.0f - t), 0.0f);
            GlStateManager.scale(t, t, 1.0f);
            GlStateManager.enableTexture2D();
            GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);
            mc.getTextureManager().bindTexture(tex);
            Gui.drawModalRectWithCustomSizedTexture(drawX, drawY, 0.0f, 0.0f, drawW, drawH, drawW, drawH);
            GlStateManager.popMatrix();
        }

        int mx = allowHover ? mouseX : -1000;
        int my = allowHover ? mouseY : -1000;
        for (GuiButton b : enlargedButtons) {
            b.drawButton(mc, mx, my);
        }
    }

    private void openPropertiesMenu(int mx, int my, Card target) {
        if (target == null) return;
        propsTarget = target;
        int maxLabel = 0;
        for (String label : PROP_LABELS) {
            maxLabel = Math.max(maxLabel, fontRendererObj.getStringWidth(label));
        }
        propsW = 4 + Math.max(fontRendererObj.getStringWidth(target.file.getName()), 19 + maxLabel + 4);
        propsH = 6 + 9 + 19 * PROP_LABELS.length;
        propsX = mx + propsW > width ? Math.max(2, mx - propsW) : mx;
        propsY = my + propsH > height ? Math.max(2, my - propsH) : my;
        propsOpen = true;
    }

    private void renderPropertiesMenu(int mouseX, int mouseY) {
        if (!propsOpen || propsTarget == null) return;
        GlStateManager.pushMatrix();
        GlStateManager.translate(0.0f, 0.0f, 300.0f);
        GlStateManager.enableBlend();
        GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);
        renderNineSlice(PROPERTIES_BG, 6, 1, propsX, propsY, propsW, propsH);
        fontRendererObj.drawStringWithShadow(propsTarget.file.getName(), propsX + 2, propsY + 2, 0xFFFFFF);

        boolean ro = isReadOnly(propsTarget.file);
        for (int i = 0; i < PROP_LABELS.length; i++) {
            boolean active = !(ro && (i == 2 || i == 3));
            int bx = propsX + 2;
            int by = propsY + 13 + 19 * i;
            int bw = propsW - 4;
            int bh = 19;
            boolean hovered = active && mouseX >= bx && mouseX < bx + bw && mouseY >= by && mouseY < by + bh;
            ResourceLocation bg = !active ? PROP_BTN_DISABLED : (hovered ? PROP_BTN_HOVERED : PROP_BTN_ENABLED);
            GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);
            mc.getTextureManager().bindTexture(bg);
            Gui.drawScaledCustomSizeModalRect(bx, by, 0.0f, 0.0f, 1, 19, 1, bh, 19.0f, 19.0f);
            Gui.drawScaledCustomSizeModalRect(bx + 1, by, 1.0f, 0.0f, 17, 19, bw - 2, bh, 19.0f, 19.0f);
            Gui.drawScaledCustomSizeModalRect(bx + bw - 1, by, 18.0f, 0.0f, 1, 19, 1, bh, 19.0f, 19.0f);

            Icon icon = PROP_ICONS[i];
            mc.getTextureManager().bindTexture(icon.texture);
            Gui.drawScaledCustomSizeModalRect(bx, by, 0.0f, 0.0f, icon.size, icon.size, 19, bh, icon.size, icon.size);

            int textColor = active ? 0xFFFFFF : 0x888888;
            fontRendererObj.drawStringWithShadow(PROP_LABELS[i], bx + 21, by + 6, textColor);
        }
        GlStateManager.popMatrix();
    }

    private void renderNineSlice(ResourceLocation tex, int size, int border, int x, int y, int w, int h) {
        int inner = size - 2 * border;
        int right = x + w - border;
        int bottom = y + h - border;
        mc.getTextureManager().bindTexture(tex);
        Gui.drawScaledCustomSizeModalRect(x, y, 0, 0, border, border, border, border, size, size);
        Gui.drawScaledCustomSizeModalRect(right, y, size - border, 0, border, border, border, border, size, size);
        Gui.drawScaledCustomSizeModalRect(x, bottom, 0, size - border, border, border, border, border, size, size);
        Gui.drawScaledCustomSizeModalRect(right, bottom, size - border, size - border, border, border, border, border, size, size);
        Gui.drawScaledCustomSizeModalRect(x + border, y, border, 0, inner, border, w - 2 * border, border, size, size);
        Gui.drawScaledCustomSizeModalRect(x + border, bottom, border, size - border, inner, border, w - 2 * border, border, size, size);
        Gui.drawScaledCustomSizeModalRect(x, y + border, 0, border, border, inner, border, h - 2 * border, size, size);
        Gui.drawScaledCustomSizeModalRect(right, y + border, size - border, border, border, inner, border, h - 2 * border, size, size);
        Gui.drawScaledCustomSizeModalRect(x + border, y + border, border, border, inner, inner, w - 2 * border, h - 2 * border, size, size);
    }

    private void initRenameModal(String draft) {
        renameField = new GuiTextField(300, fontRendererObj, (width - 150) / 2, (height - 20) / 2, 150, 20);
        renameField.setMaxStringLength(128);
        renameField.setFocused(true);
        renameField.setText(draft);
        renameDoneBtn = new GuiButton(301, width / 2 - 4 - 150, height / 2 + 50, 150, 20, "Done");
        renameBackBtn = new GuiButton(302, width / 2 + 4, height / 2 + 50, 150, 20, "Back");
        updateRenameState();
    }

    private void updateRenameState() {
        if (renameDoneBtn == null || renameField == null) return;
        String trimmed = renameField.getText().trim();
        renameDoneBtn.enabled = validStem(trimmed) && !trimmed.equals(renameOriginalStem);
    }

    private static boolean validStem(String s) {
        if (s == null || s.trim().isEmpty()) return false;
        String t = s.trim();
        if (".".equals(t) || "..".equals(t) || t.endsWith(".")) return false;
        for (int i = 0; i < t.length(); i++) {
            char c = t.charAt(i);
            if (c < 32 || "\\/:*?\"<>|".indexOf(c) >= 0) return false;
        }
        return true;
    }

    private void renderRenameModal(int mouseX, int mouseY) {
        drawGradientRect(0, 0, width, height, -1072689136, -804253680);
        drawCenteredString(fontRendererObj, "Enter a new name for your screenshot:", width / 2, height / 2 - 70, 0xFFFFFF);
        if (renameField != null) renameField.drawTextBox();
        if (renameDoneBtn != null) renameDoneBtn.drawButton(mc, mouseX, mouseY);
        if (renameBackBtn != null) renameBackBtn.drawButton(mc, mouseX, mouseY);
    }

    private void applyRename() {
        if (renameTarget == null || renameField == null) return;
        String stem = renameField.getText().trim();
        if (!validStem(stem) || stem.equals(renameOriginalStem)) return;
        File oldFile = renameTarget.file;
        String name = oldFile.getName();
        int dot = name.lastIndexOf('.');
        String ext = dot >= 0 ? name.substring(dot) : ".png";
        Path target = oldFile.toPath().resolveSibling(stem + ext);
        try {
            if (Files.exists(target)) {
                showNotice("A file with that name already exists");
                return;
            }
            Path moved = Files.move(oldFile.toPath(), target, StandardCopyOption.ATOMIC_MOVE);
            File movedFile = moved.toFile();
            renamedDuringScan.put(oldFile, movedFile);
            renameTarget.close();
            renameTarget.file = movedFile;
            renameTarget.startThumbnail();
            List<File> updated = new ArrayList<File>(cards.size());
            for (Card c : cards) updated.add(c.file);
            files = Collections.unmodifiableList(updated);
            if (enlargedCard == renameTarget) {
                releaseFullTexture();
                loadFullTexture(movedFile);
            }
            renameOpen = false;
            renameTarget = null;
        } catch (IOException fallback) {
            try {
                Path moved = Files.move(oldFile.toPath(), target);
                File movedFile = moved.toFile();
                renamedDuringScan.put(oldFile, movedFile);
                renameTarget.close();
                renameTarget.file = movedFile;
                renameTarget.startThumbnail();
                List<File> updated = new ArrayList<File>(cards.size());
                for (Card c : cards) updated.add(c.file);
                files = Collections.unmodifiableList(updated);
                renameOpen = false;
                renameTarget = null;
            } catch (IOException err) {
                showNotice("Rename failed: " + err.getMessage());
            }
        }
    }

    private void initConfigModal() {
        configButtons.clear();
        int cx = width / 2 - 105;
        int y0 = Math.max(36, height / 2 - 74);
        configButtons.add(new GuiButton(400, cx, y0, 210, 20, "Default Order: " + (cfgNewestFirst ? "Descending" : "Ascending")));
        configButtons.add(new GuiButton(401, cx, y0 + 24, 210, 20, "Screenshots Per Row: " + cfgPerRow));
        configButtons.add(new GuiButton(402, cx, y0 + 48, 210, 20, "Prompt When Deleting: " + (cfgPromptDelete ? "ON" : "OFF")));
        configButtons.add(new GuiButton(403, cx, y0 + 72, 210, 20, "Enlargement Animation: " + (cfgEnlargeAnim ? "ON" : "OFF")));
        configButtons.add(new GuiButton(404, cx, y0 + 96, 210, 20, "Screenshot Names: " + (cfgHideNames ? "Hidden" : "Visible")));
        configButtons.add(new GuiButton(405, cx, y0 + 128, 210, 20, "Done"));
    }

    private void renderConfigModal(int mouseX, int mouseY) {
        drawGradientRect(0, 0, width, height, -1072689136, -804253680);
        drawCenteredString(fontRendererObj, "Screenshot Viewer Config", width / 2, Math.max(16, height / 2 - 94), 0xFFFFFF);
        for (GuiButton b : configButtons) {
            b.drawButton(mc, mouseX, mouseY);
        }
    }

    private void requestDeleteSingle(Card card) {
        if (card == null || isReadOnly(card.file)) return;
        requestDeleteCards(Collections.singletonList(card));
    }

    private void requestDeleteCards(List<Card> targets) {
        List<Card> writable = new ArrayList<Card>();
        for (Card c : targets) {
            if (c != null && !isReadOnly(c.file)) writable.add(c);
        }
        if (writable.isEmpty()) return;
        pendingDelete = writable;
        if (cfgPromptDelete) {
            confirming = true;
            String title = writable.size() == 1
                ? "Delete '" + writable.get(0).file.getName() + "'"
                : "Delete " + writable.size() + " screenshot(s)";
            String msg = writable.size() == 1
                ? "Are you sure you want to delete this screenshot ? (it will be lost forever!)"
                : "Are you sure you want to delete these screenshots ? (they will be lost forever!)";
            mc.displayGuiScreen(new GuiYesNo(this, title, msg, 0));
        } else {
            executeDelete(writable);
        }
    }

    @Override
    public void confirmClicked(boolean result, int id) {
        List<Card> targets = pendingDelete;
        pendingDelete = Collections.emptyList();
        mc.displayGuiScreen(this);
        if (!result || targets.isEmpty()) {
            resetDeleteSelection();
            return;
        }
        executeDelete(targets);
    }

    private void executeDelete(final List<Card> targets) {
        for (Card c : targets) {
            deletedDuringScan.add(c.file);
            if (c == enlargedCard) setEnlarged(null);
            c.close();
            cards.remove(c);
        }
        List<File> remaining = new ArrayList<File>(cards.size());
        for (Card c : cards) remaining.add(c.file);
        files = Collections.unmodifiableList(remaining);
        selected = files.isEmpty() ? -1 : Math.min(Math.max(0, selected), files.size() - 1);
        layoutCards(false);
        updateEnlargedButtons();

        final List<ScreenshotSources.Root> rootsSnap = lastRoots;
        Screenshots189.IO.execute(() -> {
            for (Card c : targets) {
                File f = c.file;
                try {
                    List<Path> copies = Collections.emptyList();
                    if (f.getParentFile() != null && canonical(f.getParentFile().toPath()).equals(canonical(SharedContentPaths.screenshotsDir()))) {
                        copies = ScreenshotSources.syncedCopies(rootsSnap, f.toPath(), f.length());
                    }
                    deleteOrTrash(f);
                    for (Path copy : copies) {
                        deleteOrTrash(copy.toFile());
                    }
                } catch (Throwable ignored) {
                }
            }
        });
    }

    private static void deleteOrTrash(File file) throws IOException {
        try {
            if (com.sun.jna.platform.FileUtils.getInstance().hasTrash()) {
                com.sun.jna.platform.FileUtils.getInstance().moveToTrash(new File[] {file});
                if (!file.exists()) return;
            }
        } catch (Throwable ignored) {
        }
        Files.deleteIfExists(file.toPath());
    }

    private void openAddFolderPicker() {
        if (addFolderBtn != null) addFolderBtn.enabled = false;
        FileDialogs.openFolder("Add screenshot folder").whenComplete((folder, error) -> mc().addScheduledTask(() -> {
            if (addFolderBtn != null) addFolderBtn.enabled = true;
            if (error != null) {
                showNotice("Folder dialog failed: " + error.getMessage());
                return;
            }
            if (folder == null) return;
            Screenshots189.IO.execute(() -> {
                try {
                    ScreenshotSources.addCustomRoot(SharedContentPaths.root(), folder);
                    mc().addScheduledTask(() -> {
                        if (!closed) startScan();
                    });
                } catch (Throwable failure) {
                    mc().addScheduledTask(() -> showNotice("Add folder failed: " + failure.getMessage()));
                }
            });
        }));
    }

    private void openSystemPath(final File target) {
        if (target == null) return;
        Screenshots189.IO.execute(() -> {
            try {
                if (!target.exists() && target.equals(Screenshots189.folder())) {
                    target.mkdirs();
                }
                Desktop.getDesktop().open(target);
            } catch (Throwable noDesktop) {
                Sys.openURL(target.toURI().toString());
            }
        });
    }

    private void copyToClipboard(final File file) {
        if (file == null) return;
        showNotice("Copying " + file.getName() + "...");
        Screenshots189.IO.execute(() -> {
            String msg;
            try {
                BufferedImage image = Screenshots189.read(file, 16384);
                final BufferedImage rgb = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_RGB);
                java.awt.Graphics2D g = rgb.createGraphics();
                try {
                    g.drawImage(image, 0, 0, null);
                } finally {
                    g.dispose();
                }
                Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new Transferable() {
                    @Override
                    public DataFlavor[] getTransferDataFlavors() {
                        return new DataFlavor[] {DataFlavor.imageFlavor};
                    }

                    @Override
                    public boolean isDataFlavorSupported(DataFlavor flavor) {
                        return DataFlavor.imageFlavor.equals(flavor);
                    }

                    @Override
                    public Object getTransferData(DataFlavor flavor) throws UnsupportedFlavorException {
                        if (!isDataFlavorSupported(flavor)) throw new UnsupportedFlavorException(flavor);
                        return rgb;
                    }
                }, null);
                msg = "Successfully copied screenshot!";
            } catch (Throwable failed) {
                msg = "Failed to copy screenshot: " + failed.getMessage();
            }
            final String text = msg;
            mc().addScheduledTask(() -> showNotice(text));
        });
    }

    private void showNotice(String text) {
        transientNotice = text;
        transientNoticeUntil = System.currentTimeMillis() + 3500L;
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button.id == BTN_ADD_FOLDER) {
            openAddFolderPicker();
        } else if (button.id == BTN_CONFIG) {
            configOpen = true;
            initConfigModal();
        } else if (button.id == BTN_ORDER) {
            invertOrder();
        } else if (button.id == BTN_OPEN_FOLDER) {
            File target = qaFolder != null
                ? qaFolder
                : (SharedContentPaths.redirectEnabled() ? SharedContentPaths.screenshotsDir().toFile() : Screenshots189.folder());
            openSystemPath(target);
        } else if (button.id == BTN_DONE) {
            List<Card> marked = deletionList();
            if (fastDelete && !marked.isEmpty()) {
                fastDelete = false;
                requestDeleteCards(marked);
            } else {
                mc.displayGuiScreen(parent);
            }
        } else if (button.id == BTN_FAST_DELETE) {
            fastDelete = !fastDelete;
            if (!fastDelete) resetDeleteSelection();
        } else if (button.id == BTN_REFRESH) {
            startScan();
        }
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        if (renameOpen) {
            if (renameField != null) renameField.mouseClicked(mouseX, mouseY, mouseButton);
            if (mouseButton == 0 && renameDoneBtn != null && renameDoneBtn.mousePressed(mc, mouseX, mouseY)) {
                renameDoneBtn.playPressSound(mc.getSoundHandler());
                applyRename();
                return;
            }
            if (mouseButton == 0 && renameBackBtn != null && renameBackBtn.mousePressed(mc, mouseX, mouseY)) {
                renameBackBtn.playPressSound(mc.getSoundHandler());
                renameOpen = false;
                renameTarget = null;
                return;
            }
            return;
        }

        if (configOpen) {
            if (mouseButton == 0) {
                for (GuiButton b : configButtons) {
                    if (b.mousePressed(mc, mouseX, mouseY)) {
                        b.playPressSound(mc.getSoundHandler());
                        handleConfigClick(b.id);
                        return;
                    }
                }
            }
            return;
        }

        if (propsOpen) {
            if (mouseX >= propsX && mouseX <= propsX + propsW && mouseY >= propsY && mouseY <= propsY + propsH) {
                if (mouseButton == 0 && propsTarget != null) {
                    boolean ro = isReadOnly(propsTarget.file);
                    for (int i = 0; i < PROP_LABELS.length; i++) {
                        int bx = propsX + 2;
                        int by = propsY + 13 + 19 * i;
                        int bw = propsW - 4;
                        if (mouseX >= bx && mouseX < bx + bw && mouseY >= by && mouseY < by + 19) {
                            boolean active = !(ro && (i == 2 || i == 3));
                            if (!active) return;
                            Card target = propsTarget;
                            propsOpen = false;
                            handlePropertyAction(i, target);
                            return;
                        }
                    }
                }
                return;
            }
            propsOpen = false;
            return;
        }

        if (enlargedCard != null) {
            if (mouseButton == 1) {
                openPropertiesMenu(mouseX, mouseY, enlargedCard);
                return;
            }
            if (mouseButton == 0) {
                for (GuiButton b : enlargedButtons) {
                    if (b.mousePressed(mc, mouseX, mouseY)) {
                        b.playPressSound(mc.getSoundHandler());
                        handleEnlargedButton(b.id);
                        return;
                    }
                }
                if (zoom > 1.0f) {
                    panning = true;
                    panLastX = mouseX;
                    panLastY = mouseY;
                }
            }
            return;
        }

        // Scrollbar drag check
        if (mouseButton == 0 && canScroll()) {
            int barX = listX + listW - SCROLLBAR_SPACING - SCROLLBAR_WIDTH;
            int thumbY = scrollbarThumbY();
            int thumbH = scrollbarThumbHeight();
            if (mouseX >= barX && mouseX < barX + SCROLLBAR_WIDTH && mouseY >= thumbY && mouseY < thumbY + thumbH) {
                scrollbarClicked = true;
                scrollbarDragLastY = mouseY;
                return;
            }
        }

        // Card click check inside viewport
        int viewportY = listY + LIST_SPACING;
        int viewportBottom = listY + listH - LIST_SPACING;
        if (mouseY >= viewportY && mouseY < viewportBottom && mouseX >= listX && mouseX < listX + listW) {
            for (int i = 0; i < cards.size(); i++) {
                Card card = cards.get(i);
                if (mouseX >= card.x && mouseX < card.x + card.width && mouseY >= card.y && mouseY < card.y + card.height) {
                    selected = i;
                    if (doneBtn != null) doneBtn.playPressSound(mc.getSoundHandler());
                    if (mouseButton == 0) {
                        if (fastDelete) {
                            if (!isReadOnly(card.file)) {
                                card.selectedForDeletion = !card.selectedForDeletion;
                            }
                        } else {
                            setEnlarged(card);
                        }
                    } else if (mouseButton == 1) {
                        openPropertiesMenu(mouseX, mouseY, card);
                    }
                    return;
                }
            }
        }

        super.mouseClicked(mouseX, mouseY, mouseButton);
    }

    @Override
    protected void mouseClickMove(int mouseX, int mouseY, int clickedMouseButton, long timeSinceLastClick) {
        if (clickedMouseButton == 0 && panning && enlargedCard != null && zoom > 1.0f) {
            panX += (mouseX - panLastX);
            panY += (mouseY - panLastY);
            panLastX = mouseX;
            panLastY = mouseY;
            return;
        }
        if (clickedMouseButton == 0 && scrollbarClicked && canScroll()) {
            int dy = mouseY - scrollbarDragLastY;
            scrollbarDragLastY = mouseY;
            int spacedTrackH = viewHeight() + 4;
            int scrollDelta = MathHelper.ceiling_double_int((double) dy * totalChildrenHeight() / Math.max(1, spacedTrackH));
            scrollY = clampScroll(scrollY + scrollDelta);
            return;
        }
        super.mouseClickMove(mouseX, mouseY, clickedMouseButton, timeSinceLastClick);
    }

    @Override
    protected void mouseReleased(int mouseX, int mouseY, int state) {
        scrollbarClicked = false;
        panning = false;
        super.mouseReleased(mouseX, mouseY, state);
    }

    @Override
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        int wheel = Mouse.getEventDWheel();
        if (wheel == 0 || renameOpen || configOpen || propsOpen) return;
        double vertical = wheel > 0 ? 1.0 : -1.0;

        if (enlargedCard != null) {
            if (isCtrlKeyDown()) {
                zoom = MathHelper.clamp_float(zoom * (float) Math.pow(1.15, vertical), 1.0f, 8.0f);
                if (zoom <= 1.001f) {
                    zoom = 1.0f;
                    panX = 0.0f;
                    panY = 0.0f;
                }
            } else if (vertical > 0) {
                stepEnlarged(1);
            } else {
                stepEnlarged(-1);
            }
            return;
        }

        if (isCtrlKeyDown()) {
            if (vertical > 0 && screenshotsPerRow < 8) {
                screenshotsPerRow++;
                layoutCards(false);
            } else if (vertical < 0 && screenshotsPerRow > 2) {
                screenshotsPerRow--;
                layoutCards(false);
            }
            return;
        }

        if (canScroll()) {
            int speed = Math.max(4, Math.abs((int) (cfgScrollSpeed * (6.0f / screenshotsPerRow) * vertical * 3.0f)));
            scrollY = clampScroll(scrollY + (vertical > 0 ? -speed : speed));
        }
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (renameOpen) {
            if (keyCode == Keyboard.KEY_ESCAPE) {
                renameOpen = false;
                renameTarget = null;
                return;
            }
            if (keyCode == Keyboard.KEY_RETURN && renameDoneBtn != null && renameDoneBtn.enabled) {
                applyRename();
                return;
            }
            if (renameField != null) {
                if (typedChar < 32 || "\\/:*?\"<>|".indexOf(typedChar) < 0) {
                    renameField.textboxKeyTyped(typedChar, keyCode);
                    updateRenameState();
                }
            }
            return;
        }

        if (configOpen) {
            if (keyCode == Keyboard.KEY_ESCAPE) {
                configOpen = false;
            }
            return;
        }

        if (propsOpen) {
            if (keyCode == Keyboard.KEY_ESCAPE) {
                propsOpen = false;
            }
            return;
        }

        if (enlargedCard != null) {
            if (keyCode == Keyboard.KEY_ESCAPE) {
                setEnlarged(null);
            } else if (keyCode == Keyboard.KEY_LEFT) {
                stepEnlarged(-1);
            } else if (keyCode == Keyboard.KEY_RIGHT) {
                stepEnlarged(1);
            } else if (keyCode == Keyboard.KEY_C && isCtrlKeyDown()) {
                copyToClipboard(enlargedCard.file);
            }
            return;
        }

        if (keyCode == Keyboard.KEY_ESCAPE) {
            mc.displayGuiScreen(parent);
        } else if (keyCode == Keyboard.KEY_F5) {
            startScan();
        } else if (keyCode == Keyboard.KEY_C && isCtrlKeyDown()) {
            for (Card c : cards) {
                if (c.hovered) {
                    copyToClipboard(c.file);
                    return;
                }
            }
            if (selectedFile() != null) copyToClipboard(selectedFile());
        } else if (keyCode == Keyboard.KEY_DELETE && selected >= 0 && selected < cards.size()) {
            requestDeleteSingle(cards.get(selected));
        }
    }

    private void stepEnlarged(int delta) {
        if (enlargedCard == null) return;
        int idx = cards.indexOf(enlargedCard) + delta;
        if (idx >= 0 && idx < cards.size()) {
            setEnlarged(cards.get(idx));
        }
    }

    private void handleEnlargedButton(int id) {
        if (enlargedCard == null) return;
        if (id == BTN_ENLARGED_DONE) setEnlarged(null);
        else if (id == BTN_ENLARGED_PREV) stepEnlarged(-1);
        else if (id == BTN_ENLARGED_NEXT) stepEnlarged(1);
        else if (id == BTN_ENLARGED_OPEN) openSystemPath(enlargedCard.file);
        else if (id == BTN_ENLARGED_COPY) copyToClipboard(enlargedCard.file);
        else if (id == BTN_ENLARGED_DELETE) requestDeleteSingle(enlargedCard);
        else if (id == BTN_ENLARGED_RENAME) openRenameModal(enlargedCard);
    }

    private void handlePropertyAction(int idx, Card target) {
        if (idx == 0) openSystemPath(target.file);
        else if (idx == 1) copyToClipboard(target.file);
        else if (idx == 2) requestDeleteSingle(target);
        else if (idx == 3) openRenameModal(target);
    }

    private void openRenameModal(Card target) {
        if (target == null || isReadOnly(target.file)) return;
        renameTarget = target;
        String name = target.file.getName();
        int dot = name.lastIndexOf('.');
        renameOriginalStem = dot > 0 ? name.substring(0, dot) : name;
        renameOpen = true;
        initRenameModal(renameOriginalStem);
    }

    private void handleConfigClick(int id) {
        if (id == 400) {
            cfgNewestFirst = !cfgNewestFirst;
            if (newestFirst != cfgNewestFirst) invertOrder();
            initConfigModal();
        } else if (id == 401) {
            cfgPerRow = cfgPerRow >= 8 ? 2 : cfgPerRow + 1;
            screenshotsPerRow = cfgPerRow;
            layoutCards(false);
            initConfigModal();
        } else if (id == 402) {
            cfgPromptDelete = !cfgPromptDelete;
            initConfigModal();
        } else if (id == 403) {
            cfgEnlargeAnim = !cfgEnlargeAnim;
            initConfigModal();
        } else if (id == 404) {
            cfgHideNames = !cfgHideNames;
            layoutCards(false);
            initConfigModal();
        } else if (id == 405) {
            configOpen = false;
        }
    }

    private String buttonTooltipAt(int mx, int my) {
        if (addFolderBtn != null && hit(addFolderBtn, mx, my)) {
            return "Scan a screenshot folder, instance, or launcher folder. Originals stay in place.";
        }
        if (configBtn != null && hit(configBtn, mx, my)) return "Configure...";
        if (orderBtn != null && hit(orderBtn, mx, my)) {
            return newestFirst ? "Sorting by Descending Order" : "Sorting by Ascending Order";
        }
        if (openFolderBtn != null && hit(openFolderBtn, mx, my)) return "Open Screenshot Folder";
        if (fastDeleteBtn != null && hit(fastDeleteBtn, mx, my)) return "Toggle Fast Delete";
        if (refreshBtn != null && hit(refreshBtn, mx, my)) return "Refresh Screenshots (press F5)";
        return null;
    }

    private String enlargedTooltipAt(int mx, int my) {
        for (GuiButton b : enlargedButtons) {
            if (b instanceof IconGuiButton && hit(b, mx, my)) {
                return ((IconGuiButton) b).tooltip;
            }
        }
        return null;
    }

    private static boolean hit(GuiButton b, int mx, int my) {
        return b.visible && mx >= b.xPosition && mx < b.xPosition + b.width && my >= b.yPosition && my < b.yPosition + b.height;
    }

    private Card findCard(File file) {
        for (Card c : cards) {
            if (c.file.equals(file)) return c;
        }
        return null;
    }

    private void enableScissor(int x, int y, int w, int h) {
        ScaledResolution sr = new ScaledResolution(mc);
        int factor = sr.getScaleFactor();
        int sx = x * factor;
        int sy = mc.displayHeight - (y + h) * factor;
        int sw = Math.max(0, w * factor);
        int sh = Math.max(0, h * factor);
        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        GL11.glScissor(sx, sy, sw, sh);
    }

    private static void disableScissor() {
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
    }

    @Override
    public void onGuiClosed() {
        if (!confirming) {
            closed = true;
            cancelScan();
            closeAllCards();
            releaseFullTexture();
        }
        confirming = false;
    }

    private static Minecraft mc() {
        return Minecraft.getMinecraft();
    }
}
