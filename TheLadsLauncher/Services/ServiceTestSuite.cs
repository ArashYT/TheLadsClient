using System;
using System.IO;
using System.Linq;
using System.Threading.Tasks;
using TheLadsLauncher.Models;
using TheLadsLauncher.Services;
using TheLadsLauncher.ViewModels;

namespace TheLadsLauncher.Services;

public static class ServiceTestSuite
{
    public static async Task<int> RunAllTestsAsync()
    {
        Console.WriteLine("========================================");
        Console.WriteLine(" RUNNING THE LADS LAUNCHER SERVICE TESTS");
        Console.WriteLine("========================================");

        int passed = 0;
        int failed = 0;

        // Everything runs in a throw-away sandbox: never the user's real launcher data or real .minecraft.
        var sandbox = Path.Combine(Path.GetTempPath(), "LadsServiceTests", Guid.NewGuid().ToString("N"));
        var baseDir = Path.Combine(sandbox, ".theladsclient");
        var globalDir = Path.Combine(sandbox, "global-minecraft");
        // Static helpers (LauncherSettings, the *.Instance singletons) resolve their folders from these variables.
        Environment.SetEnvironmentVariable("THELADS_DIR", baseDir);
        Environment.SetEnvironmentVariable(SharedContentService.RootEnvironmentVariable, globalDir);
        var sandboxPaths = new PathService(baseDir);
        var sandboxShared = new SharedContentService(globalDir, Path.Combine(baseDir, "backups", "servers"));
        var sandboxProfiles = new ProfileService(sandboxPaths, sandboxShared);

        async Task AssertAsync(string testName, Func<Task> testFunc)
        {
            try
            {
                await testFunc();
                Console.WriteLine($"[PASS] {testName}");
                passed++;
            }
            catch (Exception ex)
            {
                Console.WriteLine($"[FAIL] {testName}: {ex.Message}");
                Console.WriteLine(ex.StackTrace);
                failed++;
            }
        }

        // Test 1: PathService dynamic base and subdirectories
        await AssertAsync("PathService Base & Subdirectories", async () =>
        {
            var pathService = sandboxPaths;
            if (!pathService.BaseDirectory.EndsWith(".theladsclient"))
                throw new Exception($"BaseDirectory does not end with .theladsclient: {pathService.BaseDirectory}");

            if (!pathService.ProfilesDirectory.Contains("profiles"))
                throw new Exception("ProfilesDirectory invalid");

            if (!pathService.SharedDirectory.Contains("shared"))
                throw new Exception("SharedDirectory invalid");

            if (!pathService.RuntimesDirectory.Contains("runtime"))
                throw new Exception("RuntimesDirectory invalid");

            pathService.EnsureDirectories();
            if (!Directory.Exists(pathService.BaseDirectory))
                throw new Exception("BaseDirectory not created");
            if (!Directory.Exists(pathService.SharedDirectory))
                throw new Exception("SharedDirectory not created");
            await Task.CompletedTask;
        });

        // Test 2: ProfileService CRUD & Default Profiles
        await AssertAsync("ProfileService Default Profiles & CRUD", async () =>
        {
            var profileService = sandboxProfiles;
            var profiles = profileService.GetProfiles();
            if (profiles.Count == 0)
                throw new Exception("No profiles returned by ProfileService");

            var mc121 = profiles.FirstOrDefault(p => p.MinecraftVersion == "1.21.11");
            if (mc121 == null)
                throw new Exception("1.21.11 default profile not found");
            if (mc121.JavaMajorVersion != 21)
                throw new Exception($"Expected Java 21 for 1.21.11, got {mc121.JavaMajorVersion}");

            var mc262 = profiles.FirstOrDefault(p => p.MinecraftVersion == "26.2");
            if (mc262 == null)
                throw new Exception("26.2 default profile not found");
            if (mc262.JavaMajorVersion != 25)
                throw new Exception($"Expected Java 25 for 26.2, got {mc262.JavaMajorVersion}");

            // Create custom profile
            string customName = $"Test_Profile_{Guid.NewGuid():N}";
            var created = profileService.CreateProfile(customName, "26.3", 25, isIsolated: false);
            if (created == null || created.Name != customName)
                throw new Exception("Failed to create profile");

            profileService.SetActiveProfile(created.Id);
            var active = profileService.GetActiveProfile();
            if (active.Id != created.Id)
                throw new Exception("SetActiveProfile failed");

            // Clean up
            profileService.SetActiveProfile(mc121.Id);
            bool deleted = profileService.DeleteProfile(created.Id);
            if (!deleted)
                throw new Exception("DeleteProfile returned false");
            await Task.CompletedTask;
        });

        // Test 3: Shared Synchronization Protocol
        await AssertAsync("ProfileService Shared Sync (options.txt & shared worlds)", async () =>
        {
            var pathService = sandboxPaths;
            var profileService = sandboxProfiles;

            // Ensure shared directory exists
            Directory.CreateDirectory(pathService.SharedDirectory);
            string sharedOptions = Path.Combine(pathService.SharedDirectory, "options.txt");
            string testContent = $"test_keybind:key.keyboard.space\nmodified_at:{DateTime.UtcNow.Ticks}";
            File.WriteAllText(sharedOptions, testContent);

            var testProfile = profileService.CreateProfile("SyncTestProfile", "26.3", 25, isIsolated: false);
            string profileDir = pathService.GetProfileDirectory(testProfile);
            Directory.CreateDirectory(profileDir);

            // Sync shared -> profile
            await profileService.PrepareProfileEnvironmentAsync(testProfile);
            string profileOptions = Path.Combine(profileDir, "options.txt");
            if (!File.Exists(profileOptions))
                throw new Exception("options.txt was not synchronized to profile directory");

            string syncedContent = File.ReadAllText(profileOptions);
            if (syncedContent != testContent)
                throw new Exception("options.txt content mismatch after shared -> profile sync");
            if (SafeFileOps.GetLinkTarget(Path.Combine(profileDir, "saves")) is not { } savesTarget
                || !SafeFileOps.PathsEqual(savesTarget, sandboxShared.SavesDirectory))
                throw new Exception("saves was not linked to the shared saves folder");

            // Now update profile options (simulate game updating keybinds)
            await Task.Delay(50);
            string updatedContent = testContent + "\nfullscreen:true";
            File.WriteAllText(profileOptions, updatedContent);

            // Sync profile -> shared
            await profileService.SyncProfileToSharedAsync(testProfile);
            string sharedAfterSync = File.ReadAllText(sharedOptions);
            if (sharedAfterSync != updatedContent)
                throw new Exception("options.txt content mismatch after profile -> shared sync");

            // Clean up
            profileService.DeleteProfile(testProfile.Id);
            SafeFileOps.DeleteTree(profileDir);
        });

        // Test 4: Isolated Profile Protocol
        await AssertAsync("ProfileService Isolated Profile Protection", async () =>
        {
            var pathService = sandboxPaths;
            var profileService = sandboxProfiles;

            string sharedOptions = Path.Combine(pathService.SharedDirectory, "options.txt");
            string originalShared = File.Exists(sharedOptions) ? File.ReadAllText(sharedOptions) : "original_shared";
            File.WriteAllText(sharedOptions, originalShared);

            var isolatedProfile = profileService.CreateProfile("IsolatedTestProfile", "26.3", 25, isIsolated: true);
            string isoDir = pathService.GetProfileDirectory(isolatedProfile);
            Directory.CreateDirectory(isoDir);

            string isoOptions = Path.Combine(isoDir, "options.txt");
            string isoContent = "isolated_only:true";
            File.WriteAllText(isoOptions, isoContent);

            // Prepare should not overwrite isolated profile
            await profileService.PrepareProfileEnvironmentAsync(isolatedProfile);
            if (File.ReadAllText(isoOptions) != isoContent)
                throw new Exception("Isolated profile options were unexpectedly overwritten by shared sync");
            if (!SafeFileOps.IsLink(Path.Combine(isoDir, "resourcepacks")))
                throw new Exception("Isolated profiles must still share resource packs");

            // Post-game sync should not write back to shared
            await profileService.SyncProfileToSharedAsync(isolatedProfile);
            if (File.ReadAllText(sharedOptions) != originalShared)
                throw new Exception("Isolated profile wrote back to shared folder");

            // Clean up
            profileService.DeleteProfile(isolatedProfile.Id);
            SafeFileOps.DeleteTree(isoDir);
        });

        // Test 5: JavaService Scan & Verification
        await AssertAsync("JavaService System Scan & Detection", async () =>
        {
            var javaService = new JavaService(sandboxPaths);
            var javas = javaService.ScanAllSystemJavas();
            Console.WriteLine($"  Found {javas.Count} Java installations on system.");

            string? java21 = await javaService.DetectInstalledJavaAsync(21);
            Console.WriteLine($"  Java 21 detection: {(java21 != null ? java21 : "Not installed")}");

            string? java25 = await javaService.DetectInstalledJavaAsync(25);
            Console.WriteLine($"  Java 25 detection: {(java25 != null ? java25 : "Not installed")}");
        });

        // Test 6: AuthService Offline Persistence
        await AssertAsync("AuthService Offline Account & Persistence", async () =>
        {
            var authService = new AuthService(sandboxPaths);

            var createdAccount = await authService.AddOfflineAccountAsync("TestRunnerPilot");
            if (createdAccount.Username != "TestRunnerPilot")
                throw new Exception("Session username mismatch");

            var updated = authService.GetAccounts();
            var found = updated.FirstOrDefault(a => a.Username == "TestRunnerPilot");
            if (found == null)
                throw new Exception("Saved account not found in GetAccounts()");

            bool removed = authService.RemoveAccount("TestRunnerPilot");
            if (!removed)
                throw new Exception("RemoveAccount returned false");

            var afterRemoval = authService.GetAccounts();
            if (afterRemoval.Any(a => a.Username == "TestRunnerPilot"))
                throw new Exception("Account was not removed");
        });

        // Test 7: ViewModels Instantiation & Commands
        await AssertAsync("MVVM ViewModels Execution", async () =>
        {
            var pathService = sandboxPaths;
            var profileService = sandboxProfiles;
            var javaService = new JavaService(sandboxPaths);
            var authService = new AuthService(sandboxPaths);
            var launchService = new LaunchService(pathService, profileService, javaService, authService, sharedContent: sandboxShared);
            var settings = LauncherSettings.Load();

            var mainVm = new MainWindowViewModel(pathService, profileService, javaService, authService, launchService, settings);
            var profilesVm = mainVm.ProfilesVM;
            var launchVm = mainVm.LaunchVM;
            var settingsVm = mainVm.SettingsVM;

            if (profilesVm.Profiles.Count == 0)
                throw new Exception("ProfilesViewModel has no profiles");

            if (launchVm.CurrentProfile == null)
                throw new Exception("LaunchViewModel has no active profile");

            settingsVm.MaxRamMb = 8192;
            if (settingsVm.MaxRamMb != 8192)
                throw new Exception($"Expected 8192 MaxRamMb, got {settingsVm.MaxRamMb}");

            await Task.CompletedTask;
        });

        // Every test catches its own failures, so this always runs; links are removed first so nothing outside is touched.
        SafeFileOps.DeleteTree(sandbox);

        Console.WriteLine("========================================");
        Console.WriteLine($" TESTS COMPLETE: {passed} PASSED, {failed} FAILED");
        Console.WriteLine("========================================");

        return failed > 0 ? 1 : 0;
    }
}
