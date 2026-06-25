# Analysis Report: PackRepositoryMixin Crash and Resource Pack Override Audit

## 1. Crash Diagnosis (PackRepositoryMixin)

### Observations
- **Mixin File Path**: `TheLadsCore/src/main/java/com/thelads/core/mixin/PackRepositoryMixin.java`
- **Target Class**: `net.minecraft.server.packs.repository.PackRepository`
- **Target Method**: `rebuildSelected`
- **Target Method Bytecode Signature**:
  ```
  private java.util.List<net.minecraft.server.packs.repository.Pack> rebuildSelected(java.util.Collection<java.lang.String>);
    descriptor: (Ljava/util/Collection;)Ljava/util/List;
  ```
- **Verbatim Error from Crash Log** (`TheLadsCore/run/crash-reports/crash-2026-06-24_01.33.43-client.txt`):
  ```
  Caused by: org.spongepowered.asm.mixin.injection.throwables.InvalidInjectionException: Invalid descriptor on theladscore.mixins.json:PackRepositoryMixin from mod theladscore->@Inject::onRebuildSelected(Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfo;)V! CallbackInfoReturnable is required!
  ```

### Why the Crash Occurred
The crash happened because:
1. The target method `rebuildSelected` returns a non-void type: `List<Pack>` (specifically `descriptor: (Ljava/util/Collection;)Ljava/util/List;`).
2. When the game was launched, the compiled `PackRepositoryMixin.class` class contained an injection handler method `onRebuildSelected` that accepted `CallbackInfo` (descriptor: `(Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfo;)V`).
3. Mixin requires that injectors targeting methods with a non-void return type use `CallbackInfoReturnable` instead of `CallbackInfo` to allow inspecting or changing the return value.
4. During class transformation, the Mixin processor failed to validate the injection because of this signature mismatch, throwing `InvalidInjectionException` and aborting game startup.

---

## 2. Logical Bug in Sorting Logic

During our investigation of `net.minecraft.server.packs.repository.PackRepository` bytecode:
1. The target method `rebuildSelected` compiles to:
   ```
   92: aload_2
   93: invokestatic  #177                // Method com/google/common/collect/ImmutableList.copyOf:(Ljava/util/Collection;)Lcom/google/common/collect/ImmutableList;
   96: areturn
   ```
   This means `rebuildSelected` returns an immutable copy of the list (`ImmutableList`).
2. The caller method `setSelected(Collection<String> selectedIds)` compiles to:
   ```
   public void setSelected(java.util.Collection<java.lang.String>);
     Code:
          0: aload_0
          1: aload_0
          2: aload_1
          3: invokevirtual #80                 // Method rebuildSelected:(Ljava/util/Collection;)Ljava/util/List;
          6: putfield      #24                 // Field selected:Ljava/util/List;
          9: return
   ```
   This shows `setSelected` calls `rebuildSelected` and assigns the returned list back to `this.selected`.
3. In `PackRepositoryMixin.java`, the handler does:
   ```java
   @Inject(method = "rebuildSelected", at = @At("RETURN"))
   private void onRebuildSelected(CallbackInfoReturnable<List<Pack>> cir) {
       ...
       // Sorts the lists and assigns to this.selected
       try {
           this.selected.clear();
           ...
       } catch (UnsupportedOperationException e) {
           ...
           this.selected = newList;
       }
   }
   ```
4. **The Bug**: Since we inject at `RETURN` and do not invoke `cir.setReturnValue(...)`, the original, unsorted `ImmutableList` is returned by `rebuildSelected`. As soon as the injector exits, `setSelected` overwrites `this.selected` with that returned unsorted list, completely discarding the sorting changes.
5. **The Fix**: The mixin must invoke `cir.setReturnValue(this.selected)` to ensure the sorted list is returned and assigned in `setSelected`.

---

## 3. Resource Pack Override System Audit

We audited the resource pack override system and identified its current implementation status:

### Implemented Components
1. **Mixin Configuration**: `ResourceOverridesManagerMixin.java` injects into `ResourceOverridesManager.getOverride(String packId)` to disable the hidden pack overrides if the config option `"Disable Hidden Overrides"` is set.
2. **Options Setup**: `ModuleManager.java` registers the option `Disable Hidden Overrides` as a `BoolOption` under the `TexturePacks` module.
3. **UI Settings Tab**: `LadsSettingsScreen.java` contains a `"PACKS"` tab rendering a card for `"Disable Hidden Packs Override"`. Clicking it correctly toggles the option and saves the configuration.
4. **Compatibility Patch**: `PackCompatibilityMixin.java` sets all resource pack compatibilities to `COMPATIBLE` to suppress the "made for a different version" warnings.

### Incomplete Components (Missing UI Exposure)
`TexturePacksModule` defines several other options in `ModuleManager.java` that are fully functional in the HUD rendering (`TexturePackHudElement.java`) but are **not exposed** in the settings screen's `"PACKS"` tab:
- `"Show Hidden"` (BoolOption) — Toggles whether mod built-in/library packs appear in the active list.
- `"Show All"` (BoolOption) — Toggles whether to display all active packs or limit to `Max Packs`.
- `"Max Packs"` (DropdownOption) — Limits the active pack display.
- `"Size"`, `"Background"`, `"Color mode"` — Cosmetic configuration options for the TexturePacks HUD element.

Currently, the `"PACKS"` tab in `LadsSettingsScreen.java` only exposes `"Disable Hidden Overrides"`.

---

## 4. Concrete Fix Strategy (For the Worker)

To fix the crash, resolve the logical bug, and complete the resource pack override settings tab, the following changes must be performed:

### A. Fix `PackRepositoryMixin.java`
1. Update the injection handler method's signature to include target method parameters and correct `CallbackInfoReturnable`:
   ```java
   import java.util.Collection; // Import Collection
   ...
   @Inject(method = "rebuildSelected", at = @At("RETURN"), cancellable = true)
   private void onRebuildSelected(Collection<String> selectedIds, CallbackInfoReturnable<List<Pack>> cir) {
   ```
2. At the end of `onRebuildSelected`, set the return value to the sorted list:
   ```java
   // At the end of the method:
   cir.setReturnValue(this.selected);
   ```

### B. Complete Settings UI in `LadsSettingsScreen.java`
Extend `renderPacksTab` and `mouseClicked` to render cards and handle clicks for the other Texture Pack HUD settings, specifically:
- `"Show Hidden"` (labeled: "Show Hidden Packs in HUD")
- `"Show All"` (labeled: "Show All Active Packs")
- `"Max Packs"` (labeled: "Max Packs to Display")
