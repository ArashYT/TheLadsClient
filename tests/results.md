# E2E Test Suite Run Report
- **Timestamp**: 2026-09-04 15:51:16 UTC
- **Tiers Tested**: [1, 2, 3, 4]
- **Total Tests**: 166
- **Passed**: 147
- **Failed**: 19
- **Errors**: 0
- **Elapsed**: 5.73s

## Summary by Status
| Status | Count |
|---|---|
| Passed | 147 |
| Failed | 19 |
| Errors | 0 |
| Skipped | 0 |

## Test Results Catalog
| Test ID | Status | Duration | Description |
|---|---|---|---|
| `test_tier1_feature_coverage.TestTier1FeatureCoverage.test_f01_01_repo_clone_pack_size_under_50mb` | **PASSED** | 0.0005s | F1: Cloned repository pack size must be strictly under 50 MB. |
| `test_tier1_feature_coverage.TestTier1FeatureCoverage.test_f01_02_no_monster_blobs_in_git_history` | **PASSED** | 0.0644s | F1: No individual blobs over 50 MB exist in reachable git history. |
| `test_tier1_feature_coverage.TestTier1FeatureCoverage.test_f01_03_temp_auth_zip_blob_absent_from_git` | **PASSED** | 0.0291s | F1: Monster blob temp_auth.zip (1.868 GB) is absent from reachable git history. |
| `test_tier1_feature_coverage.TestTier1FeatureCoverage.test_f01_04_no_large_installer_binaries_in_git` | **PASSED** | 0.0238s | F1: Heavy installer executables (.exe) are absent from reachable git tree. |
| `test_tier1_feature_coverage.TestTier1FeatureCoverage.test_f01_05_working_tree_tracked_size_under_50mb` | **PASSED** | 0.0825s | F1: Total size of active tracked files in Git HEAD is under 50 MB. |
| `test_tier1_feature_coverage.TestTier1FeatureCoverage.test_f02_01_pack_toml_exists_and_valid` | **PASSED** | 0.0006s | F2: Packwiz/pack.toml exists and contains valid required root metadata. |
| `test_tier1_feature_coverage.TestTier1FeatureCoverage.test_f02_02_index_toml_exists_and_valid` | **PASSED** | 0.0s | F2: Packwiz/index.toml exists and contains valid index metadata. |
| `test_tier1_feature_coverage.TestTier1FeatureCoverage.test_f02_03_zero_tracked_jars_in_packwiz` | **PASSED** | 0.0236s | F2: Zero physical .jar files are tracked in Packwiz/ by git. |
| `test_tier1_feature_coverage.TestTier1FeatureCoverage.test_f02_04_mods_are_pw_toml_metadata` | **PASSED** | 0.0012s | F2: Mod descriptors in Packwiz/mods are .pw.toml files with valid metadata. |
| `test_tier1_feature_coverage.TestTier1FeatureCoverage.test_f02_05_resourcepacks_zero_tracked_zips` | **PASSED** | 0.0234s | F2: Zero physical .zip files in Packwiz/resourcepacks are tracked in git. |
| `test_tier1_feature_coverage.TestTier1FeatureCoverage.test_f03_01_dead_testlogin_project_absent` | **PASSED** | 0.0s | F3: Obsolete project TestLogin is completely absent from workspace root. |
| `test_tier1_feature_coverage.TestTier1FeatureCoverage.test_f03_02_dead_launcher_clean_project_absent` | **PASSED** | 0.0s | F3: Duplicate project TheLadsLauncher_Clean is completely absent from workspace root. |
| `test_tier1_feature_coverage.TestTier1FeatureCoverage.test_f03_03_dead_staging_projects_absent` | **PASSED** | 0.0s | F3: Dead test projects TestCmlLib, MsalTest, and Playground are absent. |
| `test_tier1_feature_coverage.TestTier1FeatureCoverage.test_f03_04_legacy_launcher_agents_absent` | **PASSED** | 0.0s | F3: Legacy .agents directory inside TheLadsLauncher is absent (only root .agents allowed). |
| `test_tier1_feature_coverage.TestTier1FeatureCoverage.test_f03_05_outer_dead_staging_directories_absent` | **PASSED** | 0.0005s | F3: Outer staging and decompiled directories (temp_mc, TempJar, decompiled) are absent. |
| `test_tier1_feature_coverage.TestTier1FeatureCoverage.test_f04_01_gitignore_file_exists_and_non_empty` | **PASSED** | 0.0s | F4: Repository root .gitignore exists and contains active rule definitions. |
| `test_tier1_feature_coverage.TestTier1FeatureCoverage.test_f04_02_gitignore_blocks_binary_executables` | **PASSED** | 0.0717s | F4: .gitignore blocks binary executables and installers (*.exe, *.dll). |
| `test_tier1_feature_coverage.TestTier1FeatureCoverage.test_f04_03_gitignore_blocks_java_build_artifacts` | **PASSED** | 0.0714s | F4: .gitignore blocks Java build artifacts (*.jar, .gradle/, build/). |
| `test_tier1_feature_coverage.TestTier1FeatureCoverage.test_f04_04_gitignore_blocks_dotnet_build_artifacts` | **PASSED** | 0.0532s | F4: .gitignore blocks .NET build directories (bin/, obj/). |
| `test_tier1_feature_coverage.TestTier1FeatureCoverage.test_f04_05_gitignore_blocks_crash_dumps_and_logs` | **PASSED** | 0.076s | F4: .gitignore blocks logs, crash dumps, and scratch disassemblies. |
| `test_tier1_feature_coverage.TestTier1FeatureCoverage.test_f05_01_launcher_csproj_targets_net8` | **PASSED** | 0.0s | F5: TheLadsLauncher.csproj specifies net8.0-windows or net8.0 target framework. |
| `test_tier1_feature_coverage.TestTier1FeatureCoverage.test_f05_02_launcher_suppresses_msb3277_or_resolves_conflict` | **PASSED** | 0.0s | F5: TheLadsLauncher.csproj resolves MSB3277 WindowsBase version conflict. |
| `test_tier1_feature_coverage.TestTier1FeatureCoverage.test_f05_03_avalonia_obsolete_systemdecorations_eliminated` | **PASSED** | 0.0048s | F5: Obsolete Avalonia property SystemDecorations is replaced with WindowDecorations. |
| `test_tier1_feature_coverage.TestTier1FeatureCoverage.test_f05_04_avalonia_obsolete_watermark_eliminated` | **PASSED** | 0.0044s | F5: Obsolete TextBox.Watermark is replaced with PlaceholderText across all views. |
| `test_tier1_feature_coverage.TestTier1FeatureCoverage.test_f05_05_launcher_builds_with_zero_errors_and_zero_warnings` | **FAILED** | 2.3619s | F5: dotnet build TheLadsLauncher.csproj completes with 0 errors and 0 warnings. |
| `test_tier1_feature_coverage.TestTier1FeatureCoverage.test_f06_01_ipathservice_interface_defined` | **PASSED** | 0.0005s | F6: IPathService interface exists with BaseDirectory, SharedDirectory, ProfilesDirectory, RuntimesDirectory. |
| `test_tier1_feature_coverage.TestTier1FeatureCoverage.test_f06_02_iprofileservice_interface_defined` | **PASSED** | 0.0s | F6: IProfileService interface exists defining profile management operations. |
| `test_tier1_feature_coverage.TestTier1FeatureCoverage.test_f06_03_ijavaservice_interface_defined` | **PASSED** | 0.0005s | F6: IJavaService interface exists defining Java detection and automated download. |
| `test_tier1_feature_coverage.TestTier1FeatureCoverage.test_f06_04_iauthservice_interface_defined` | **PASSED** | 0.0s | F6: IAuthService interface exists defining authentication operations. |
| `test_tier1_feature_coverage.TestTier1FeatureCoverage.test_f06_05_viewmodels_defined_and_decoupled` | **PASSED** | 0.0005s | F6: ViewModels directory exists with ViewModelBase and primary ViewModels. |
| `test_tier1_feature_coverage.TestTier1FeatureCoverage.test_f07_01_profile_1_21_1_specification` | **PASSED** | 0.0006s | F7: Predefined profile for 1.21.1 exists specifying Minecraft 1.21.1 and Java 21. |
| `test_tier1_feature_coverage.TestTier1FeatureCoverage.test_f07_02_profile_26_2_specification` | **PASSED** | 0.0005s | F7: Predefined profile for 26.2 exists specifying Minecraft 26.2 and Java 25. |
| `test_tier1_feature_coverage.TestTier1FeatureCoverage.test_f07_03_profile_latest_release_specification` | **PASSED** | 0.0005s | F7: Predefined profile for latest release exists. |
| `test_tier1_feature_coverage.TestTier1FeatureCoverage.test_f07_04_profile_model_supports_isisolated_flag` | **PASSED** | 0.0s | F7: Profile model supports IsIsolated boolean flag defaulting to false. |
| `test_tier1_feature_coverage.TestTier1FeatureCoverage.test_f07_05_profile_selection_contract` | **PASSED** | 0.0s | F7: Active profile selection contract updates profile state and game directory. |
| `test_tier1_feature_coverage.TestTier1FeatureCoverage.test_f08_01_shared_directory_structure_contract` | **PASSED** | 0.0005s | F8: Shared directory contract points to %APPDATA%/.theladsclient/shared/. |
| `test_tier1_feature_coverage.TestTier1FeatureCoverage.test_f08_02_shared_options_txt_keybinds_synchronization` | **PASSED** | 0.0s | F8: Shared synchronization handles options.txt for vanilla options and keybindings. |
| `test_tier1_feature_coverage.TestTier1FeatureCoverage.test_f08_03_shared_servers_dat_synchronization` | **PASSED** | 0.0005s | F8: Shared synchronization handles servers.dat for multiplayer servers. |
| `test_tier1_feature_coverage.TestTier1FeatureCoverage.test_f08_04_shared_accounts_and_profile_synchronization` | **PASSED** | 0.0005s | F8: Shared synchronization handles lads_accounts.json and lads_profile.json. |
| `test_tier1_feature_coverage.TestTier1FeatureCoverage.test_f08_05_isolated_profile_skips_shared_synchronization` | **PASSED** | 0.0005s | F8: Isolated profiles (IsIsolated == true) skip shared directory synchronization. |
| `test_tier1_feature_coverage.TestTier1FeatureCoverage.test_f09_01_zero_hardcoded_user_arash_in_launcher_cs` | **FAILED** | 0.0042s | F9: Zero occurrences of hardcoded 'C:\Users\Arash' in TheLadsLauncher C# files. |
| `test_tier1_feature_coverage.TestTier1FeatureCoverage.test_f09_02_zero_hardcoded_user_arash_in_launcher_axaml` | **PASSED** | 0.0043s | F9: Zero occurrences of hardcoded 'C:\Users\Arash' in TheLadsLauncher AXAML files. |
| `test_tier1_feature_coverage.TestTier1FeatureCoverage.test_f09_03_zero_hardcoded_static_lads_client_root` | **FAILED** | 0.0079s | F9: Zero occurrences of static root 'C:\The Lads Client' in TheLadsLauncher codebase. |
| `test_tier1_feature_coverage.TestTier1FeatureCoverage.test_f09_04_dynamic_base_directory_resolution` | **PASSED** | 0.0s | F9: PathService defaults dynamically to %APPDATA%/.theladsclient. |
| `test_tier1_feature_coverage.TestTier1FeatureCoverage.test_f09_05_custom_base_directory_override_supported` | **PASSED** | 0.0s | F9: PathService supports custom base path override via constructor or config. |
| `test_tier1_feature_coverage.TestTier1FeatureCoverage.test_f10_01_java_21_requirement_for_mc_1_21_1` | **PASSED** | 0.0005s | F10: JavaService specifies Java 21 for Minecraft 1.21.1. |
| `test_tier1_feature_coverage.TestTier1FeatureCoverage.test_f10_02_java_25_requirement_for_mc_26_2` | **PASSED** | 0.0s | F10: JavaService specifies Java 25 for Minecraft 26.2. |
| `test_tier1_feature_coverage.TestTier1FeatureCoverage.test_f10_03_adoptium_api_endpoint_structure_java_21` | **PASSED** | 0.0s | F10: Adoptium REST API endpoint for Java 21 query follows official schema. |
| `test_tier1_feature_coverage.TestTier1FeatureCoverage.test_f10_04_adoptium_api_endpoint_structure_java_25` | **PASSED** | 0.0005s | F10: Adoptium REST API query parameters specify OS windows and architecture x64. |
| `test_tier1_feature_coverage.TestTier1FeatureCoverage.test_f10_05_managed_runtime_directory_structure` | **PASSED** | 0.0s | F10: Managed runtime paths follow %APPDATA%/.theladsclient/runtime/java-{version}/bin/java.exe. |
| `test_tier1_feature_coverage.TestTier1FeatureCoverage.test_f11_01_settings_gradle_defines_three_subprojects` | **FAILED** | 0.0005s | F11: settings.gradle includes subprojects common, v1_21_1, and v26_2. |
| `test_tier1_feature_coverage.TestTier1FeatureCoverage.test_f11_02_common_subproject_java_21_toolchain` | **PASSED** | 0.0s | F11: Subproject :common configures Java 21 toolchain (release = 21). |
| `test_tier1_feature_coverage.TestTier1FeatureCoverage.test_f11_03_v1_21_1_subproject_mc_1_21_1_fabric_loom` | **FAILED** | 0.0s | F11: Subproject :v1_21_1 configures Minecraft 1.21.1 and includes :common. |
| `test_tier1_feature_coverage.TestTier1FeatureCoverage.test_f11_04_v26_2_subproject_mc_26_2_fabric_loom` | **FAILED** | 0.0s | F11: Subproject :v26_2 configures Minecraft 26.2 with Java 25 and includes :common. |
| `test_tier1_feature_coverage.TestTier1FeatureCoverage.test_f11_05_root_build_gradle_coordinates_subprojects` | **FAILED** | 0.0005s | F11: Root build.gradle coordinates multi-project repository and build tasks. |
| `test_tier1_feature_coverage.TestTier1FeatureCoverage.test_f12_01_ladsgraphics_bridge_interface_defined` | **PASSED** | 0.0005s | F12: LadsGraphics bridge interface in :common defines universal drawing primitives. |
| `test_tier1_feature_coverage.TestTier1FeatureCoverage.test_f12_02_v1_21_1_guigraphics_adapter_implements_bridge` | **FAILED** | 0.0005s | F12: GuiGraphicsLadsAdapter in :v1_21_1 wraps GuiGraphics to implement LadsGraphics. |
| `test_tier1_feature_coverage.TestTier1FeatureCoverage.test_f12_03_v26_2_guigraphicsextractor_adapter_implements_bridge` | **FAILED** | 0.0s | F12: GuiGraphicsExtractorLadsAdapter in :v26_2 wraps GuiGraphicsExtractor to implement LadsGraphics. |
| `test_tier1_feature_coverage.TestTier1FeatureCoverage.test_f12_04_hud_elements_render_through_ladsgraphics` | **FAILED** | 0.0011s | F12: Universal HUD elements in :common render through LadsGraphics interface. |
| `test_tier1_feature_coverage.TestTier1FeatureCoverage.test_f12_05_settings_screens_render_through_ladsgraphics` | **FAILED** | 0.0011s | F12: Settings and layout screens in :common render through LadsGraphics bridge. |
| `test_tier1_feature_coverage.TestTier1FeatureCoverage.test_f13_01_title_screen_lads_settings_button_injected` | **PASSED** | 0.0339s | F13: 'Lads Settings' button is registered in TitleScreenMixin. |
| `test_tier1_feature_coverage.TestTier1FeatureCoverage.test_f13_02_lads_settings_positioned_above_options` | **PASSED** | 0.0338s | F13: 'Lads Settings' button is positioned directly at/above Options button Y. |
| `test_tier1_feature_coverage.TestTier1FeatureCoverage.test_f13_03_subsequent_buttons_shifted_by_24px` | **PASSED** | 0.034s | F13: Buttons below Options are shifted downward by 24 pixels. |
| `test_tier1_feature_coverage.TestTier1FeatureCoverage.test_f13_04_bottom_left_account_card_rendered` | **PASSED** | 0.035s | F13: TitleScreenMixin draws bottom-left account switcher card. |
| `test_tier1_feature_coverage.TestTier1FeatureCoverage.test_f13_05_layout_parity_across_1_21_1_and_26_2` | **FAILED** | 0.0s | F13: Title screen button injection logic exists in both v1_21_1 and v26_2 subprojects. |
| `test_tier1_feature_coverage.TestTier1FeatureCoverage.test_f14_01_account_switcher_reads_dynamic_accounts_json` | **FAILED** | 0.0338s | F14: AccountSwitcherScreen reads accounts from dynamic ClientPaths. |
| `test_tier1_feature_coverage.TestTier1FeatureCoverage.test_f14_02_account_json_schema_validation` | **PASSED** | 0.0s | F14: Account JSON schema validator verifies required account fields. |
| `test_tier1_feature_coverage.TestTier1FeatureCoverage.test_f14_03_skin_cache_priority_local_skin_png` | **PASSED** | 0.0692s | F14: Skin fetching prioritizes local custom skin.png when present. |
| `test_tier1_feature_coverage.TestTier1FeatureCoverage.test_f14_04_skin_fetch_mojang_and_minotar_fallback` | **PASSED** | 0.0692s | F14: Skin fetching connects to Mojang API with Minotar fallback. |
| `test_tier1_feature_coverage.TestTier1FeatureCoverage.test_f14_05_skin_caching_to_disk_contract` | **PASSED** | 0.0687s | F14: Downloaded skins are cached to disk in .theladsclient/cache/skins/. |
| `test_tier1_feature_coverage.TestTier1FeatureCoverage.test_f15_01_mixin_configs_specify_default_require_zero` | **FAILED** | 0.0352s | F15: Version-specific mixin configs declare injectors.defaultRequire = 0. |
| `test_tier1_feature_coverage.TestTier1FeatureCoverage.test_f15_02_v1_21_1_mixin_targets_gui_class` | **FAILED** | 0.0s | F15: GuiMixin in v1_21_1 targets net.minecraft.client.gui.Gui. |
| `test_tier1_feature_coverage.TestTier1FeatureCoverage.test_f15_03_v26_2_mixin_targets_hud_class` | **FAILED** | 0.0s | F15: GuiMixin in v26_2 targets net.minecraft.client.gui.Hud. |
| `test_tier1_feature_coverage.TestTier1FeatureCoverage.test_f15_04_boot_log_validator_single_resource_reload` | **PASSED** | 0.0s | F15: Boot log validator accepts logs with exactly ONE Reloading ResourceManager entry. |
| `test_tier1_feature_coverage.TestTier1FeatureCoverage.test_f15_05_boot_log_validator_zero_mixin_injection_errors` | **PASSED** | 0.0s | F15: Boot log validator rejects logs containing mixin injection errors. |
| `test_tier2_boundary_corner.TestTier2BoundaryCorner.test_f01_b01_repo_pack_size_boundary_margin` | **PASSED** | 0.0s | F1: Packfile size has at least 15 MB safety margin under the 50 MB limit (size <= 35 MB). |
| `test_tier2_boundary_corner.TestTier2BoundaryCorner.test_f01_b02_loose_object_count_boundary` | **PASSED** | 0.0282s | F1: Loose git objects count must be < 100 to ensure git garbage collection was run. |
| `test_tier2_boundary_corner.TestTier2BoundaryCorner.test_f01_b03_git_object_pack_count_boundary` | **PASSED** | 0.0s | F1: Pack file count is <= 4, verifying clean repacked object store. |
| `test_tier2_boundary_corner.TestTier2BoundaryCorner.test_f01_b04_git_commit_history_depth_integrity` | **PASSED** | 0.0284s | F1: Git commit history remains intact after purge (commits >= 10, HEAD valid). |
| `test_tier2_boundary_corner.TestTier2BoundaryCorner.test_f01_b05_large_blobs_boundary_check` | **PASSED** | 0.0849s | F1: No single tracked file in active working tree exceeds 25 MB. |
| `test_tier2_boundary_corner.TestTier2BoundaryCorner.test_f02_b01_malformed_pw_toml_syntax_detection` | **PASSED** | 0.0s | F2: Validator detects corrupted TOML syntax with unclosed quotes or brackets. |
| `test_tier2_boundary_corner.TestTier2BoundaryCorner.test_f02_b02_empty_or_whitespace_pw_toml_detection` | **PASSED** | 0.0s | F2: Validator rejects empty or whitespace-only .pw.toml file. |
| `test_tier2_boundary_corner.TestTier2BoundaryCorner.test_f02_b03_missing_required_fields_pw_toml` | **PASSED** | 0.0s | F2: Validator rejects .pw.toml missing filename field. |
| `test_tier2_boundary_corner.TestTier2BoundaryCorner.test_f02_b04_invalid_side_specification_pw_toml` | **PASSED** | 0.0s | F2: Validator rejects .pw.toml with invalid side (e.g. side = 'unknown'). |
| `test_tier2_boundary_corner.TestTier2BoundaryCorner.test_f02_b05_index_toml_files_not_empty_and_valid` | **PASSED** | 0.0s | F2: Index.toml contains non-empty file entries and sha256 hash format. |
| `test_tier2_boundary_corner.TestTier2BoundaryCorner.test_f03_b01_root_workspace_zero_exe_installers` | **PASSED** | 0.0s | F3: Zero loose .exe installer executables exist in repository root. |
| `test_tier2_boundary_corner.TestTier2BoundaryCorner.test_f03_b02_root_workspace_zero_crash_debug_txt` | **PASSED** | 0.0011s | F3: Zero crash dumps or debug text files exist in root workspace. |
| `test_tier2_boundary_corner.TestTier2BoundaryCorner.test_f03_b03_root_workspace_zero_temp_dirs` | **PASSED** | 0.0s | F3: Zero temporary staging directories (temp*, Temp*) exist at root. |
| `test_tier2_boundary_corner.TestTier2BoundaryCorner.test_f03_b04_root_workspace_zero_disassembly_dumps` | **PASSED** | 0.0s | F3: Zero intermediate disassembly dumps (*.il, *.disasm) exist in root. |
| `test_tier2_boundary_corner.TestTier2BoundaryCorner.test_f03_b05_no_unapproved_hidden_directories` | **PASSED** | 0.0s | F3: No obsolete tool caches (e.g. .aider*) exist in workspace root. |
| `test_tier2_boundary_corner.TestTier2BoundaryCorner.test_f04_b01_gitignore_negation_rules_precedence` | **PASSED** | 0.05s | F4: Negation rules preserve .pw.toml files while ignoring .jar files. |
| `test_tier2_boundary_corner.TestTier2BoundaryCorner.test_f04_b02_gitignore_source_files_not_ignored` | **PASSED** | 0.1001s | F4: Essential project source files (.cs, .java, .axaml, .gradle) are NEVER ignored. |
| `test_tier2_boundary_corner.TestTier2BoundaryCorner.test_f04_b03_gitignore_deep_nested_build_libs_ignored` | **PASSED** | 0.0482s | F4: Deeply nested Gradle build output jars are ignored. |
| `test_tier2_boundary_corner.TestTier2BoundaryCorner.test_f04_b04_gitignore_deep_nested_bin_obj_ignored` | **PASSED** | 0.0485s | F4: Deeply nested .NET compilation binaries are ignored. |
| `test_tier2_boundary_corner.TestTier2BoundaryCorner.test_f04_b05_gitignore_various_archive_formats_ignored` | **PASSED** | 0.0497s | F4: Archive files (.zip, .tar, .gz) are blocked by gitignore in build and root areas. |
| `test_tier2_boundary_corner.TestTier2BoundaryCorner.test_f05_b01_csproj_treat_warnings_as_errors_or_clean_build` | **FAILED** | 1.3243s | F5: TheLadsLauncher.csproj compiles with warning count strictly 0. |
| `test_tier2_boundary_corner.TestTier2BoundaryCorner.test_f05_b02_cs4014_unawaited_async_calls_guarded` | **PASSED** | 0.0049s | F5: Async calls inside event handlers or lambdas are properly awaited or discarded. |
| `test_tier2_boundary_corner.TestTier2BoundaryCorner.test_f05_b03_nullable_dereference_guards_cs8602_cs8604` | **PASSED** | 0.0064s | F5: Nullable reference types are guarded with null-conditional or pattern matching. |
| `test_tier2_boundary_corner.TestTier2BoundaryCorner.test_f05_b04_unused_event_declarations_guarded_cs0067` | **PASSED** | 0.0s | F5: Custom ICommand implementations provide accessors or use CanExecuteChanged safely. |
| `test_tier2_boundary_corner.TestTier2BoundaryCorner.test_f05_b05_nowarn_contains_msb3277_suppression` | **PASSED** | 0.0s | F5: TheLadsLauncher.csproj suppresses or resolves MSB3277 conflict. |
| `test_tier2_boundary_corner.TestTier2BoundaryCorner.test_f06_b01_service_interface_nullability_contracts` | **PASSED** | 0.0037s | F6: Service interfaces declare nullable annotations properly. |
| `test_tier2_boundary_corner.TestTier2BoundaryCorner.test_f06_b02_viewmodel_property_change_notification` | **PASSED** | 0.0s | F6: ViewModelBase implements INotifyPropertyChanged. |
| `test_tier2_boundary_corner.TestTier2BoundaryCorner.test_f06_b03_services_directory_isolation` | **PASSED** | 0.0005s | F6: Services do not directly import Avalonia.Controls (clean UI decoupling). |
| `test_tier2_boundary_corner.TestTier2BoundaryCorner.test_f06_b04_path_service_handles_special_characters` | **PASSED** | 0.0s | F6: PathService contract ensures safe resolution with spaces or non-ascii characters. |
| `test_tier2_boundary_corner.TestTier2BoundaryCorner.test_f06_b05_profile_service_handles_empty_profile_list` | **PASSED** | 0.0005s | F6: ProfileService defines fallback or default profiles when profiles collection is empty. |
| `test_tier2_boundary_corner.TestTier2BoundaryCorner.test_f07_b01_profile_id_uniqueness_validation` | **PASSED** | 0.0s | F7: Profile IDs must be unique across all profiles. |
| `test_tier2_boundary_corner.TestTier2BoundaryCorner.test_f07_b02_profile_unsupported_version_handling` | **PASSED** | 0.0006s | F7: Profile specifications map to supported Minecraft loaders. |
| `test_tier2_boundary_corner.TestTier2BoundaryCorner.test_f07_b03_corrupt_profile_json_recovery` | **PASSED** | 0.0s | F7: Corrupted lads_profile.json format validation. |
| `test_tier2_boundary_corner.TestTier2BoundaryCorner.test_f07_b04_profile_custom_game_dir_resolution` | **PASSED** | 0.0s | F7: Profile model supports custom GameDir or defaults to profile-specific path. |
| `test_tier2_boundary_corner.TestTier2BoundaryCorner.test_f07_b05_profile_roundtrip_json_serialization` | **PASSED** | 0.0s | F7: Profile object schema round-trips through JSON cleanly. |
| `test_tier2_boundary_corner.TestTier2BoundaryCorner.test_f08_b01_missing_shared_directory_auto_created` | **PASSED** | 0.0005s | F8: Shared sync protocol ensures directory exists before copying. |
| `test_tier2_boundary_corner.TestTier2BoundaryCorner.test_f08_b02_corrupt_options_txt_handling` | **PASSED** | 0.0s | F8: options.txt sync handles lines with corrupted key-value formatting. |
| `test_tier2_boundary_corner.TestTier2BoundaryCorner.test_f08_b03_timestamp_conflict_resolution` | **PASSED** | 0.0005s | F8: Sync logic compares last modified times before copying back to shared. |
| `test_tier2_boundary_corner.TestTier2BoundaryCorner.test_f08_b04_isolated_mode_does_not_touch_shared_dir` | **PASSED** | 0.0s | F8: When IsIsolated is true, synchronization pass is skipped. |
| `test_tier2_boundary_corner.TestTier2BoundaryCorner.test_f08_b05_shared_sync_handles_file_lock_without_crash` | **PASSED** | 0.0005s | F8: Sync operations are wrapped in try-catch to prevent file-lock crashes. |
| `test_tier2_boundary_corner.TestTier2BoundaryCorner.test_f09_b01_path_service_empty_custom_path_fallback` | **PASSED** | 0.0s | F9: PathService constructor treats null/empty custom path as default %APPDATA%. |
| `test_tier2_boundary_corner.TestTier2BoundaryCorner.test_f09_b02_path_service_traversal_prevention` | **PASSED** | 0.0005s | F9: Profile directory resolution uses Path.Combine preventing relative path escape. |
| `test_tier2_boundary_corner.TestTier2BoundaryCorner.test_f09_b03_zero_hardcoded_drive_roots` | **PASSED** | 0.0053s | F9: Zero hardcoded references to static backup drives (G:\The Lads Client Backups). |
| `test_tier2_boundary_corner.TestTier2BoundaryCorner.test_f09_b04_log_directory_auto_creation` | **PASSED** | 0.0s | F9: PathService defines and creates LogsDirectory. |
| `test_tier2_boundary_corner.TestTier2BoundaryCorner.test_f09_b05_client_paths_java_env_var_override` | **PASSED** | 0.0346s | F9: Java ClientPaths class supports THELADS_DIR environment variable override. |
| `test_tier2_boundary_corner.TestTier2BoundaryCorner.test_f10_b01_adoptium_api_network_failure_handling` | **PASSED** | 0.0s | F10: JavaService handles network exceptions during Adoptium API queries without crash. |
| `test_tier2_boundary_corner.TestTier2BoundaryCorner.test_f10_b02_adoptium_response_malformed_json_handling` | **PASSED** | 0.0s | F10: Adoptium JSON parsing is resilient to unexpected response formats. |
| `test_tier2_boundary_corner.TestTier2BoundaryCorner.test_f10_b03_jdk_corrupt_zip_cleanup` | **PASSED** | 0.0s | F10: Zip extraction cleans up temporary files upon completion or failure. |
| `test_tier2_boundary_corner.TestTier2BoundaryCorner.test_f10_b04_java_major_version_mismatch_rejected` | **PASSED** | 0.0s | F10: Java detection rejects incompatible versions (e.g. Java 17 for MC 26.2). |
| `test_tier2_boundary_corner.TestTier2BoundaryCorner.test_f10_b05_jdk_release_file_fast_inspection` | **PASSED** | 0.0s | F10: Fast inspection parses JDK release file directly. |
| `test_tier2_boundary_corner.TestTier2BoundaryCorner.test_f11_b01_common_subproject_builds_without_minecraft_loom` | **PASSED** | 0.0s | F11: Subproject :common does NOT apply fabric-loom plugin. |
| `test_tier2_boundary_corner.TestTier2BoundaryCorner.test_f11_b02_gradle_wrapper_properties_integrity` | **PASSED** | 0.0s | F11: gradle-wrapper.properties defines a valid Gradle 8+ distribution URL. |
| `test_tier2_boundary_corner.TestTier2BoundaryCorner.test_f11_b03_gradle_build_cache_independence` | **FAILED** | 0.0011s | F11: Multi-project build is not dependent on hardcoded external local user caches. |
| `test_tier2_boundary_corner.TestTier2BoundaryCorner.test_f11_b04_gradle_properties_defines_required_versions` | **PASSED** | 0.0s | F11: gradle.properties specifies version properties. |
| `test_tier2_boundary_corner.TestTier2BoundaryCorner.test_f11_b05_subproject_artifact_classification` | **FAILED** | 0.0s | F11: Subprojects configure distinct archiveClassifier for 1.21.1 and 26.2 artifacts. |
| `test_tier2_boundary_corner.TestTier2BoundaryCorner.test_f12_b01_ladsgraphics_null_text_drawing_safety` | **PASSED** | 0.0011s | F12: LadsGraphics implementation guards against null string drawing. |
| `test_tier2_boundary_corner.TestTier2BoundaryCorner.test_f12_b02_ladsgraphics_scissor_inverted_coordinates` | **PASSED** | 0.0006s | F12: Scissor method declaration accepts bounding box parameters. |
| `test_tier2_boundary_corner.TestTier2BoundaryCorner.test_f12_b03_ladsgraphics_extreme_scale_factors` | **PASSED** | 0.0s | F12: Scale transformation accepts float factors. |
| `test_tier2_boundary_corner.TestTier2BoundaryCorner.test_f12_b04_ladsgraphics_color_alpha_extremes` | **PASSED** | 0.001s | F12: Fill method handles 32-bit ARGB hex color values. |
| `test_tier2_boundary_corner.TestTier2BoundaryCorner.test_f12_b05_ladsgraphics_pose_stack_overflow_prevention` | **PASSED** | 0.0006s | F12: Pose stack push and pop operations are declared as paired methods. |
| `test_tier2_boundary_corner.TestTier2BoundaryCorner.test_f13_b01_title_screen_missing_options_widget_fallback` | **PASSED** | 0.0441s | F13: TitleScreenMixin provides a fallback Y coordinate if Options button is absent. |
| `test_tier2_boundary_corner.TestTier2BoundaryCorner.test_f13_b02_title_screen_small_window_height_clamping` | **PASSED** | 0.04s | F13: TitleScreen account card margin prevents clipping on small heights. |
| `test_tier2_boundary_corner.TestTier2BoundaryCorner.test_f13_b03_title_screen_repeated_resize_idempotence` | **PASSED** | 0.0341s | F13: TitleScreen button injection executes in init() callback. |
| `test_tier2_boundary_corner.TestTier2BoundaryCorner.test_f13_b04_title_screen_button_dimensions_match_vanilla` | **PASSED** | 0.0329s | F13: 'Lads Settings' button uses standard 200px width and 20px height. |
| `test_tier2_boundary_corner.TestTier2BoundaryCorner.test_f13_b05_title_screen_button_click_action_opens_settings` | **PASSED** | 0.0482s | F13: Button click action instantiates LadsSettingsScreen. |
| `test_tier2_boundary_corner.TestTier2BoundaryCorner.test_f14_b01_empty_accounts_file_handled_gracefully` | **PASSED** | 0.0s | F14: Empty accounts array [] is accepted without parsing error. |
| `test_tier2_boundary_corner.TestTier2BoundaryCorner.test_f14_b02_malformed_accounts_file_recovers_cleanly` | **PASSED** | 0.0s | F14: Validator rejects non-array accounts root object. |
| `test_tier2_boundary_corner.TestTier2BoundaryCorner.test_f14_b03_skin_fetch_timeout_and_fallback_to_default` | **PASSED** | 0.0716s | F14: Skin fetch pipeline includes fallback texture identifiers. |
| `test_tier2_boundary_corner.TestTier2BoundaryCorner.test_f14_b04_account_switcher_selection_state_toggle` | **PASSED** | 0.0s | F14: Account switcher verifies single active account selection. |
| `test_tier2_boundary_corner.TestTier2BoundaryCorner.test_f14_b05_skin_png_damaged_image_fallback` | **PASSED** | 0.0709s | F14: Custom skin loading verifies file existence and format before loading. |
| `test_tier2_boundary_corner.TestTier2BoundaryCorner.test_f15_b01_boot_log_multiple_resource_reloads_rejected` | **PASSED** | 0.0s | F15: Boot log with 2+ Reloading ResourceManager entries fails validation. |
| `test_tier2_boundary_corner.TestTier2BoundaryCorner.test_f15_b02_boot_log_zero_resource_reloads_rejected` | **PASSED** | 0.0s | F15: Boot log with 0 Reloading ResourceManager entries fails validation. |
| `test_tier2_boundary_corner.TestTier2BoundaryCorner.test_f15_b03_boot_log_mixin_apply_error_detected` | **PASSED** | 0.0s | F15: Boot log containing MixinApplyError fails validation. |
| `test_tier2_boundary_corner.TestTier2BoundaryCorner.test_f15_b04_boot_log_truncated_file_handling` | **PASSED** | 0.0s | F15: 0-byte or empty boot log fails validation cleanly. |
| `test_tier2_boundary_corner.TestTier2BoundaryCorner.test_f15_b05_boot_log_case_insensitive_timing_match` | **PASSED** | 0.0s | F15: Boot log benchmark matcher handles various startup formats. |
| `test_tier3_cross_feature.TestTier3CrossFeature.test_t3_01_profile_switching_shared_keybinds_persistence` | **PASSED** | 0.0026s | T3.1: Verifies two-way sync protocol when switching between 1.21.1 and 26.2. |
| `test_tier3_cross_feature.TestTier3CrossFeature.test_t3_02_dynamic_path_resolution_launcher_core_parity` | **PASSED** | 0.0363s | T3.2: PathService in TheLadsLauncher and ClientPaths in TheLadsCore |
| `test_tier3_cross_feature.TestTier3CrossFeature.test_t3_03_profile_version_to_java_runtime_mapping` | **PASSED** | 0.0005s | T3.3: Profile version mapping dynamically determines required Java major version: |
| `test_tier3_cross_feature.TestTier3CrossFeature.test_t3_04_profile_isolation_toggle_cross_sync_behavior` | **PASSED** | 0.0s | T3.4: Toggling IsIsolated = True disables sync with shared directory, |
| `test_tier3_cross_feature.TestTier3CrossFeature.test_t3_05_packwiz_metadata_integrity_and_repo_cleanliness` | **PASSED** | 0.0238s | T3.5: Packwiz metadata architecture strictly enforces that all mod descriptors |
| `test_tier3_cross_feature.TestTier3CrossFeature.test_t3_06_account_switcher_data_flow_launcher_to_client` | **PASSED** | 0.0s | T3.6: Accounts created and serialized by Launcher AuthService into lads_accounts.json |
| `test_tier3_cross_feature.TestTier3CrossFeature.test_t3_07_ladsgraphics_bridge_and_hud_configuration_schema` | **PASSED** | 0.0011s | T3.7: HUD elements render through LadsGraphics coordinates, and configuration |
| `test_tier3_cross_feature.TestTier3CrossFeature.test_t3_08_subproject_gradle_and_version_specific_mixins_alignment` | **PASSED** | 0.0s | T3.8: Mixin JSON configurations align with subproject Minecraft targets: |
| `test_tier3_cross_feature.TestTier3CrossFeature.test_t3_09_title_screen_layout_parity_across_gradle_submodules` | **PASSED** | 0.0331s | T3.9: Title screen button injection in both v1_21_1 and v26_2 locates Options |
| `test_tier3_cross_feature.TestTier3CrossFeature.test_t3_10_strict_gitignore_and_multi_project_artifacts_protection` | **PASSED** | 0.0983s | T3.10: Strict gitignore configuration ensures that compiling both Gradle subprojects |
| `test_tier4_real_world.TestTier4RealWorld.test_t4_01_pipeline_gatekeeper_verification_script_flow` | **PASSED** | 0.0257s | T4.1: Simulates the 4-gate verification pipeline (Requirement R4): |
| `test_tier4_real_world.TestTier4RealWorld.test_t4_02_cold_start_onboarding_and_directory_provisioning` | **PASSED** | 0.001s | T4.2: Simulates first-time user run when %APPDATA%/.theladsclient does not exist. |
| `test_tier4_real_world.TestTier4RealWorld.test_t4_03_full_multi_profile_lifecycle_and_keybind_propagation` | **PASSED** | 0.0022s | T4.3: Simulates full game launch, control customization, exit, and switch: |
| `test_tier4_real_world.TestTier4RealWorld.test_t4_04_boot_log_verification_for_v1_21_1_and_v26_2` | **PASSED** | 0.0s | T4.4: Validates real-world Minecraft boot logs for both versions. |
| `test_tier4_real_world.TestTier4RealWorld.test_t4_05_packwiz_remote_modpack_metadata_resolution` | **PASSED** | 0.0021s | T4.5: Validates Packwiz modpack metadata resolution: |
| `test_tier4_real_world.TestTier4RealWorld.test_t4_06_account_and_skin_cache_lifecycle` | **PASSED** | 0.0016s | T4.6: Simulates user account and skin lifecycle: |