/*
 * Tentackle - https://tentackle.org
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */

package org.tentackle.preferenzr.common;

import org.tentackle.app.Application;
import org.tentackle.dbms.Db;
import org.tentackle.dbms.prefs.DbPreferencesKey;
import org.tentackle.dbms.prefs.DbPreferencesNode;
import org.tentackle.log.Logger;
import org.tentackle.pdo.DomainContext;
import org.tentackle.pdo.DomainContextProvider;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;

/**
 * The preference repository encapsulates the {@link DbPreferencesNode} and {@link DbPreferencesKey} persistence.<br>
 * It maps the persistent objects to the internal DTOs {@link PreferenceEntry} and {@link PreferenceValue}.
 * This allows the other modules to refer to the DTOs only to avoid dependencies on the persistence layer.
 * <p>
 * The persistent objects are read and written low-level, i.e. bypassing the {@code DbPreferencesFactory}.
 * This is necessary because Preferenzr must browse and edit the preferences of <em>all</em> users, not just
 * those of the session's user, and because it needs full control over deletions.
 */
public class PreferencesRepository implements DomainContextProvider {

  private static final Logger LOGGER = Logger.get(PreferencesRepository.class);

  /** the path of the root node of a preferences tree. */
  public static final String ROOT_PATH = "/";

  private final DomainContext context;                          // domain context to load from storage
  private final List<PreferencesUpdatedListener> listeners;     // preferences modification listener
  private final List<PreferenceEntry> roots;                    // the loaded trees, system first
  private final List<String> orphans;                           // description of orphan nodes and keys, if any
  private boolean invalid;                                      // true if repo is outdated and needs to be reloaded
  private int modificationCount;                                // number of modified preferences

  public PreferencesRepository() {
    this(Application.getInstance().getDomainContext());
  }

  /**
   * Creates a repository for a given domain context.
   *
   * @param context the domain context. If null, the repository is detached: the trees must be provided
   *        by {@link #buildTrees} and cannot be persisted. Used by the unit tests.
   */
  PreferencesRepository(DomainContext context) {
    this.context = context;
    listeners = new ArrayList<>();
    roots = new ArrayList<>();
    orphans = new ArrayList<>();
    invalid = true;
  }

  @Override
  public DomainContext getDomainContext() {
    return context;
  }

  /**
   * Gets the loaded preference trees.<br>
   * The system preferences come first, followed by the users in alphabetical order.
   *
   * @return the root entries
   */
  public Collection<? extends PreferenceNode> getPreferences() {
    assertLoaded();
    return roots;
  }

  /**
   * Gets the root entry of a scope.
   *
   * @param user the username, null for the system preferences
   * @return the root entry, null if there are no preferences for that scope
   */
  public PreferenceEntry getRoot(String user) {
    assertLoaded();
    for (PreferenceEntry root : roots) {
      if (user == null ? root.getUser() == null : user.equals(root.getUser())) {
        return root;
      }
    }
    return null;
  }

  /**
   * Gets the names of all users holding preferences.
   *
   * @return the usernames, alphabetically sorted
   */
  public List<String> getUsers() {
    assertLoaded();
    List<String> users = new ArrayList<>();
    for (PreferenceEntry root : roots) {
      if (root.getUser() != null) {
        users.add(root.getUser());
      }
    }
    return users;
  }

  /**
   * Gets the descriptions of the orphan nodes and keys detected during the last load.<br>
   * Orphans are nodes or keys referring to a parent or node that does not exist. Since there is no referential
   * integrity enforced by the database, this may happen if the preferences were modified manually via SQL.
   *
   * @return the descriptions, empty if all is well
   */
  public List<String> getOrphans() {
    assertLoaded();
    return orphans;
  }

  /**
   * Gets of modifications made so far.
   *
   * @return the modification count
   */
  public int getModificationCount() {
    assertLoaded();
    return modificationCount;
  }

  /**
   * Adds a preference update listener.
   *
   * @param listener the listener
   */
  public void addPreferencesUpdatedListener(PreferencesUpdatedListener listener) {
    listeners.add(listener);
  }

  /**
   * Removes a preference update listener.
   *
   * @param listener the listener
   */
  public void removePreferencesUpdatedListener(PreferencesUpdatedListener listener) {
    listeners.remove(listener);
  }

  /**
   * Updates the modification count.
   *
   * @param diff number of modifications to add
   */
  public void updateModificationCount(int diff) {
    modificationCount += diff;
    fireListeners(false);
  }

  /**
   * Invalidates the repository.
   */
  public synchronized void invalidate() {
    invalid = true;
  }

  /**
   * Creates the root entry of a scope that does not exist yet.
   *
   * @param user the username, null for the system preferences
   * @return the new root entry
   */
  public PreferenceEntry createRoot(String user) {
    assertLoaded();
    PreferenceEntry root = getRoot(user);
    if (root == null) {
      root = new PreferenceEntry(this, null, createPersistentNode(user, ROOT_PATH), PreferenceState.NEW);
      roots.add(root);
      sortRoots();
      root.updateModificationCount(1);
    }
    else {
      root.revive();
    }
    return root;
  }

  /**
   * Gets an entry by its scope and absolute path, creating the missing nodes along the way.
   *
   * @param user the username, null for the system preferences
   * @param path the absolute path
   * @return the entry, never null
   */
  public PreferenceEntry resolveEntry(String user, String path) {
    PreferenceEntry entry = createRoot(user);
    StringTokenizer stok = new StringTokenizer(path, ROOT_PATH);
    while (stok.hasMoreTokens()) {
      entry = entry.createChild(stok.nextToken());
    }
    return entry;
  }

  /**
   * Gets an entry by its scope and absolute path.
   *
   * @param user the username, null for the system preferences
   * @param path the absolute path
   * @return the entry, null if no such entry
   */
  public PreferenceEntry findEntry(String user, String path) {
    PreferenceEntry entry = getRoot(user);
    StringTokenizer stok = new StringTokenizer(path, ROOT_PATH);
    while (entry != null && stok.hasMoreTokens()) {
      entry = entry.getChild(stok.nextToken());
    }
    return entry;
  }

  /**
   * Applies a dumped tree to a scope of the model.<br>
   * The dump is applied to the in-memory model only, i.e. the modifications are shown to the user
   * and must be saved explicitly.
   *
   * @param tree the dumped tree
   * @param user the target username, null for the system preferences
   * @param mode whether to merge the dump or to replace the dumped subtree
   */
  public void importDump(PreferencesDump.DumpedTree tree, String user, PreferencesDump.MergeMode mode) {
    assertLoaded();
    if (mode == PreferencesDump.MergeMode.REPLACE) {
      PreferenceEntry base = findEntry(user, tree.getBasePath());
      if (base != null) {
        if (base.isRoot()) {
          // the root itself must survive: remove its contents only
          for (PreferenceEntry child : new ArrayList<>(base.getChildren().values())) {
            child.remove();
          }
          for (PreferenceValue key : new ArrayList<>(base.getKeys().values())) {
            key.remove();
          }
        }
        else {
          base.remove();
        }
      }
    }
    for (Map.Entry<String, Map<String, String>> dumpedNode : tree.keysByPath().entrySet()) {
      PreferenceEntry entry = resolveEntry(user, dumpedNode.getKey());
      for (Map.Entry<String, String> dumpedKey : dumpedNode.getValue().entrySet()) {
        entry.createKey(dumpedKey.getKey(), dumpedKey.getValue());
      }
    }
  }

  /**
   * Creates a persistent node not stored yet.
   *
   * @param user the username, null for the system preferences
   * @param path the absolute path
   * @return the persistent node
   */
  DbPreferencesNode createPersistentNode(String user, String path) {
    DbPreferencesNode node = new DbPreferencesNode();
    if (context != null) {
      node.setSession(getSession());
    }
    node.setUser(user);
    node.setName(path);
    return node;
  }

  /**
   * Creates a persistent key not stored yet.
   *
   * @param key the key name
   * @param value the value
   * @return the persistent key
   */
  DbPreferencesKey createPersistentKey(String key, String value) {
    DbPreferencesKey preferencesKey = new DbPreferencesKey();
    if (context != null) {
      preferencesKey.setSession(getSession());
    }
    preferencesKey.setKey(key);
    preferencesKey.setValue(value);
    return preferencesKey;
  }

  /**
   * Persists all pending modifications back to storage.<br>
   * Everything is written within a single transaction. Since {@link DbPreferencesNode} and {@link DbPreferencesKey}
   * are tracked and provide a table serial, running Tentackle applications will refresh their preferences caches
   * automatically.
   */
  public void persist() {
    assertLoaded();
    if (modificationCount == 0) {
      return;
    }
    getSession().transaction("persist preferences", () -> {
      for (PreferenceEntry root : new ArrayList<>(roots)) {
        deleteKeys(root);
      }
      for (PreferenceEntry root : new ArrayList<>(roots)) {
        deleteEntries(root);
      }
      for (PreferenceEntry root : new ArrayList<>(roots)) {
        saveEntries(root);
      }
      for (PreferenceEntry root : new ArrayList<>(roots)) {
        saveKeys(root);
      }
      return null;
    });
  }

  /**
   * Deletes all keys marked for deletion, depth first.
   *
   * @param entry the entry to start at
   */
  private void deleteKeys(PreferenceEntry entry) {
    for (PreferenceEntry child : new ArrayList<>(entry.getChildren().values())) {
      deleteKeys(child);
    }
    for (PreferenceValue key : new ArrayList<>(entry.getKeys().values())) {
      if (key.getState() == PreferenceState.REMOVED && !key.getPersistentKey().isNew()) {
        key.getPersistentKey().deleteObject();
      }
    }
  }

  /**
   * Deletes all entries marked for deletion, children before their parents.
   *
   * @param entry the entry to start at
   */
  private void deleteEntries(PreferenceEntry entry) {
    for (PreferenceEntry child : new ArrayList<>(entry.getChildren().values())) {
      deleteEntries(child);
    }
    if (entry.getState() == PreferenceState.REMOVED && !entry.getPersistentNode().isNew()) {
      entry.getPersistentNode().deleteObject();
    }
  }

  /**
   * Saves all new entries, parents before their children.
   *
   * @param entry the entry to start at
   */
  private void saveEntries(PreferenceEntry entry) {
    if (entry.getState() == PreferenceState.NEW && entry.getPersistentNode().isNew()) {
      DbPreferencesNode node = entry.getPersistentNode();
      if (entry.isRoot()) {
        node.setParentId(0);
        node.saveObject();
        node.setRootNodeId(node.getId());   // a root node is its own root
        node.saveObject();
      }
      else {
        DbPreferencesNode parentNode = entry.getParent().getPersistentNode();
        node.setParentId(parentNode.getId());
        node.setRootNodeId(parentNode.getRootNodeId());
        node.saveObject();
      }
    }
    if (entry.getState() != PreferenceState.REMOVED) {
      for (PreferenceEntry child : new ArrayList<>(entry.getChildren().values())) {
        saveEntries(child);
      }
    }
  }

  /**
   * Saves all new and modified keys.
   *
   * @param entry the entry to start at
   */
  private void saveKeys(PreferenceEntry entry) {
    if (entry.getState() == PreferenceState.REMOVED) {
      return;
    }
    DbPreferencesNode node = entry.getPersistentNode();
    for (PreferenceValue key : new ArrayList<>(entry.getKeys().values())) {
      if (key.getState() == PreferenceState.NEW || key.getState() == PreferenceState.MODIFIED) {
        DbPreferencesKey persistentKey = key.getPersistentKey();
        persistentKey.setNodeId(node.getId());
        persistentKey.setRootNodeId(node.getRootNodeId());
        persistentKey.saveObject();
      }
    }
    for (PreferenceEntry child : new ArrayList<>(entry.getChildren().values())) {
      saveKeys(child);
    }
  }

  /**
   * Asserts that the repository is loaded.
   */
  private synchronized void assertLoaded() {
    if (invalid) {
      loadFromStorage();
      invalid = false;
      fireListeners(true);
    }
  }

  /**
   * Fires the repo listeners.
   *
   * @param reloaded true if repo has been reloaded
   */
  private void fireListeners(boolean reloaded) {
    for (PreferencesUpdatedListener listener : listeners) {
      listener.preferencesUpdated(modificationCount, reloaded);
    }
  }

  /**
   * Gets the database session.
   *
   * @return the session
   */
  private Db getSession() {
    return (Db) getDomainContext().getSession();
  }

  /**
   * Loads the preferences from the database.
   */
  private void loadFromStorage() {
    // select all low-level (bypassing the factory!)
    Db db = getSession();
    buildTrees(new DbPreferencesNode(db).selectAllObjects(), new DbPreferencesKey(db).selectAllObjects());
  }

    // TODO: convert to Preferenzr DTOs (PreferenceNode, PreferenceKey)

  /**
   * Builds the preference trees from the persistent objects.
   *
   * @param dbPreferencesNodes all persistent nodes
   * @param dbPreferencesKeys all persistent keys
   */
  void buildTrees(List<DbPreferencesNode> dbPreferencesNodes, List<DbPreferencesKey> dbPreferencesKeys) {
    roots.clear();
    orphans.clear();
    modificationCount = 0;

    Map<Long, List<DbPreferencesNode>> nodesByParentId = new HashMap<>();
    Map<Long, List<DbPreferencesKey>> keysByNodeId = new HashMap<>();
    List<DbPreferencesNode> rootNodes = new ArrayList<>();

    for (DbPreferencesNode node : dbPreferencesNodes) {
      if (node.getParentId() == 0) {
        rootNodes.add(node);
      }
      else {
        nodesByParentId.computeIfAbsent(node.getParentId(), id -> new ArrayList<>()).add(node);
      }
    }
    for (DbPreferencesKey key : dbPreferencesKeys) {
      keysByNodeId.computeIfAbsent(key.getNodeId(), id -> new ArrayList<>()).add(key);
    }

    int nodeCount = 0;
    for (DbPreferencesNode rootNode : rootNodes) {
      roots.add(createEntry(null, rootNode, nodesByParentId, keysByNodeId));
      nodeCount++;
    }
    sortRoots();

    // whatever is left refers to a node that does not exist
    for (List<DbPreferencesNode> nodes : nodesByParentId.values()) {
      for (DbPreferencesNode node : nodes) {
        orphans.add(CommonBundle.createOrphanNodeComment(node.getName(), node.getParentId()));
        LOGGER.severe("orphan node found: {0}", node);
      }
    }
    for (List<DbPreferencesKey> keys : keysByNodeId.values()) {
      for (DbPreferencesKey key : keys) {
        orphans.add(CommonBundle.createOrphanKeyComment(key.getKey(), key.getNodeId()));
        LOGGER.severe("orphan key found: {0}", key);
      }
    }

    LOGGER.info("{0} preference trees loaded from {1} nodes and {2} keys, {3} orphans",
                nodeCount, dbPreferencesNodes.size(), dbPreferencesKeys.size(), orphans.size());
    invalid = false;    // the model is in sync with the given data
  }

  /**
   * Creates an entry and all of its sub-nodes and keys.<br>
   * The consumed nodes and keys are removed from the maps so that only the orphans are left over.
   *
   * @param parent the parent entry, null if root
   * @param node the persistent node
   * @param nodesByParentId all non-root nodes by their parent's ID
   * @param keysByNodeId all keys by their node's ID
   * @return the entry
   */
  private PreferenceEntry createEntry(PreferenceEntry parent, DbPreferencesNode node,
                                      Map<Long, List<DbPreferencesNode>> nodesByParentId,
                                      Map<Long, List<DbPreferencesKey>> keysByNodeId) {
    PreferenceEntry entry = new PreferenceEntry(this, parent, node, PreferenceState.UNCHANGED);
    List<DbPreferencesKey> keys = keysByNodeId.remove(node.getId());
    if (keys != null) {
      for (DbPreferencesKey key : keys) {
        entry.addKey(new PreferenceValue(entry, key, PreferenceState.UNCHANGED));
      }
    }
    List<DbPreferencesNode> childNodes = nodesByParentId.remove(node.getId());
    if (childNodes != null) {
      for (DbPreferencesNode childNode : childNodes) {
        entry.addChild(createEntry(entry, childNode, nodesByParentId, keysByNodeId));
      }
    }
    return entry;
  }

  /**
   * Sorts the trees: system preferences first, then the users in alphabetical order.
   */
  private void sortRoots() {
    roots.sort(Comparator.comparing(PreferenceEntry::getUser, Comparator.nullsFirst(Comparator.naturalOrder())));
  }

}
