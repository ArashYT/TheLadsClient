using System.Text.Json.Serialization;

namespace TheLadsLauncher.Models;

public class AccountItem
{
    [JsonPropertyName("uuid")]
    public string Uuid { get; set; } = "";

    [JsonPropertyName("username")]
    public string Username { get; set; } = "";

    [JsonPropertyName("accessToken")]
    public string AccessToken { get; set; } = "";

    [JsonPropertyName("type")]
    public string AccountType { get; set; } = "offline";

    // Read older launcher files without turning Microsoft entries into offline accounts.
    [JsonPropertyName("accountType")]
    [JsonIgnore(Condition = JsonIgnoreCondition.WhenWritingNull)]
    public string? LegacyAccountType { get => null; set { if (!string.IsNullOrWhiteSpace(value)) AccountType = value; } }

    [JsonPropertyName("selected")]
    public bool Selected { get; set; } = false;
}
