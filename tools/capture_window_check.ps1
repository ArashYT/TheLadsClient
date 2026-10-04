# QA only: captures a game window the way OBS and Discord do and reports whether the capture is black.
#   bitblt      - GDI copy of the screen over the window (GDI capture)
#   printwindow - the window's own composition surface (PW_RENDERFULLCONTENT)
#   duplicate   - DXGI Desktop Duplication of the monitor (OBS "Display Capture", Discord screen share)
#   wgc         - Windows Graphics Capture of the window (OBS "Window Capture", Discord window share)
# A window that exactly covers a monitor can be promoted to fullscreen/independent flip by Windows, and capture tools then see black.
# Usage: powershell -NoProfile -File capture_window_check.ps1 -Hwnd <decimal handle> -OutDir <folder> -Tag <name>
param([Parameter(Mandatory)][long]$Hwnd, [Parameter(Mandatory)][string]$OutDir, [Parameter(Mandatory)][string]$Tag)
Add-Type -ReferencedAssemblies System.Drawing -TypeDefinition @'
using System;
using System.Drawing;
using System.Drawing.Imaging;
using System.IO;
using System.Runtime.InteropServices;
public static class WindowCapture {
    [StructLayout(LayoutKind.Sequential)] struct RECT { public int L, T, R, B; }
    [DllImport("user32.dll")] static extern bool SetProcessDPIAware();
    [DllImport("user32.dll")] static extern bool GetWindowRect(IntPtr h, out RECT r);
    [DllImport("user32.dll")] static extern bool PrintWindow(IntPtr h, IntPtr dc, uint flags);
    [DllImport("user32.dll")] static extern bool SetWindowPos(IntPtr h, IntPtr after, int x, int y, int w, int cy, uint flags);
    [DllImport("user32.dll")] static extern bool SetForegroundWindow(IntPtr h);
    [DllImport("d3d11.dll")] static extern int D3D11CreateDevice(IntPtr adapter, int type, IntPtr sw, uint flags, IntPtr levels, uint n, uint sdk, out IntPtr dev, out int level, out IntPtr ctx);
    [DllImport("d3d11.dll")] static extern int CreateDirect3D11DeviceFromDXGIDevice(IntPtr dxgiDevice, out IntPtr graphicsDevice);
    [DllImport("combase.dll")] static extern int RoGetActivationFactory(IntPtr classId, ref Guid iid, out IntPtr factory);
    [DllImport("combase.dll")] static extern int WindowsCreateString([MarshalAs(UnmanagedType.LPWStr)] string s, int len, out IntPtr h);
    [ComImport, Guid("3628E81B-3CAC-4C60-B7F4-23CE0E0C3356"), InterfaceType(ComInterfaceType.InterfaceIsIUnknown)]
    interface IGraphicsCaptureItemInterop { [PreserveSig] int CreateForWindow(IntPtr window, ref Guid iid, out IntPtr item); }

    static string outDir, tag;
    static IntPtr device, context;
    public static string Report = "";

    public static void Run(long hwnd, string dir, string name, Action extra) {
        SetProcessDPIAware();
        outDir = dir; tag = name; Directory.CreateDirectory(dir);
        IntPtr h = new IntPtr(hwnd);
        // On top of everything for the capture, as a window the user is looking at is.
        SetWindowPos(h, new IntPtr(-1), 0, 0, 0, 0, 0x13); SetForegroundWindow(h);
        System.Threading.Thread.Sleep(3000); // a promotion to flip takes a moment
        RECT r; GetWindowRect(h, out r);
        int w = r.R - r.L, hh = r.B - r.T;
        Add("window rect " + r.L + "," + r.T + " " + w + "x" + hh);
        try { using (var b = new Bitmap(w, hh)) { using (var g = Graphics.FromImage(b)) g.CopyFromScreen(r.L, r.T, 0, 0, new Size(w, hh)); Save(b, "bitblt"); } } catch (Exception e) { Add("bitblt FAILED " + e.Message); }
        try {
            using (var b = new Bitmap(w, hh)) {
                using (var g = Graphics.FromImage(b)) { IntPtr dc = g.GetHdc(); bool ok = PrintWindow(h, dc, 2); g.ReleaseHdc(dc); if (!ok) Add("printwindow returned false"); }
                Save(b, "printwindow");
            }
        } catch (Exception e) { Add("printwindow FAILED " + e.Message); }
        try { Duplicate(r); } catch (Exception e) { Add("duplicate FAILED " + e.Message); }
        try { extra(); } catch (Exception e) { Add("wgc FAILED " + e.Message); }
        SetWindowPos(h, new IntPtr(-2), 0, 0, 0, 0, 0x13);
        File.WriteAllText(Path.Combine(dir, name + "-capture.txt"), Report);
    }

    public static void Add(string line) { Report += line + "\n"; Console.WriteLine(line); }

    // Share of pixels that are not black, and the mean brightness: a captured Minecraft frame has plenty, a black capture has none.
    static void Save(Bitmap b, string method) {
        var data = b.LockBits(new Rectangle(0, 0, b.Width, b.Height), ImageLockMode.ReadOnly, PixelFormat.Format32bppArgb);
        long lit = 0, sum = 0, n = (long)b.Width * b.Height;
        byte[] row = new byte[data.Stride];
        for (int y = 0; y < b.Height; y++) {
            Marshal.Copy(new IntPtr(data.Scan0.ToInt64() + (long)y * data.Stride), row, 0, row.Length);
            for (int x = 0; x < b.Width; x++) { int m = Math.Max(row[x * 4], Math.Max(row[x * 4 + 1], row[x * 4 + 2])); sum += m; if (m > 12) lit++; }
        }
        b.UnlockBits(data);
        double litPct = 100.0 * lit / n;
        string file = Path.Combine(outDir, tag + "-" + method + ".png");
        b.Save(file, ImageFormat.Png);
        Add(method + " " + b.Width + "x" + b.Height + " lit=" + litPct.ToString("F1") + "% mean=" + ((double)sum / n).ToString("F1") + " -> " + (litPct > 5 ? "SHOWS THE GAME" : "BLACK") + " (" + file + ")");
    }

    // Direct3D 11 and DXGI through raw COM calls (vtable slots of d3d11.h / dxgi1_2.h).
    static IntPtr Slot(IntPtr o, int i) { return Marshal.ReadIntPtr(Marshal.ReadIntPtr(o), i * IntPtr.Size); }
    delegate int Qi(IntPtr o, ref Guid iid, out IntPtr r);
    delegate int Get(IntPtr o, out IntPtr r);
    delegate int GetIface(IntPtr o, ref Guid iid, out IntPtr r);
    delegate int EnumOut(IntPtr o, uint i, out IntPtr r);
    delegate int Dup(IntPtr o, IntPtr dev, out IntPtr r);
    delegate int Acquire(IntPtr o, uint ms, byte[] info, out IntPtr res);
    delegate int Release1(IntPtr o);
    delegate void GetDesc(IntPtr o, uint[] d);
    delegate int CreateTex(IntPtr o, uint[] d, IntPtr init, out IntPtr t);
    delegate void Copy(IntPtr o, IntPtr dst, IntPtr src);
    delegate int Map(IntPtr o, IntPtr res, uint sub, uint type, uint flags, byte[] mapped);
    delegate void Unmap(IntPtr o, IntPtr res, uint sub);
    static T Fn<T>(IntPtr o, int i) { return (T)(object)Marshal.GetDelegateForFunctionPointer(Slot(o, i), typeof(T)); }
    static IntPtr Query(IntPtr o, string iid) { Guid g = new Guid(iid); IntPtr r; Check(Fn<Qi>(o, 0)(o, ref g, out r), "QueryInterface " + iid); return r; }
    static void Check(int hr, string what) { if (hr < 0) throw new Exception(what + " 0x" + hr.ToString("X")); }
    const string IID_DXGIDevice = "54ec77fa-1377-44e6-8c32-88fd5f44c84c", IID_Texture2D = "6f15aaf2-d208-4e89-9ab4-489535d34f9c";

    static void NewDevice() {
        if (device != IntPtr.Zero) return;
        int level;
        Check(D3D11CreateDevice(IntPtr.Zero, 1, IntPtr.Zero, 0x20, IntPtr.Zero, 0, 7, out device, out level, out context), "D3D11CreateDevice"); // BGRA support
    }

    // A GPU texture read back through a staging copy, cropped to (x, y, width, height), saved as <method>.
    static void SaveTexture(IntPtr tex, int x, int y, int width, int height, string method) {
        uint[] d = new uint[11]; Fn<GetDesc>(tex, 10)(tex, d);
        d[7] = 3; d[8] = 0; d[9] = 0x20000; d[10] = 0; // STAGING, no bind flags, CPU read
        IntPtr stage; Check(Fn<CreateTex>(device, 5)(device, d, IntPtr.Zero, out stage), "CreateTexture2D");
        Fn<Copy>(context, 47)(context, stage, tex);
        byte[] mapped = new byte[16]; Check(Fn<Map>(context, 14)(context, stage, 0, 1, 0, mapped), "Map");
        IntPtr p = new IntPtr(BitConverter.ToInt64(mapped, 0)); uint pitch = BitConverter.ToUInt32(mapped, 8); // D3D11_MAPPED_SUBRESOURCE
        int cx = Math.Max(0, x), cy = Math.Max(0, y), cw = Math.Min((int)d[0], x + width) - cx, ch = Math.Min((int)d[1], y + height) - cy;
        using (var b = new Bitmap(cw, ch, PixelFormat.Format32bppArgb)) {
            var data = b.LockBits(new Rectangle(0, 0, cw, ch), ImageLockMode.WriteOnly, PixelFormat.Format32bppArgb);
            byte[] row = new byte[cw * 4];
            for (int j = 0; j < ch; j++) { Marshal.Copy(new IntPtr(p.ToInt64() + (long)(cy + j) * pitch + cx * 4L), row, 0, row.Length); Marshal.Copy(row, 0, new IntPtr(data.Scan0.ToInt64() + (long)j * data.Stride), row.Length); }
            b.UnlockBits(data);
            Save(b, method);
        }
        Fn<Unmap>(context, 15)(context, stage, 0);
    }

    // The monitor, cropped to the window (a primary-monitor window: the output starts at 0,0).
    static void Duplicate(RECT win) {
        NewDevice();
        IntPtr dxgi = Query(device, IID_DXGIDevice), adapter, output;
        Check(Fn<Get>(dxgi, 7)(dxgi, out adapter), "GetAdapter");
        Check(Fn<EnumOut>(adapter, 7)(adapter, 0, out output), "EnumOutputs(0)");
        IntPtr output1 = Query(output, "00cddea8-939b-4b83-a340-a685226666cc"), dup;
        Check(Fn<Dup>(output1, 22)(output1, device, out dup), "DuplicateOutput");
        IntPtr res = IntPtr.Zero; byte[] info = new byte[48]; int hr = 0;
        for (int i = 0; i < 20; i++) {
            hr = Fn<Acquire>(dup, 8)(dup, 250, info, out res);
            if (hr >= 0 && BitConverter.ToUInt32(info, 16) > 0) break; // AccumulatedFrames
            if (hr >= 0) Fn<Release1>(dup, 14)(dup);
        }
        Check(hr, "AcquireNextFrame");
        SaveTexture(Query(res, IID_Texture2D), win.L, win.T, win.R - win.L, win.B - win.T, "duplicate");
        Fn<Release1>(dup, 14)(dup);
    }

    // Windows Graphics Capture: the WinRT objects are made by the PowerShell below, these are the COM pieces it needs.
    public static IntPtr ItemForWindow(long hwnd) {
        IntPtr hs, factory, item; Guid interop = new Guid("3628E81B-3CAC-4C60-B7F4-23CE0E0C3356"), iid = new Guid("79C3F95B-31F7-4EC2-A464-632EF5D30760");
        Check(WindowsCreateString("Windows.Graphics.Capture.GraphicsCaptureItem", 44, out hs), "WindowsCreateString");
        Check(RoGetActivationFactory(hs, ref interop, out factory), "RoGetActivationFactory");
        Check(((IGraphicsCaptureItemInterop)Marshal.GetObjectForIUnknown(factory)).CreateForWindow(new IntPtr(hwnd), ref iid, out item), "CreateForWindow");
        return item;
    }
    public static IntPtr WinRtDevice() {
        NewDevice(); IntPtr graphics;
        Check(CreateDirect3D11DeviceFromDXGIDevice(Query(device, IID_DXGIDevice), out graphics), "CreateDirect3D11DeviceFromDXGIDevice");
        return graphics;
    }
    public static void SaveSurface(object surface, string method) {
        IntPtr access = Query(Marshal.GetIUnknownForObject(surface), "A9B3D012-3DF2-4EE3-B8D1-8695F457D3C1"), tex; Guid iid = new Guid(IID_Texture2D);
        Check(Fn<GetIface>(access, 3)(access, ref iid, out tex), "GetInterface(ID3D11Texture2D)");
        uint[] d = new uint[11]; Fn<GetDesc>(tex, 10)(tex, d);
        SaveTexture(tex, 0, 0, (int)d[0], (int)d[1], method);
    }
}
'@

function Capture-WindowGraphics {
    [void][Windows.Graphics.Capture.GraphicsCaptureItem, Windows.Graphics.Capture, ContentType = WindowsRuntime]
    [void][Windows.Graphics.Capture.Direct3D11CaptureFramePool, Windows.Graphics.Capture, ContentType = WindowsRuntime]
    [void][Windows.Graphics.DirectX.DirectXPixelFormat, Windows.Graphics.DirectX, ContentType = WindowsRuntime]
    [void][Windows.Graphics.DirectX.Direct3D11.IDirect3DDevice, Windows.Graphics.DirectX.Direct3D11, ContentType = WindowsRuntime]
    $item = [Runtime.InteropServices.Marshal]::GetObjectForIUnknown([WindowCapture]::ItemForWindow($Hwnd))
    $device = [Runtime.InteropServices.Marshal]::GetObjectForIUnknown([WindowCapture]::WinRtDevice())
    # Reflection: PowerShell's own conversion cannot cast the COM wrapper to the WinRT interface, the CLR's cast (a QueryInterface) can.
    $create = [Windows.Graphics.Capture.Direct3D11CaptureFramePool].GetMethod('CreateFreeThreaded')
    $pool = $create.Invoke($null, @($device, [Windows.Graphics.DirectX.DirectXPixelFormat]::B8G8R8A8UIntNormalized, 2, $item.Size))
    $session = $pool.CreateCaptureSession($item)
    $session.StartCapture()
    $frame = $null
    for ($i = 0; $i -lt 50 -and -not $frame; $i++) { Start-Sleep -Milliseconds 100; $frame = $pool.TryGetNextFrame() }
    if (-not $frame) { [WindowCapture]::Add('wgc: no frame arrived in 5 s (a black or blocked capture delivers none)'); return }
    [WindowCapture]::SaveSurface($frame.Surface, 'wgc')
    $frame.Dispose(); $session.Dispose(); $pool.Dispose()
}

[WindowCapture]::Run($Hwnd, $OutDir, $Tag, [Action]{ Capture-WindowGraphics })
