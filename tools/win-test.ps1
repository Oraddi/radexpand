param(
    [string]$Exe = (Join-Path $PSScriptRoot '..\desktop\build\compose\binaries\main\app\RadExpand\RadExpand.exe'),
    [int]$KeyDelayMs = 15
)
# Windows RadExpand uctan uca testi.
# Kendi test penceresini (WinForms TextBox) acar ve gercek tus olaylariyla (SendInput) yazar.
# GUVENLIK: Her tustan once on plandaki pencere kontrol edilir; test penceresi degilse test DURUR
# (baska bir programa tek harf gitmez).
$ErrorActionPreference = 'Stop'
# Baska bir genisletici (Espanso, eski MedExpand, kurulu RadExpand) calisiyorsa test anlamsiz olur: iki kez genisler.
$others = Get-Process -ErrorAction SilentlyContinue | Where-Object { $_.ProcessName -match '^(espanso|espansod|MedExpand|RadExpand)$' }
if ($others) { "TEST BASLATILMADI: baska bir metin genisletici calisiyor: $(($others.ProcessName | Sort-Object -Unique) -join ', ')"; exit 2 }
$home2 = Join-Path $env:TEMP 'radexpand-wintest'
Remove-Item $home2 -Recurse -Force -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force $home2 | Out-Null

# --- Test kisayollari (Android test ekranindakilerle ayni)
function S($t, $r, $mode = 'MANUAL', $wb = $true) { [ordered]@{ id = [guid]::NewGuid().ToString(); trigger = $t; replacement = $r; mode = $mode; wordBoundary = $wb; propagateCase = $true; enabled = $true; category = 'Test'; updatedAt = 1; deleted = $false; note = '' } }
$data = [ordered]@{
    format = 'radexpand'; version = 1
    shortcuts = @(
        (S 'ri' 'hiperintensite'), (S 't2r' 'T2 hiperintens'), (S 'iz' 'izlendi.'), (S 'izş' 'izlenmiştir.'), (S 'ç' 'çapında'),
        (S 'rtü' "Raporda tanımlı bulgular`n`nDr. Ayşe ÖZTÜRK"), (S 'bel' 'belirginleşme' 'SPECIAL_ONLY'),
        (S 'drimza' 'Dr. Ayşe ÖZTÜRK' 'AUTO'), (S 'ivd' 'intervertebral disk' 'AUTO' $false),
        (S 'kcc' 'Karaciğer kraniokaudal boyutu $|$ mm ölçülmüştür.')
    )
    settings = [ordered]@{ unknownStartIsBoundary = $true }
}
[System.IO.File]::WriteAllText((Join-Path $home2 'radexpand.json'), ($data | ConvertTo-Json -Depth 6), (New-Object System.Text.UTF8Encoding($false)))

Add-Type -ReferencedAssemblies System.Windows.Forms, System.Drawing -TypeDefinition @'
using System;
using System.Collections.Generic;
using System.Runtime.InteropServices;
using System.Text;
using System.Threading;
using System.Windows.Forms;

public class WinHarness {
    [DllImport("user32.dll")] static extern uint SendInput(uint n, INPUT[] inputs, int size);
    [DllImport("user32.dll")] static extern IntPtr GetForegroundWindow();
    [DllImport("user32.dll")] static extern bool SetForegroundWindow(IntPtr h);
    [DllImport("user32.dll")] static extern IntPtr GetKeyboardLayout(uint id);
    [DllImport("user32.dll", CharSet = CharSet.Unicode)] static extern int ToUnicodeEx(uint vk, uint scan, byte[] state, [Out, MarshalAs(UnmanagedType.LPArray)] char[] buf, int cch, uint flags, IntPtr hkl);
    [StructLayout(LayoutKind.Sequential)] struct KEYBDINPUT { public ushort wVk; public ushort wScan; public uint dwFlags; public uint time; public IntPtr dwExtraInfo; }
    [StructLayout(LayoutKind.Sequential)] struct MOUSEINPUT { public int dx; public int dy; public uint mouseData; public uint dwFlags; public uint time; public IntPtr dwExtraInfo; }
    [StructLayout(LayoutKind.Explicit)] struct UNION { [FieldOffset(0)] public MOUSEINPUT mi; [FieldOffset(0)] public KEYBDINPUT ki; }
    [StructLayout(LayoutKind.Sequential)] struct INPUT { public uint type; public UNION u; }

    public static Form form;
    public static TextBox box;
    static Dictionary<char, int[]> map = new Dictionary<char, int[]>();
    public static List<string> results = new List<string>();
    public static bool aborted = false;

    static void BuildMap() {
        IntPtr hkl = GetKeyboardLayout(0);
        for (int shift = 0; shift < 2; shift++) {
            for (uint vk = 0x20; vk < 0xFF; vk++) {
                if (vk >= 0x60 && vk <= 0x6F) continue; // sayi tus takimi
                byte[] st = new byte[256];
                if (shift == 1) st[0x10] = 0x80;
                char[] sb = new char[8];
                int n = ToUnicodeEx(vk, 0, st, sb, 8, 4, hkl);
                if (n == 1 && sb[0] != (char)0 && !map.ContainsKey(sb[0])) map[sb[0]] = new int[] { (int)vk, shift };
            }
        }
    }

    static INPUT Key(ushort vk, bool up) {
        INPUT i = new INPUT(); i.type = 1; i.u.ki.wVk = vk; i.u.ki.dwFlags = up ? 2u : 0u; return i;
    }

    static bool Fg() { return GetForegroundWindow() == form.Handle; }

    static void Send(List<INPUT> evs) {
        INPUT[] arr = evs.ToArray();
        SendInput((uint)arr.Length, arr, Marshal.SizeOf(typeof(INPUT)));
    }

    static List<INPUT> Events(string seq) {
        List<INPUT> evs = new List<INPUT>();
        foreach (char c in seq) {
            ushort vk = 0; bool shift = false;
            if (c == '␣') vk = 0x20;       // ␣
            else if (c == '⏎') vk = 0x0D;  // ⏎
            else if (c == '⇥') vk = 0x09;  // ⇥
            else if (c == '⌫') vk = 0x08;  // ⌫
            else {
                int[] m;
                if (!map.TryGetValue(c, out m)) throw new Exception("Klavye duzeninde yok: " + c);
                vk = (ushort)m[0]; shift = m[1] == 1;
            }
            if (shift) evs.Add(Key(0x10, false));
            evs.Add(Key(vk, false)); evs.Add(Key(vk, true));
            if (shift) evs.Add(Key(0x10, true));
        }
        return evs;
    }

    static void TypeSeq(string seq, int delay) {
        List<INPUT> evs = Events(seq);
        if (delay <= 0) { if (!Fg()) { aborted = true; return; } Send(evs); return; }
        // Her tus (asagi+yukari, shift dahil) icin on plan kontrolu
        List<INPUT> chunk = new List<INPUT>();
        foreach (INPUT e in evs) {
            chunk.Add(e);
            if (e.u.ki.dwFlags == 2u && e.u.ki.wVk != 0x10) {
                if (!Fg()) { aborted = true; return; }
                Send(chunk); chunk.Clear(); Thread.Sleep(delay);
            }
        }
        if (chunk.Count > 0 && Fg()) Send(chunk);
    }

    public static void Run(string[] names, string[] seqs, string[] exps, bool[] burst, int delay) {
        BuildMap();
        form = new Form(); form.Text = "RadExpand test penceresi"; form.TopMost = true; form.Width = 900; form.Height = 400;
        box = new TextBox(); box.Multiline = true; box.AcceptsTab = true; box.AcceptsReturn = true; box.Dock = DockStyle.Fill;
        box.Font = new System.Drawing.Font("Segoe UI", 14);
        form.Controls.Add(box);
        form.Shown += delegate {
            Thread t = new Thread(delegate() {
                Thread.Sleep(800);
                for (int k = 0; k < 10 && !Fg(); k++) {
                    form.Invoke((MethodInvoker)delegate { form.Activate(); box.Focus(); SetForegroundWindow(form.Handle); });
                    Thread.Sleep(300);
                }
                if (!Fg()) { aborted = true; results.Add("ODAK ALINAMADI: test baslatilmadi"); form.Invoke((MethodInvoker)delegate { form.Close(); }); return; }
                for (int i = 0; i < names.Length && !aborted; i++) {
                    form.Invoke((MethodInvoker)delegate { box.Clear(); box.Focus(); });
                    Thread.Sleep(400);
                    // Kutu programla temizlendi; RadExpand bunu goremez. Gercek kullanici gibi once Home tusuna bas.
                    if (Fg()) { List<INPUT> h = new List<INPUT>(); h.Add(Key(0x24, false)); h.Add(Key(0x24, true)); Send(h); Thread.Sleep(150); }
                    TypeSeq(seqs[i], burst[i] ? 0 : delay);
                    Thread.Sleep(1200);
                    string got = (string)form.Invoke((Func<string>)delegate { return box.Text; });
                    got = got.Replace("\r\n", "\n");
                    string vis = got.Replace(" ", "·").Replace("\n", "⏎").Replace("\t", "⇥");
                    results.Add((aborted ? "DURDU " : (got == exps[i] ? "GECTI " : "KALDI ")) + names[i] + "  =>  " + vis);
                }
                if (aborted) results.Add("TEST DURDURULDU: odak test penceresinden cikti");
                form.Invoke((MethodInvoker)delegate { form.Close(); });
            });
            t.IsBackground = true; t.Start();
        };
        Application.Run(form);
    }
}
'@

# --- RadExpand'i test klasoruyle baslat
$env:RADEXPAND_HOME = $home2
$p = Start-Process -FilePath $Exe -ArgumentList '--minimized' -PassThru
Start-Sleep -Seconds 6
if ($p.HasExited) { "RadExpand basladiktan sonra kapandi (kod $($p.ExitCode))"; exit 1 }

$tests = @(
    [pscustomobject]@{ n = 'ri (tek basina)'; s = 'ri'; e = 'ri'; b = $false },
    [pscustomobject]@{ n = 'ri + Space'; s = 'ri␣'; e = 'hiperintensite '; b = $false },
    [pscustomobject]@{ n = 'ri + Enter'; s = 'ri⏎'; e = "hiperintensite`n"; b = $false },
    [pscustomobject]@{ n = 'ri + Tab'; s = 'ri⇥'; e = 'hiperintensite'; b = $false },
    [pscustomobject]@{ n = 'ri + Tab + ler'; s = 'ri⇥ler'; e = 'hiperintensiteler'; b = $false },
    [pscustomobject]@{ n = 'birinci + Space'; s = 'birinci␣'; e = 'birinci '; b = $false },
    [pscustomobject]@{ n = 't2r + Space'; s = 't2r␣'; e = 'T2 hiperintens '; b = $false },
    [pscustomobject]@{ n = 'drimza (otomatik)'; s = 'drimza'; e = 'Dr. Ayşe ÖZTÜRK'; b = $false },
    [pscustomobject]@{ n = 'izş + Space'; s = 'izş␣'; e = 'izlenmiştir. '; b = $false },
    [pscustomobject]@{ n = 'ç + Space'; s = 'ç␣'; e = 'çapında '; b = $false },
    [pscustomobject]@{ n = 'aç + Space'; s = 'aç␣'; e = 'aç '; b = $false },
    [pscustomobject]@{ n = 'bel + Space'; s = 'bel␣'; e = 'bel '; b = $false },
    [pscustomobject]@{ n = 'bel + Tab'; s = 'bel⇥'; e = 'belirginleşme'; b = $false },
    [pscustomobject]@{ n = 'ri + Space + Backspace'; s = 'ri␣⌫'; e = 'ri '; b = $false },
    [pscustomobject]@{ n = 'rix + Space'; s = 'rix␣'; e = 'rix '; b = $false },
    [pscustomobject]@{ n = 'Ri + Space'; s = 'Ri␣'; e = 'Hiperintensite '; b = $false },
    [pscustomobject]@{ n = 'kcc + Space + 152'; s = 'kcc␣152'; e = 'Karaciğer kraniokaudal boyutu 152 mm ölçülmüştür.'; b = $false },
    [pscustomobject]@{ n = 'rtü + Space'; s = 'rtü␣'; e = "Raporda tanımlı bulgular`n`nDr. Ayşe ÖZTÜRK "; b = $false },
    [pscustomobject]@{ n = 'xivd (otomatik, sınır kapalı)'; s = 'xivd'; e = 'xintervertebral disk'; b = $false },
    [pscustomobject]@{ n = 'HIZLI: ri␣ x5 tek seferde'; s = 'ri␣ri␣ri␣ri␣ri␣'; e = ('hiperintensite ' * 5); b = $true },
    [pscustomobject]@{ n = 'HIZLI: cümle tek seferde'; s = 'bir ri␣odak t2r⇥ler izş␣son'; e = 'bir hiperintensite odak T2 hiperintensler izlenmiştir. son'; b = $true }
)
$names = [string[]]($tests | ForEach-Object { $_.n }); $seqs = [string[]]($tests | ForEach-Object { $_.s })
$exps = [string[]]($tests | ForEach-Object { $_.e }); $burst = [bool[]]($tests | ForEach-Object { $_.b })
try {
    [WinHarness]::Run($names, $seqs, $exps, $burst, $KeyDelayMs)
} finally {
    # RadExpand.exe asil uygulamayi alt surecte calistirir: tum agaci kapat (yoksa kanca acik kalir)
    & taskkill.exe /T /F /PID $p.Id 2>&1 | Out-Null
}
[WinHarness]::results
"Gecen: $(@([WinHarness]::results | Where-Object { $_ -like 'GECTI*' }).Count) / $($tests.Count)"
