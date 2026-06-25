using Avalonia;
using Avalonia.Controls;
using Avalonia.Interactivity;
using Avalonia.Media;
using Avalonia.Media.Imaging;
using Avalonia.Platform.Storage;
using System;
using System.IO;
using System.Net.Http;
using System.Runtime.InteropServices;
using System.Threading.Tasks;

namespace TheLadsLauncher.Views
{
    public partial class SkinManagerView : UserControl
    {
        public string ActiveUsername { get; set; } = "No Account Selected";
        public event EventHandler<string>? LogMessage;
        
        private static readonly HttpClient _httpClient = new HttpClient();
        
        // Skin & Cape Editor state
        private string _selectedEditor = "Skin";
        private int _editorWidth = 8;
        private int _editorHeight = 8;
        private Color[,] _skinBasePixels = new Color[64, 64];
        private Color[,] _skinOverlayPixels = new Color[64, 64];
        private Color[,] _capePixels = new Color[64, 64];
        private bool _isDrawing = false;
        private Color _activeEditorColor = Colors.White;
        private bool _isUpdatingColor = false;

        public SkinManagerView()
        {
            InitializeComponent();
            InitializeEditor();
        }

        private void Log(string msg)
        {
            LogMessage?.Invoke(this, msg);
        }

        public async Task LoadPlayerSkin(string username)
        {
            ActiveUsername = username;
            try
            {
                SelectedAccountText.Text = username;

                string localSkinPath = Path.Combine(@"C:\The Lads Client", "skin.png");
                if (File.Exists(localSkinPath))
                {
                    using (var stream = File.OpenRead(localSkinPath))
                    {
                        PlayerSkinPreview.Source = Bitmap.DecodeToWidth(stream, 150);
                    }
                }
                else
                {
                    // Load 3D-like body render
                    string bodyUrl = $"https://mc-heads.net/body/player/{username}/150";
                    var bodyBytes = await _httpClient.GetByteArrayAsync(bodyUrl);
                    using (var ms = new MemoryStream(bodyBytes))
                    {
                        PlayerSkinPreview.Source = new Bitmap(ms);
                    }
                }

                LoadPresetsList(username);
            }
            catch
            {
                PlayerSkinPreview.Source = null;
            }
        }

        private void LoadPresetsList(string username)
        {
            PresetSelector.Items.Clear();
            string presetDir = Path.Combine(@"C:\The Lads Client", "presets", username);
            if (Directory.Exists(presetDir))
            {
                var files = Directory.GetFiles(presetDir, "*.png");
                foreach (var f in files)
                {
                    PresetSelector.Items.Add(Path.GetFileNameWithoutExtension(f));
                }
            }
            if (PresetSelector.Items.Count > 0)
                PresetSelector.SelectedIndex = 0;
        }

        private void SavePreset_Click(object? sender, RoutedEventArgs e)
        {
            string username = SelectedAccountText.Text ?? "";
            if (string.IsNullOrEmpty(username) || username == "No Account Selected") return;

            string name = "Preset_" + DateTime.Now.ToString("yyyyMMdd_HHmmss");
            string presetDir = Path.Combine(@"C:\The Lads Client", "presets", username);
            Directory.CreateDirectory(presetDir);
            string dest = Path.Combine(presetDir, name + ".png");

            SaveSkinPng(dest, false);
            LoadPresetsList(username);
            Log($"[Presets] Saved skin preset: {name}");
        }

        private void ApplyPreset_Click(object? sender, RoutedEventArgs e)
        {
            string username = SelectedAccountText.Text ?? "";
            if (string.IsNullOrEmpty(username)) return;
            string? selected = PresetSelector.SelectedItem as string;
            if (string.IsNullOrEmpty(selected)) return;

            string src = Path.Combine(@"C:\The Lads Client", "presets", username, selected + ".png");
            string dest = Path.Combine(@"C:\The Lads Client", "skin.png");
            if (File.Exists(src))
            {
                File.Copy(src, dest, true);
                Log($"[Presets] Applied skin preset: {selected}");
                _ = LoadPlayerSkin(username);
                LoadPixelsFromSkinFile(dest);
                if (EditorContainer.IsVisible)
                {
                    DrawPixelGrid();
                }
            }
        }

        private void DeletePreset_Click(object? sender, RoutedEventArgs e)
        {
            string username = SelectedAccountText.Text ?? "";
            if (string.IsNullOrEmpty(username)) return;
            string? selected = PresetSelector.SelectedItem as string;
            if (string.IsNullOrEmpty(selected)) return;

            string path = Path.Combine(@"C:\The Lads Client", "presets", username, selected + ".png");
            if (File.Exists(path))
            {
                File.Delete(path);
                Log($"[Presets] Deleted skin preset: {selected}");
                LoadPresetsList(username);
            }
        }

        private async void UploadSkin_Click(object? sender, RoutedEventArgs e)
        {
            var topLevel = TopLevel.GetTopLevel(this);
            if (topLevel == null) return;
            
            var files = await topLevel.StorageProvider.OpenFilePickerAsync(new FilePickerOpenOptions
            {
                Title = "Select Skin PNG File",
                FileTypeFilter = new[] { FilePickerFileTypes.ImageAll }
            });

            if (files != null && files.Count > 0)
            {
                string src = files[0].Path.LocalPath;
                string dest = Path.Combine(@"C:\The Lads Client", "skin.png");
                File.Copy(src, dest, true);
                Log($"[Skin] Imported skin from local file.");
                
                string username = SelectedAccountText.Text ?? "";
                if (!string.IsNullOrEmpty(username) && username != "No Account Selected")
                    _ = LoadPlayerSkin(username);

                LoadPixelsFromSkinFile(dest);
                if (EditorContainer.IsVisible)
                {
                    DrawPixelGrid();
                }
            }
        }

        private void InitializeEditor()
        {
            var palette = new[] {
                Colors.Red, Colors.Orange, Colors.Yellow, Colors.Green,
                Colors.Blue, Colors.Purple, Colors.Pink, Colors.White,
                Colors.Black, Colors.Gray, Colors.Brown, Colors.Teal
            };
            ColorPaletteContainer.Children.Clear();
            foreach (var c in palette)
            {
                var btn = new Button
                {
                    Width = 24, Height = 24,
                    Background = new SolidColorBrush(c),
                    Margin = new Thickness(2),
                    CornerRadius = new CornerRadius(4)
                };
                btn.Click += (s, e) => { SetEditorColor(c); };
                ColorPaletteContainer.Children.Add(btn);
            }

            // Sliders property change bindings
            SliderRed.PropertyChanged += (s, e) => { if (e.Property.Name == "Value") UpdateColorFromSliders(); };
            SliderGreen.PropertyChanged += (s, e) => { if (e.Property.Name == "Value") UpdateColorFromSliders(); };
            SliderBlue.PropertyChanged += (s, e) => { if (e.Property.Name == "Value") UpdateColorFromSliders(); };

            ColorHexInput.PropertyChanged += (s, e) => {
                if (e.Property.Name == "Text" && ColorHexInput.Text != null && ColorHexInput.Text.StartsWith("#") && ColorHexInput.Text.Length == 9) {
                    if (_isUpdatingColor) return;
                    try {
                        var parsed = Color.Parse(ColorHexInput.Text);
                        SetEditorColor(parsed);
                    } catch {}
                }
            };

            SkinPartSelector.SelectionChanged += (s, e) => UpdateEditorGridSize();
            RadioBaseLayer.IsCheckedChanged += (s, e) => DrawPixelGrid();
            RadioOverlayLayer.IsCheckedChanged += (s, e) => DrawPixelGrid();

            // Initial color setup
            SetEditorColor(Colors.White);

            PixelEditorCanvas.PointerReleased += (s, e) => _isDrawing = false;
            PixelEditorCanvas.PointerExited += (s, e) => _isDrawing = false;

            // Load skin.png if exists
            LoadPixelsFromSkinFile(Path.Combine(@"C:\The Lads Client", "skin.png"));
            LoadPixelsFromSkinFile(Path.Combine(@"C:\The Lads Client", "config", "cape.png"), true);
        }

        private void UpdateColorFromSliders()
        {
            if (_isUpdatingColor) return;
            _isUpdatingColor = true;

            byte r = (byte)SliderRed.Value;
            byte g = (byte)SliderGreen.Value;
            byte b = (byte)SliderBlue.Value;
            byte a = _activeEditorColor.A;
            _activeEditorColor = Color.FromArgb(a, r, g, b);

            TextRed.Text = r.ToString();
            TextGreen.Text = g.ToString();
            TextBlue.Text = b.ToString();

            ActiveColorPreview.Background = new SolidColorBrush(_activeEditorColor);
            ColorHexInput.Text = $"#{_activeEditorColor.A:X2}{_activeEditorColor.R:X2}{_activeEditorColor.G:X2}{_activeEditorColor.B:X2}";
            
            _isUpdatingColor = false;
        }

        private void SetEditorColor(Color color)
        {
            if (_isUpdatingColor) return;
            _isUpdatingColor = true;

            _activeEditorColor = color;
            SliderRed.Value = color.R;
            SliderGreen.Value = color.G;
            SliderBlue.Value = color.B;

            TextRed.Text = color.R.ToString();
            TextGreen.Text = color.G.ToString();
            TextBlue.Text = color.B.ToString();

            ActiveColorPreview.Background = new SolidColorBrush(color);
            ColorHexInput.Text = $"#{color.A:X2}{color.R:X2}{color.G:X2}{color.B:X2}";

            _isUpdatingColor = false;
        }

        private void ToggleSkinEditor_Click(object? sender, RoutedEventArgs e)
        {
            _selectedEditor = "Skin";
            ToggleSkinEditorBtn.Classes.Add("active");
            ToggleCapeEditorBtn.Classes.Remove("active");
            EditorContainer.IsVisible = true;
            UpdateEditorGridSize();
        }

        private void ToggleCapeEditor_Click(object? sender, RoutedEventArgs e)
        {
            _selectedEditor = "Cape";
            ToggleCapeEditorBtn.Classes.Add("active");
            ToggleSkinEditorBtn.Classes.Remove("active");
            EditorContainer.IsVisible = true;
            UpdateEditorGridSize();
        }

        private void UpdateEditorGridSize()
        {
            if (_selectedEditor == "Cape")
            {
                _editorWidth = 22;
                _editorHeight = 17;
                EditorLayersRow.IsVisible = false;
                SkinPartSelector.IsVisible = false;
            }
            else
            {
                EditorLayersRow.IsVisible = true;
                SkinPartSelector.IsVisible = true;
                string part = (SkinPartSelector.SelectedItem as ComboBoxItem)?.Content as string ?? "Head (8x8)";
                if (part.Contains("Head")) { _editorWidth = 8; _editorHeight = 8; }
                else if (part.Contains("Body")) { _editorWidth = 8; _editorHeight = 12; }
                else { _editorWidth = 4; _editorHeight = 12; }
            }
            DrawPixelGrid();
        }

        private void DrawPixelGrid()
        {
            PixelEditorCanvas.Children.Clear();
            double canvasSize = 192.0;
            double pixelSize = canvasSize / Math.Max(_editorWidth, _editorHeight);

            double offsetX = (canvasSize - (_editorWidth * pixelSize)) / 2;
            double offsetY = (canvasSize - (_editorHeight * pixelSize)) / 2;

            bool isOverlay = RadioOverlayLayer.IsChecked ?? false;

            for (int y = 0; y < _editorHeight; y++)
            {
                for (int x = 0; x < _editorWidth; x++)
                {
                    int localX = x;
                    int localY = y;
                    
                    var border = new Border
                    {
                        Width = pixelSize,
                        Height = pixelSize,
                        BorderBrush = new SolidColorBrush(Color.Parse("#1A1A22")),
                        BorderThickness = new Thickness(0.5)
                    };

                    Color c = GetEditorPixel(localX, localY, isOverlay);
                    border.Background = new SolidColorBrush(c);

                    border.PointerPressed += (s, e) => {
                        if (e.GetCurrentPoint(PixelEditorCanvas).Properties.IsLeftButtonPressed)
                        {
                            _isDrawing = true;
                            border.Background = new SolidColorBrush(_activeEditorColor);
                            SetEditorPixel(localX, localY, isOverlay, _activeEditorColor);
                        }
                    };

                    border.PointerMoved += (s, e) => {
                        if (_isDrawing && e.GetCurrentPoint(PixelEditorCanvas).Properties.IsLeftButtonPressed)
                        {
                            border.Background = new SolidColorBrush(_activeEditorColor);
                            SetEditorPixel(localX, localY, isOverlay, _activeEditorColor);
                        }
                    };

                    Canvas.SetLeft(border, offsetX + (x * pixelSize));
                    Canvas.SetTop(border, offsetY + (y * pixelSize));
                    PixelEditorCanvas.Children.Add(border);
                }
            }
        }

        private (int X, int Y) GetMappedCoordinates(int localX, int localY, bool isOverlay)
        {
            if (_selectedEditor == "Cape")
            {
                return (1 + localX, 1 + localY);
            }

            string part = (SkinPartSelector.SelectedItem as ComboBoxItem)?.Content as string ?? "Head (8x8)";
            if (part.Contains("Head"))
            {
                return (isOverlay ? 40 + localX : 8 + localX, 8 + localY);
            }
            else if (part.Contains("Body"))
            {
                return (isOverlay ? 20 + localX : 20 + localX, isOverlay ? 36 + localY : 20 + localY);
            }
            else if (part.Contains("Left Arm"))
            {
                return (isOverlay ? 52 + localX : 36 + localX, 52 + localY);
            }
            else if (part.Contains("Right Arm"))
            {
                return (isOverlay ? 44 + localX : 44 + localX, isOverlay ? 36 + localY : 20 + localY);
            }
            else if (part.Contains("Left Leg"))
            {
                return (isOverlay ? 4 + localX : 20 + localX, 52 + localY);
            }
            else
            {
                return (isOverlay ? 4 + localX : 4 + localX, isOverlay ? 36 + localY : 20 + localY);
            }
        }

        private Color GetEditorPixel(int localX, int localY, bool isOverlay)
        {
            var (tx, ty) = GetMappedCoordinates(localX, localY, isOverlay);
            if (_selectedEditor == "Cape")
            {
                return _capePixels[tx, ty];
            }
            return isOverlay ? _skinOverlayPixels[tx, ty] : _skinBasePixels[tx, ty];
        }

        private void SetEditorPixel(int localX, int localY, bool isOverlay, Color color)
        {
            var (tx, ty) = GetMappedCoordinates(localX, localY, isOverlay);
            if (_selectedEditor == "Cape")
            {
                _capePixels[tx, ty] = color;
            }
            else
            {
                if (isOverlay)
                    _skinOverlayPixels[tx, ty] = color;
                else
                    _skinBasePixels[tx, ty] = color;
            }
        }

        private void ClearEditorCanvas_Click(object? sender, RoutedEventArgs e)
        {
            bool isOverlay = RadioOverlayLayer.IsChecked ?? false;
            for (int y = 0; y < _editorHeight; y++)
            {
                for (int x = 0; x < _editorWidth; x++)
                {
                    SetEditorPixel(x, y, isOverlay, Colors.Transparent);
                }
            }
            DrawPixelGrid();
        }

        private void SaveEditorDrawing_Click(object? sender, RoutedEventArgs e)
        {
            string username = SelectedAccountText.Text ?? "";
            if (string.IsNullOrEmpty(username) || username == "No Account Selected")
            {
                if (_selectedEditor == "Cape")
                {
                    string path = Path.Combine(@"C:\The Lads Client", "config", "cape.png");
                    Directory.CreateDirectory(Path.GetDirectoryName(path)!);
                    SaveSkinPng(path, true);
                    Log("[Cape] Saved custom cape to config.");
                }
                else
                {
                    string path = Path.Combine(@"C:\The Lads Client", "skin.png");
                    SaveSkinPng(path, false);
                    Log("[Skin] Saved custom skin.");
                }
                return;
            }

            if (_selectedEditor == "Cape")
            {
                string path = Path.Combine(@"C:\The Lads Client", "config", "cape.png");
                Directory.CreateDirectory(Path.GetDirectoryName(path)!);
                SaveSkinPng(path, true);
                Log($"[Cape] Saved custom cape for {username}.");
            }
            else
            {
                string path = Path.Combine(@"C:\The Lads Client", "skin.png");
                SaveSkinPng(path, false);
                Log($"[Skin] Saved custom skin for {username}.");
                _ = LoadPlayerSkin(username);
            }
        }

        private void LoadPixelsFromSkinFile(string filePath, bool isCape = false)
        {
            if (isCape)
            {
                Array.Clear(_capePixels, 0, _capePixels.Length);
            }
            else
            {
                Array.Clear(_skinBasePixels, 0, _skinBasePixels.Length);
                Array.Clear(_skinOverlayPixels, 0, _skinOverlayPixels.Length);
            }

            if (!File.Exists(filePath)) return;
            try
            {
                using (var stream = File.OpenRead(filePath))
                {
                    using (var writeable = WriteableBitmap.Decode(stream))
                    {
                        var w = (int)writeable.PixelSize.Width;
                        var h = (int)writeable.PixelSize.Height;
                        if (w == 64 && (h == 64 || h == 32))
                        {
                            using (var buf = writeable.Lock())
                            {
                                int length = buf.RowBytes * h;
                                byte[] raw = new byte[length];
                                Marshal.Copy(buf.Address, raw, 0, length);
                                bool isRgba = buf.Format == Avalonia.Platform.PixelFormat.Rgba8888;
                                
                                for (int y = 0; y < h; y++)
                                {
                                    for (int x = 0; x < w; x++)
                                    {
                                        int idx = y * buf.RowBytes + x * 4;
                                        byte b, g, r, a;
                                        if (isRgba)
                                        {
                                            r = raw[idx];
                                            g = raw[idx + 1];
                                            b = raw[idx + 2];
                                            a = raw[idx + 3];
                                        }
                                        else
                                        {
                                            b = raw[idx];
                                            g = raw[idx + 1];
                                            r = raw[idx + 2];
                                            a = raw[idx + 3];
                                        }
                                        var color = Color.FromArgb(a, r, g, b);
                                        
                                        if (isCape)
                                        {
                                            _capePixels[x, y] = color;
                                        }
                                        else
                                        {
                                            if (IsOverlayRegion(x, y))
                                            {
                                                _skinOverlayPixels[x, y] = color;
                                            }
                                            else
                                            {
                                                _skinBasePixels[x, y] = color;
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            catch { }
        }

        private bool IsOverlayRegion(int x, int y)
        {
            if (y < 16 && x >= 32) return true;
            if (y >= 32 && y < 48 && x < 56) return true;
            if (y >= 48 && (x < 16 || x >= 48)) return true;
            return false;
        }

        private void SaveSkinPng(string filePath, bool isCape)
        {
            int w = 64;
            int h = isCape ? 32 : 64;
            
            var bitmap = new WriteableBitmap(new PixelSize(w, h), new Vector(96, 96), Avalonia.Platform.PixelFormat.Bgra8888, Avalonia.Platform.AlphaFormat.Premul);
            using (var buf = bitmap.Lock())
            {
                int size = w * h * 4;
                byte[] raw = new byte[size];
                for (int y = 0; y < h; y++)
                {
                    for (int x = 0; x < w; x++)
                    {
                        Color c = isCape ? _capePixels[x, y] : (IsOverlayRegion(x, y) ? _skinOverlayPixels[x, y] : _skinBasePixels[x, y]);
                        
                        int idx = (y * w + x) * 4;
                        raw[idx] = c.B;
                        raw[idx + 1] = c.G;
                        raw[idx + 2] = c.R;
                        raw[idx + 3] = c.A;
                    }
                }
                Marshal.Copy(raw, 0, buf.Address, raw.Length);
            }
            bitmap.Save(filePath);
        }
    }
}
