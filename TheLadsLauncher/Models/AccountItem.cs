using System.Text.Json.Serialization;

namespace TheLadsLauncher.Models;

public class AccountItem
{
    [JsonPropertyName("uuid")]
    public string Uuid { get; set; } = "";

    [JsonPropertyName("username")]
    public string Username { get; set; } = "";

    // Never read from or written to disk: launchers before 1.2.1 saved live tokens here in plain text, and the next save drops them.
    [JsonIgnore]
    public string AccessToken { get; set; } = "";

    [JsonPropertyName("type")]
    public string AccountType { get; set; } = "offline";

    // Read older launcher files without turning Microsoft entries into offline accounts.
    [JsonPropertyName("accountType")]
    [JsonIgnore(Condition = JsonIgnoreCondition.WhenWritingNull)]
    public string? LegacyAccountType { get => null; set { if (!string.IsNullOrWhiteSpace(value)) AccountType = value; } }

    [JsonPropertyName("selected")]
    public bool Selected { get; set; } = false;

    public static implicit operator Services.AccountSummary(AccountItem item) =>
        new(item.Username, item.Uuid, item.AccountType, item.Selected);
}
