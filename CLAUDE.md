# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

Preferenzr is a JavaFX tool built on the Tentackle framework that browses and edits the preferences
of *another* Tentackle application. Those preferences live in the target application's database
(`org.tentackle.dbms.prefs`, tables `prefnode` / `prefkey`), not in `java.util.prefs`.
See `README.md` for the user-facing feature list.

Java 25, Maven multi-module, JPMS (every module has a `module-info.java`), TestNG.

## Build and run

```bash
mvn clean install                       # build all three library modules
mvn -Pjlink clean install               # additionally build the self-contained image
jlink/target/jlink/bin/preferenzr.sh    # start it (DB config: jlink/target/jlink/conf/backend.properties)

mvn test                                        # all tests
mvn -pl preferenzr-common test -Dtest=PreferencesDumpTest    # one test class
mvn -pl preferenzr-common test -Dtest=PreferencesDumpTest#testReadJdkExport   # one method
```

Deploying (`distributionManagement`) needs the `krake` profile from `~/.m2/settings.xml`
(`mvn -Pkrake ...`), which supplies `repo.id`, `site.id` etc. Plain `install` does not.

### Running the GUI during development

`preferenzr-client/src/test/java/.../RunPreferenzr.java` starts the client from the IDE or via
`mvn -pl preferenzr-client test -Dtest=RunPreferenzr`. Its name deliberately does not match
surefire's default includes, so a normal build never launches the GUI. It connects to whatever
`preferenzr-client/src/test/filtered-resources/backend.properties` points at — that must be an
**existing** Tentackle application database; Preferenzr does not create the schema.
`RunPreferenzr#runPreferenzrDE` starts it with `--locale=de_DE`.

## Modules and dependency direction

`preferenzr-common` ← `preferenzr-gui` ← `preferenzr-client` ← `jlink` (profile `jlink` only).

- **common** — the domain model and everything database-related.
- **gui** — JavaFX controllers, FXML, bundles, CSS. Knows only the model DTOs.
- **client** — `PreferenzrFxClient` (the `Application`), login, logging config.
- **jlink** — image/launcher (`packaging` can be switched to `jpackage` for an installer).

## Architecture

### The in-memory model is the whole design

`PreferencesRepository` (common) is the only class that touches persistence. It reads *all*
`DbPreferencesNode` / `DbPreferencesKey` rows **low-level, bypassing `DbPreferencesFactory`** —
necessary because Preferenzr must see every user's tree, not just the session user's, and needs
full control over deletion. It converts them into the DTOs `PreferenceEntry` (a node) and
`PreferenceValue` (a key), both implementing `PreferenceNode`. Everything above `common` sees only
those DTOs.

Edits mutate the DTO tree, never the database. Each DTO carries a `PreferenceState`
(`UNCHANGED` / `NEW` / `MODIFIED` / `REMOVED`) and a `modificationCount` that bubbles up to the
repository through `updateModificationCount(int diff)`; the GUI renders state as a CSS row style
(`preference-new` / `-modified` / `-removed` in `MainController.css`) and the count in the status
label. "Discard changes" is simply `invalidate()` + reload. `persist()` writes everything in one
transaction in a fixed order that respects the FK-less parent/child structure:
delete keys → delete nodes (children first) → save nodes (parents first) → save keys.

Deletion is two-phase: `remove()` on a never-persisted (`NEW`) object drops it from the map
outright, otherwise it flips the state to `REMOVED` and `revive()` can undo it.

Because the database has no referential integrity for these tables, `buildTrees()` treats nodes and
keys whose parent is missing as **orphans** and reports them via `getOrphans()`.

### The application is a deliberately atypical Tentackle client

`PreferenzrFxClient` disables the modification tracker, caching and the security manager, and has
no user PDOs — the credentials entered at login *are* the database credentials (hence
`PreferenzrLogin.presetUserAndPassword()`). It only ever touches `prefnode`, `prefkey`,
`modification` and `object_sequence_id`, so it can run with limited DB privileges. Preserve these
properties when changing the client.

Critically, Preferenzr must **not** store its own settings in the database it is editing:
`PreferenzrPreferencesFactory` (a `@Service(PersistedPreferencesFactory.class)`) redirects the
application's own preferences to the local `java.util.prefs` store under `Preferenzr`.
Application settings go through `PreferenzrPreferences`.

### Dumps

`PreferencesDump` reads/writes the standard `java.util.prefs` XML format, so dumps interoperate with
`Preferences.importPreferences()` / `exportSubtree()`. A dump always starts at the tree root;
`DumpedTree.getBasePath()` walks back down the chain of childless ancestors to find what was actually
dumped. `PreferencesRepository.importDump()` applies it to the in-memory model only (`MERGE` or
`REPLACE`) — the user still has to save.

## Conventions and gotchas

- **GUI resources live next to the Java source, not in `src/main/resources`.** For each controller
  there is one package holding `Foo.java`, `Foo.fxml`, `Foo.properties`, `Foo_de.properties` and
  optionally `Foo.css` / `Foo-dark.css`. `preferenzr-gui/pom.xml` adds `src/main/java` as a resource
  directory for those extensions. This is a SceneBuilder workaround — keep it.
- **Every user-visible string is a bundle key in both `en` and `de`.** FXML uses `%key`, code uses
  `resources.getString(...)` or `CommonBundle`/`GuiBundle`. `tentackle-check-maven-plugin`'s
  `bundles` goal fails the build for missing keys in `en_US, de_DE`, so add both when adding a string.
- `@Bindable` getters/setters are what the Tentackle FX binding uses; `@FxControllerService`,
  `@TableConfigurationProviderService`, `@Service` are annotation-processed into
  `target/generated-resources/services` and picked up as JPMS/`META-INF/services` providers —
  adding a controller or service means the module may need a matching `provides`/`exports`.
- Each module provides a `ModuleHook` (`.../service/Hook.java`) so Tentackle can load resource
  bundles across module boundaries.
- Tests: `AbstractRepositoryTest` builds a repository from hand-made persistent objects with a
  `null` `DomainContext` (detached, cannot persist) — use it for model logic.
  `PreferencesPersistenceTest` creates the `prefnode`/`prefkey` schema in an in-memory H2 database
  for the load/save path. The gui module's `TestControllers` / `TestGuiProviders` /
  `TestTableConfigurationProviders` are Tentackle harnesses that instantiate every controller and
  provider — they catch broken FXML and missing bundle keys.
- Version and build timestamp come from `Version.properties` filtering `${pom.version}` /
  `${timestamp}`, so `Version.RELEASE` only reflects a filtered-resources build.
