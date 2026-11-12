# The engine reads its start-up preferences file (see OaviEngine.writeEnginePrefs) with
# SnakeYAML. SnakeYAML looks up its own package name and fills the engine's config class by
# reflection, so neither may be renamed or stripped.
-keep class org.yaml.snakeyaml.** { *; }
-keep class org.mozilla.gecko.util.DebugConfig { *; }
-keep class org.mozilla.gecko.util.DebugConfig$* { *; }
-dontwarn org.yaml.snakeyaml.**
