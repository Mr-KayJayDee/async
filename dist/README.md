# `:dist`

No source code. Aggregates `:core + :ecs + :binding` into one shaded JAR via
`com.gradleup.shadow`.

```bash
./gradlew :dist:shadowJar
# → dist/build/libs/async-<version>.jar
```

Drop that JAR in your Hytale server's `mods/` directory. That's the only
artifact a consumer ever needs — the per-module split exists for testability
and codebase hygiene, not deployment cherry-picking.
