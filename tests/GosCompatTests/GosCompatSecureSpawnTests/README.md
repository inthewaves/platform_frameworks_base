# GosCompatSecureSpawnTests

This module verifies the app process startup path selected by secure app spawning and by app
compatibility flags that require exec spawning.

`nativeServiceTerminatesOnSigterm` directly verifies native application signal cleanup. The
inherited host test runs once with exec spawning forced on for the helper package and once with it
forced off, using the persistent native zygote case as the control.

`webViewRendererProcessGroupIsRemoved` terminates a WebView renderer with `chrome://kill` and
verifies that its per-process cgroup is removed. The enabled case covers a renderer created through
the primary zygote, while the disabled case covers the managed WebView zygote path.

The package under test is the non-debuggable `GosCompatSecureSpawnApp`, not `GosCompatCheckApp`. 
`GosCompatCheckApp` is intentionally debuggable because the existing GosCompat modules use 
`run-as app.grapheneos.goscompat.checks` to read result files from its app data directory. Making
that shared helper non-debuggable would require reworking those result collection paths or splitting
the existing modules first, which is more churn than this regression test needs.

Run the module directly with:

```sh
atest GosCompatSecureSpawnTests
```
