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

import org.tentackle.bind.Bindable;
import org.tentackle.dbms.prefs.DbPreferencesNode;
import org.tentackle.validate.Validateable;
import org.tentackle.validate.ValidationScopeFactory;
import org.tentackle.validate.ValidationUtilities;
import org.tentackle.validate.validator.NotEmpty;

import java.util.ArrayList;
import java.util.List;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * A node within a preferences tree.<br>
 * Corresponds to a {@link DbPreferencesNode} and holds its sub-nodes and its {@link PreferenceValue}s.
 * The root entry of a tree represents the scope, i.e. the system preferences or the preferences of a single user.
 */
public class PreferenceEntry implements PreferenceNode, Validateable {

  private final PreferencesRepository repository;             // the owning repository
  private final PreferenceEntry parent;                       // the parent entry, null if root
  private final DbPreferencesNode persistentNode;             // the persistent node
  private final SortedMap<String, PreferenceEntry> children;  // sub-nodes by basename
  private final SortedMap<String, PreferenceValue> keys;      // keys by name
  private PreferenceState state;                              // pending modification
  private int modificationCount;                              // number of pending modifications in this subtree

  /**
   * Creates a preference entry.
   *
   * @param repository the repository
   * @param parent the parent entry, null if this is the root of a tree
   * @param persistentNode the persistent node
   * @param state the initial state
   */
  public PreferenceEntry(PreferencesRepository repository, PreferenceEntry parent,
                         DbPreferencesNode persistentNode, PreferenceState state) {
    this.repository = repository;
    this.parent = parent;
    this.persistentNode = persistentNode;
    this.state = state;
    children = new TreeMap<>();
    keys = new TreeMap<>();
    validate(ValidationUtilities.getInstance().getDefaultValidationPath(this),
             ValidationScopeFactory.getInstance().getAllScope());
  }

  /**
   * Gets the preferences repository.
   *
   * @return the repository
   */
  public PreferencesRepository getRepository() {
    return repository;
  }

  /**
   * Gets the parent entry.
   *
   * @return the parent, null if this is the root of a tree
   */
  public PreferenceEntry getParent() {
    return parent;
  }

  /**
   * Gets the persistent node.
   *
   * @return the persistent node, never null
   */
  public DbPreferencesNode getPersistentNode() {
    return persistentNode;
  }

  /**
   * Returns whether this entry is the root of a preferences tree.
   *
   * @return true if root
   */
  public boolean isRoot() {
    return parent == null;
  }

  @Override
  @NotEmpty
  @Bindable
  public String getName() {
    if (isRoot()) {
      return persistentNode.getUser() == null ? CommonBundle.getString("System") : persistentNode.getUser();
    }
    return persistentNode.getBaseName();
  }

  @Override
  public String getValue() {
    return null;
  }

  @Override
  public String getPath() {
    return persistentNode.getName();
  }

  @Override
  public String getUser() {
    return persistentNode.getUser();
  }

  @Override
  public List<? extends PreferenceNode> getNodes() {
    List<PreferenceNode> nodes = new ArrayList<>(children.size() + keys.size());
    nodes.addAll(children.values());
    nodes.addAll(keys.values());
    return nodes;
  }

  @Override
  public PreferenceState getState() {
    return state;
  }

  @Override
  public void updateModificationCount(int diff) {
    modificationCount += diff;
    if (parent == null) {
      repository.updateModificationCount(diff);
    }
    else {
      parent.updateModificationCount(diff);
    }
  }

  @Override
  public int getModificationCount() {
    return modificationCount;
  }

  /**
   * Gets the sub-nodes of this entry.
   *
   * @return the sub-nodes by basename, sorted
   */
  public SortedMap<String, PreferenceEntry> getChildren() {
    return children;
  }

  /**
   * Gets the keys of this entry.
   *
   * @return the keys by name, sorted
   */
  public SortedMap<String, PreferenceValue> getKeys() {
    return keys;
  }

  /**
   * Gets a sub-node by its basename.
   *
   * @param name the basename
   * @return the sub-node, null if no such node
   */
  public PreferenceEntry getChild(String name) {
    return children.get(name);
  }

  /**
   * Gets a key by its name.
   *
   * @param key the key name
   * @return the key, null if no such key
   */
  public PreferenceValue getKey(String key) {
    return keys.get(key);
  }

  /**
   * Adds a sub-node loaded from storage.<br>
   * Only used while building the tree.
   *
   * @param child the child entry
   */
  void addChild(PreferenceEntry child) {
    children.put(child.getName(), child);
  }

  /**
   * Adds a key loaded from storage.<br>
   * Only used while building the tree.
   *
   * @param key the key
   */
  void addKey(PreferenceValue key) {
    keys.put(key.getName(), key);
  }

  /**
   * Creates a new sub-node.
   *
   * @param name the basename of the new node
   * @return the new entry
   */
  public PreferenceEntry createChild(String name) {
    PreferenceEntry child = children.get(name);
    if (child != null) {
      if (child.getState() == PreferenceState.REMOVED) {
        child.revive();
      }
      return child;
    }
    child = new PreferenceEntry(repository, this,
                                repository.createPersistentNode(getUser(), createChildPath(name)),
                                PreferenceState.NEW);
    children.put(name, child);
    child.updateModificationCount(1);
    return child;
  }

  /**
   * Creates a new key.
   *
   * @param key the key name
   * @param value the value
   * @return the new preference value
   */
  public PreferenceValue createKey(String key, String value) {
    PreferenceValue preferenceValue = keys.get(key);
    if (preferenceValue != null) {
      preferenceValue.revive();
      preferenceValue.setValue(value);
      return preferenceValue;
    }
    preferenceValue = new PreferenceValue(this, repository.createPersistentKey(key, value), PreferenceState.NEW);
    keys.put(key, preferenceValue);
    preferenceValue.updateModificationCount(1);
    return preferenceValue;
  }

  /**
   * Builds the absolute path of a sub-node.
   *
   * @param name the basename of the sub-node
   * @return the absolute path
   */
  public String createChildPath(String name) {
    String path = getPath();
    return path.endsWith("/") ? path + name : path + "/" + name;
  }

  /**
   * Marks this entry and all of its sub-nodes and keys for deletion.<br>
   * Entries that were never persisted are dropped from the model instead.
   */
  public void remove() {
    if (state == PreferenceState.NEW) {
      detach();
    }
    else if (state != PreferenceState.REMOVED) {
      for (PreferenceEntry child : new ArrayList<>(children.values())) {
        child.remove();
      }
      for (PreferenceValue key : new ArrayList<>(keys.values())) {
        key.remove();
      }
      state = PreferenceState.REMOVED;
      updateModificationCount(1);
    }
  }

  /**
   * Un-marks this entry for deletion.<br>
   * The parent chain is revived as well, since a node cannot exist without its parents.
   */
  public void revive() {
    if (state == PreferenceState.REMOVED) {
      state = PreferenceState.UNCHANGED;
      updateModificationCount(-1);
    }
    if (parent != null) {
      parent.revive();
    }
  }

  /**
   * Drops this never-persisted entry from its parent and undoes its contribution to the modification count.
   */
  private void detach() {
    updateModificationCount(-modificationCount);
    if (parent != null) {
      parent.children.remove(getName());
    }
  }

  @Override
  public String toString() {
    return (getUser() == null ? "<system>" : getUser()) + ":" + getPath();
  }

}
