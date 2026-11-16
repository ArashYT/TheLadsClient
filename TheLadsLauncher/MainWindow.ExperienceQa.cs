using System;
using System.IO;
using System.Linq;
using System.Threading;
using System.Threading.Tasks;
using TheLadsLauncher.Services;

namespace TheLadsLauncher;

/// <summary>
/// --preview-experience-update &lt;outputDir&gt; [--start-game] (sandbox only, like the other --preview- modes): the Lads SMP Reborn card's
/// Play path without the window and without an invited Microsoft account. It runs the same calls as ExperiencesView.LadsSmpRebornPlay_Click:
/// install from the pinned pack when the instance does not exist, else keep it; apply the pack patches it has not had yet (the bundled ones
/// included); bring the Fabric Loader to the pinned version; with --start-game also launch it through ModpackLauncher.LaunchAsync as an
/// offline test player (debug builds only). Writes experience-update.log to the output folder, then exits.
/// </summary>
public partial class MainWindow
{
    private async Task RunExperienceUpdatePreviewAsync(string output, bool startGame)
    {
        Directory.CreateDirectory(output);
        var logFile = Path.Combine(output, "experience-update.log");
        void Line(string text)
        {
            var line = DateTime.Now.ToString("HH:mm:ss.fff") + " " + text;
            File.AppendAllText(logFile, line + Environment.NewLine);
            Console.WriteLine(line);
        }
        try
        {
            var data = PathService.Instance.BaseDirectory;
            var real = Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.ApplicationData), ".theladsclient");
            if (SafeFileOps.IsSameOrInside(SafeFileOps.GetFinalPath(data), SafeFileOps.GetFinalPath(real)))
            {
                Line("refused: this mode runs only against a sandbox THELADS_DIR, never the real launcher data");
                return;
            }
            var exp = ExperiencesService.LadsSmpReborn;
            var id = ExperiencesService.ActiveLadsSmpRebornInstanceId(false); // the release instance, not the dev one
            Line($"launcher {Program.Version}, data {data}, instance {id}, pin {exp.PackArchiveUrl} sha256 {exp.PackArchiveSha256}");
            var token = CancellationToken.None;
            bool installed = ExperiencesService.IsInstalled(data, id);
            Line("instance installed before: " + installed + (installed ? ", recorded pack version " + ExperiencesService.GetInstance(data, id)?.PackVersion
                + ", patches applied " + string.Join(",", ModpackPatchEngine.GetAppliedPatches(ExperiencesService.InstanceDirectory(data, id))) : ""));
            var instance = installed
                ? ExperiencesService.GetInstance(data, id) ?? await ExperiencesService.InstallLadsSmpRebornAsync(data, Line, token, ExperiencesService.DefaultHttp, id)
                : await ExperiencesService.InstallLadsSmpRebornAsync(data, Line, token, ExperiencesService.DefaultHttp, id);
            int applied = await ExperiencesService.ApplyPendingPatchesAsync(data, instance.Id, ExperiencesService.DefaultHttp, Line, token, ExperiencesService.BundledPatchesDirectory);
            Line($"patches applied this run: {applied}; applied list now: {string.Join(",", ModpackPatchEngine.GetAppliedPatches(instance.Directory))}");
            if (instance.LoaderVersion != exp.LoaderVersion)
            {
                instance.LoaderVersion = exp.LoaderVersion;
                Modpacks.Save(instance);
            }
            Line($"instance.json packVersion {ExperiencesService.GetInstance(data, id)?.PackVersion}, loader {instance.LoaderVersion}");
            if (startGame)
            {
                if (!IsDevOfflineAllowed) { Line("not starting the game: offline test players need a debug build"); return; }
                var session = AccountIdentity.CreateOfflineSession("QaTdc");
                var process = await ModpackLauncher.LaunchAsync(data, instance, session, settings, JavaService.Instance, ExperiencesService.DefaultHttp, Line, token);
                Line($"game started, PID {process.Id}");
                File.WriteAllText(Path.Combine(output, "game.pid"), process.Id.ToString());
                // Stay up so the launcher keeps the game's log readers going; the QA script ends the game and this process by PID.
                await Task.Delay(TimeSpan.FromMinutes(10));
            }
        }
        catch (Exception e) { Line("FAILED: " + e); }
        finally { Line("done"); Avalonia.Threading.Dispatcher.UIThread.Post(Close); }
    }
}
