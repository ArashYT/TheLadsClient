using System;

class Program {
    static void Main() {
        bool b1 = Version.TryParse("0.14.8", out var v1);
        bool b2 = Version.TryParse("0.14.9", out var v2);
        Console.WriteLine($"b1={b1} v1={v1} b2={b2} v2={v2} v2>v1={v2>v1}");
    }
}
