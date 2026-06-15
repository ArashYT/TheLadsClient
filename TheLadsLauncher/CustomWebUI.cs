using System;
using System.Net;
using System.Threading;
using System.Threading.Tasks;
using XboxAuthNet.OAuth.CodeFlow;

namespace TheLadsLauncher
{
    public class CustomWebUI : IWebUI
    {
        public async Task<XboxAuthNet.OAuth.CodeFlow.CodeFlowAuthorizationResult> DisplayDialogAndInterceptUri(Uri uri, ICodeFlowUrlChecker checker, CancellationToken cancellationToken)
        {
            Console.WriteLine("!!! OAUTH URL (INTERCEPT) !!! " + uri.ToString());
            
            while (!System.IO.File.Exists("auth_result.txt"))
            {
                await Task.Delay(1000);
            }
            var code = System.IO.File.ReadAllText("auth_result.txt");
            System.IO.File.Delete("auth_result.txt");
            
            var result = new XboxAuthNet.OAuth.CodeFlow.CodeFlowAuthorizationResult();
            result.Code = code;
            return result;
        }

        public Task DisplayDialogAndNavigateUri(Uri uri, CancellationToken cancellationToken)
        {
            Console.WriteLine("!!! OAUTH URL (NAVIGATE) !!! " + uri.ToString());
            return Task.Delay(-1); // Block forever, let XboxAuthNet server handle it
        }
    }
}
