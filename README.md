# Preferenzr

**Preferenzr** is a GUI tool to browse and edit the preferences of a Tentackle application.

Tentackle applications store their preferences in the database rather than in the local
`java.util.prefs` store (see `org.tentackle.dbms.prefs`, tables `prefnode` and `prefkey`).
Preferenzr connects to such a database and lets you work with those trees directly.

See the [user manual](src/main/docs/manual.md) for a description of all features and how to use them.

## Features

- **Browse** the system preferences and the preferences of every user in a single tree.
- **Edit** values in the editor pane on the right. Changes are highlighted and only written when
  you press *save*, so *discard changes* always gets you back to the stored state.
- **Add and remove** nodes and keys via the context menu of the tree. Removing a node marks its
  whole subtree for deletion.
- **Dump** a node and everything below it to a file, and **load** such a file back into any user
  or into the system preferences, either merging it or replacing the dumped subtree.
- **Filter** (F2) and **find** (F3 / Shift-F3) across node names, key names and values.

Dumps use the standard XML format of `java.util.prefs`, so they can also be read by
`Preferences.importPreferences()` and files written by `Preferences.exportSubtree()` can be loaded
into Preferenzr.

## Installation
Preferenzr is usually installed by downloading and unpacking the platform specific jlink ZIP-archive
of the latest release:

- [Linux x64](https://github.com/krake-com/preferenzr/releases/latest/download/preferenzr-linux-x64.zip)
- [Linux ARM64](https://github.com/krake-com/preferenzr/releases/latest/download/preferenzr-linux-aarch64.zip)
- [Windows x64](https://github.com/krake-com/preferenzr/releases/latest/download/preferenzr-windows-x64.zip)
- [macOS ARM64](https://github.com/krake-com/preferenzr/releases/latest/download/preferenzr-macos-aarch64.zip)

Older versions are available on the [releases page](https://github.com/krake-com/preferenzr/releases).

It is invoked via a start script found in the **bin** folder (**preferenzr.sh** or **preferenzr.cmd**,
depending on the platform). The database is configured in **conf/backend.properties**.

## Running

Preferenzr is a non-typical Tentackle application: a single connection, usually directly via JDBC,
no modification tracker, no caching and no security manager. It only touches the tables `prefnode`,
`prefkey`, `modification` and `object_sequence_id`, so it can run as a user with limited privileges.
Because it must not store its own settings in the database it is editing, its own preferences go to
the local `java.util.prefs` store under `Preferenzr` (see `PreferenzrPreferencesFactory`).

Build and start a self-contained image:

```
mvn -Pjlink clean install
jlink/target/jlink/bin/preferenzr.sh
```

The database is configured in `jlink/target/jlink/conf/backend.properties`. During development,
`RunPreferenzr` in `preferenzr-client/src/test` starts the client from the IDE using
`preferenzr-client/src/test/filtered-resources/backend.properties`.

## Modules

| Module | Content |
|---|---|
| `preferenzr-common` | the preference model (`PreferenceEntry`, `PreferenceValue`), `PreferencesRepository` and the XML dump |
| `preferenzr-gui` | the JavaFX controllers, FXML, resource bundles and stylesheets |
| `preferenzr-client` | the application class, login and logging configuration |
| `jlink` | the jlink image and its launcher scripts (profile `jlink`) |
