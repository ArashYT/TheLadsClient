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

    [JsonPropertyName("accountType")]
    public string AccountType { get; set; } = "offline";

    [JsonPropertyName("selected")]
    public bool Selected { get; set; } = false;
}
