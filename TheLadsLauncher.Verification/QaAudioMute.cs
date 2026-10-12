using System.Diagnostics;
using System.Runtime.InteropServices;

/// <summary>
/// Mutes the Windows Core Audio session(s) of QA game processes when opt-in silent mode is enabled
/// (LADS_VERIFY_SILENT=1 or &lt;shared root&gt;\.qa-silent). In-game volume remains at 10% so sound probes
/// continue to work while OS audio is suppressed.
/// </summary>
static class QaAudioMute
{
    private static bool s_loggedError;

    /// <summary>
    /// Opt-in: Mute only when LADS_VERIFY_SILENT=1 or the shared-root file .qa-silent exists;
    /// otherwise normal audible QA at the in-game 10% volume.
    /// </summary>
    public static bool IsSilent
    {
        get
        {
            if (Environment.GetEnvironmentVariable("LADS_VERIFY_SILENT") == "1")
                return true;
            try
            {
                return File.Exists(Path.Combine(QaShared.Root, ".qa-silent"));
            }
            catch
            {
                return false;
            }
        }
    }

    public static void MuteProcess(int processId) => MuteProcess((uint)processId);

    public static void MuteProcess(uint processId)
    {
        if (!IsSilent) return;

        try
        {
            var none = Guid.Empty;
            var iid = typeof(IAudioSessionManager2).GUID;
            var enumerator = (IMMDeviceEnumerator)new MMDeviceEnumerator();
            if (enumerator.EnumAudioEndpoints(0, 1, out var devices) != 0 || devices == null)
                return;

            if (devices.GetCount(out int deviceCount) != 0)
                return;

            for (int d = 0; d < deviceCount; d++)
            {
                if (devices.Item(d, out var device) != 0 || device == null)
                    continue;

                if (device.Activate(ref iid, 23, IntPtr.Zero, out object obj) != 0 || obj is not IAudioSessionManager2 manager)
                    continue;

                if (manager.GetSessionEnumerator(out var sessionEnumerator) != 0 || sessionEnumerator == null)
                    continue;

                if (sessionEnumerator.GetCount(out int sessionCount) != 0)
                    continue;

                for (int i = 0; i < sessionCount; i++)
                {
                    if (sessionEnumerator.GetSession(i, out var session) != 0 || session == null)
                        continue;

                    if (session.GetProcessId(out uint pid) != 0 || pid != processId)
                        continue;

                    if (session is ISimpleAudioVolume volume)
                    {
                        if (volume.GetMute(out bool isMuted) == 0 && !isMuted)
                        {
                            volume.SetMute(true, ref none);
                        }
                    }
                }
            }
        }
        catch (Exception ex)
        {
            if (!s_loggedError)
            {
                s_loggedError = true;
                Console.WriteLine($"[QaAudioMute] COM error: {ex.Message}");
            }
        }
    }

    [ComImport, Guid("BCDE0395-E52F-467C-8E3D-C4579291692E")]
    private class MMDeviceEnumerator { }

    [Guid("A95664D2-9614-4F35-A746-DE8DB63617E6"), InterfaceType(ComInterfaceType.InterfaceIsIUnknown)]
    private interface IMMDeviceEnumerator
    {
        int EnumAudioEndpoints(int flow, int mask, out IMMDeviceCollection devices);
        int NotImpl1();
    }

    [Guid("0BD7A1BE-7A1A-44DB-8397-CC5392387B5E"), InterfaceType(ComInterfaceType.InterfaceIsIUnknown)]
    private interface IMMDeviceCollection
    {
        int GetCount(out int n);
        int Item(int i, out IMMDevice device);
    }

    [Guid("D666063F-1587-4E43-81F1-B948E807363F"), InterfaceType(ComInterfaceType.InterfaceIsIUnknown)]
    private interface IMMDevice
    {
        int Activate(ref Guid iid, int ctx, IntPtr p, [MarshalAs(UnmanagedType.IUnknown)] out object o);
    }

    [Guid("77AA99A0-1BD6-484F-8BC7-2C654C9A9B6F"), InterfaceType(ComInterfaceType.InterfaceIsIUnknown)]
    private interface IAudioSessionManager2
    {
        int NotImpl1();
        int NotImpl2();
        int GetSessionEnumerator(out IAudioSessionEnumerator e);
    }

    [Guid("E2F5BB11-0570-40CA-ACDD-3AA01277DEE8"), InterfaceType(ComInterfaceType.InterfaceIsIUnknown)]
    private interface IAudioSessionEnumerator
    {
        int GetCount(out int n);
        int GetSession(int i, out IAudioSessionControl2 s);
    }

    [Guid("bfb7ff88-7239-4fc9-8fa2-07c950be9c6d"), InterfaceType(ComInterfaceType.InterfaceIsIUnknown)]
    private interface IAudioSessionControl2
    {
        int f1(); int f2(); int f3(); int f4(); int f5(); int f6(); int f7(); int f8(); int f9(); int f10(); int f11();
        int GetProcessId(out uint pid);
    }

    [Guid("87CE5498-68D6-44E5-9215-6DA47EF883D8"), InterfaceType(ComInterfaceType.InterfaceIsIUnknown)]
    private interface ISimpleAudioVolume
    {
        int SetMasterVolume(float v, ref Guid ctx);
        int GetMasterVolume(out float v);
        int SetMute(bool mute, ref Guid ctx);
        int GetMute(out bool mute);
    }
}
