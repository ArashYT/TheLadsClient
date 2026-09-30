using Xunit;

/// <summary>
/// Tests with wall-clock limits (lock waits, stall timers) run alone after the parallel tests:
/// on a loaded 2-core CI runner, parallel process starts and JVMs delay timers by seconds.
/// </summary>
[CollectionDefinition(Name, DisableParallelization = true)]
public sealed class TimingSensitive { public const string Name = "Timing-sensitive (serial)"; }
