using System;
using System.Collections.Generic;
using System.Text.RegularExpressions;

class Program {
    static void Main() {
        var paths = new List<string> {
            @"C:\Program Files\Java\jdk-17\bin\java.exe",
            @"C:\Program Files\Java\jdk-21\bin\java.exe",
            @"C:\Program Files\Eclipse Adoptium\jdk-25.0.3\bin\java.exe",
            @"C:\Program Files\Java\jdk1.8.0_202\bin\java.exe"
        };
        
        paths.Sort((a, b) => 
        {
            int GetVer(string path)
            {
                var match = Regex.Match(path, @"(?:jdk|jre)-?(\d+)");
                if (match.Success && int.TryParse(match.Groups[1].Value, out int v)) return v;
                match = Regex.Match(path, @"1\.(\d+)\.");
                if (match.Success && int.TryParse(match.Groups[1].Value, out int v2)) return v2;
                return 0;
            }
            return GetVer(b).CompareTo(GetVer(a));
        });

        foreach(var p in paths) Console.WriteLine(p);
    }
}
