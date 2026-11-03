using System;
using System.Collections.Generic;
using System.IO;
using System.Linq;
using System.Text;
using System.Text.Json.Nodes;

namespace TheLadsLauncher.Services;

/// <summary>
/// TheLadsLauncher.exe --client-import scan [--custom-dir &lt;path&gt;] | apply [&lt;client&gt;] &lt;Category,Category&gt; [--custom-dir &lt;path&gt;] | undo,
/// with --in-game when the game asks: the in-game dialog's way to the one importer, and the dry run's. Answers one JSON object on stdout;
/// no window, no account or token is touched.
/// </summary>
public static class ClientImportCli
{
    public const string Flag = "--client-import";
    private const int ListedLines = 60;

    public static int Run(string[] args)
    {
        JsonObject answer;
        int exit = 0;
        try
        {
            bool inGame = args.Contains("--in-game");
            var paths = PathService.Instance;
            var targets = new ImportTargets(paths, ProfileService.Instance.GetProfiles(), SharedContentService.ResolveDefaultRoot(),
                Path.Combine(paths.BaseDirectory, "settings.json"), inGame);
            answer = Answer(args.SkipWhile(a => a != Flag).Skip(1).ToArray(), ImportEnvironment.Current, targets);
        }
        catch (Exception e)
        {
            answer = new JsonObject { ["error"] = e.Message };
            exit = 1;
        }
        using var stdout = Console.OpenStandardOutput();
        stdout.Write(Encoding.UTF8.GetBytes(answer.ToJsonString()));
        return exit;
    }

    internal static JsonObject Answer(string[] rawArgs, ImportEnvironment env, ImportTargets targets)
    {
        var (command, customDir) = ParseArgs(rawArgs);
        if (customDir != null && !Directory.Exists(customDir))
            throw new ArgumentException("That folder does not exist.");

        switch (command.FirstOrDefault())
        {
            case "scan":
                var clients = new JsonArray();
                if (customDir != null)
                {
                    var customDesc = Describe(ClientImport.Plan(ClientImport.DetectCustomFolder(customDir), targets));
                    customDesc["customDir"] = Path.GetFullPath(customDir);
                    clients.Add(customDesc);
                }
                foreach (var source in ClientImport.Detect(env)) clients.Add(Describe(ClientImport.Plan(source, targets)));
                return new JsonObject { ["clients"] = clients, ["canUndo"] = ClientImport.LatestBackup(targets) != null };
            case "apply" when customDir != null && command.Length >= 2:
                var customSource = ClientImport.DetectCustomFolder(customDir);
                string catArg = command.Length >= 3 ? command[2] : command[1];
                var customCategories = catArg.Split(',').Select(Enum.Parse<ImportCategory>).ToList();
                return Describe(ClientImport.Apply(ClientImport.Plan(customSource, targets), customCategories, targets));
            case "apply" when command.Length >= 3:
                var chosen = ClientImport.Detect(env).FirstOrDefault(s => s.Id == command[1])
                    ?? throw new ArgumentException("That client is not installed.");
                var categories = command[2].Split(',').Select(Enum.Parse<ImportCategory>).ToList();
                return Describe(ClientImport.Apply(ClientImport.Plan(chosen, targets), categories, targets));
            case "undo":
                var backup = ClientImport.LatestBackup(targets) ?? throw new InvalidOperationException("There is no import to undo.");
                return Describe(ClientImport.Undo(backup, targets));
            default:
                throw new ArgumentException("Usage: --client-import scan | apply [<client>] <categories> [--custom-dir <path>] | undo [--in-game]");
        }
    }

    private static (string[] Command, string? CustomDir) ParseArgs(string[] rawArgs)
    {
        var positional = new List<string>();
        string? customDir = null;
        for (int i = 0; i < rawArgs.Length; i++)
        {
            string arg = rawArgs[i];
            if (arg == "--in-game") continue;
            if (arg is "--custom-dir" or "--from-dir")
            {
                if (i + 1 >= rawArgs.Length) throw new ArgumentException($"{arg} requires a folder path.");
                customDir = rawArgs[++i];
            }
            else if (arg.StartsWith("--custom-dir=", StringComparison.Ordinal))
                customDir = arg["--custom-dir=".Length..];
            else if (arg.StartsWith("--from-dir=", StringComparison.Ordinal))
                customDir = arg["--from-dir=".Length..];
            else
                positional.Add(arg);
        }
        return (positional.ToArray(), customDir);
    }

    internal static JsonObject Describe(ImportPlan plan)
    {
        var categories = new JsonArray();
        foreach (var category in plan.Categories.Values)
        {
            var described = new JsonObject
            {
                ["id"] = category.Category.ToString(),
                ["changes"] = category.Changes.Count,
                ["skipped"] = category.SkippedCount,
                ["changeLines"] = Lines(category.Changes),
                ["skippedLines"] = Lines(category.Skipped)
            };
            if (category.Problem != null) described["problem"] = category.Problem;
            categories.Add(described);
        }
        return new JsonObject { ["id"] = plan.Source.Id, ["name"] = plan.Source.Name, ["basic"] = plan.Source.Basic, ["note"] = plan.Source.Note, ["categories"] = categories };
    }

    private static JsonArray Lines(IEnumerable<string> lines) => new(lines.Take(ListedLines).Select(l => (JsonNode)JsonValue.Create(l)!).ToArray());

    private static JsonObject Describe(ImportResult result)
    {
        var applied = new JsonObject();
        foreach (var (category, count) in result.Applied) applied[category.ToString()] = count;
        return new JsonObject
        {
            ["applied"] = applied,
            ["errors"] = new JsonArray(result.Errors.Select(e => (JsonNode)JsonValue.Create(e)!).ToArray()),
            ["configChanged"] = result.ConfigChanged,
            ["restartNeeded"] = result.RestartNeeded,
            ["launcherPending"] = result.LauncherPending,
            ["backup"] = result.BackupDirectory
        };
    }
}
