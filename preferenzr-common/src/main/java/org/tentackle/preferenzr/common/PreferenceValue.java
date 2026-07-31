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
import org.tentackle.dbms.prefs.DbPreferencesKey;
import org.tentackle.validate.Validateable;
import org.tentackle.validate.ValidationScopeFactory;
import org.tentackle.validate.ValidationUtilities;
import org.tentackle.validate.validator.NotEmpty;

import java.util.List;
import java.util.Objects;

/**
 * A key within a preferences node.<br>
 * The key holds the value and is always a leaf of the preferences tree.
 */
public class PreferenceValue implements PreferenceNode, Validateable {

  private final PreferenceEntry entry;                // the node this key belongs to
  private final DbPreferencesKey persistentKey;       // the persistent key holding name and value
  private final String initialValue;                  // the value as loaded from storage
  private PreferenceState state;                      // pending modification
  private int modificationCount;                      // 1 if this key is modified, else 0

  /**
   * Creates a preference key.
   *
   * @param entry the node this key belongs to
   * @param persistentKey the persistent key
   * @param state the initial state
   */
  public PreferenceValue(PreferenceEntry entry, DbPreferencesKey persistentKey, PreferenceState state) {
    this.entry = entry;
    this.persistentKey = persistentKey;
    this.state = state;
    this.initialValue = state == PreferenceState.NEW ? null : persistentKey.getValue();
    validate(ValidationUtilities.getInstance().getDefaultValidationPath(this),
             ValidationScopeFactory.getInstance().getAllScope());
  }

  /**
   * Gets the node this key belongs to.
   *
   * @return the entry
   */
  public PreferenceEntry getEntry() {
    return entry;
  }

  /**
   * Gets the persistent key.
   *
   * @return the persistent key, never null
   */
  public DbPreferencesKey getPersistentKey() {
    return persistentKey;
  }

  /**
   * Gets the value as loaded from storage.
   *
   * @return the initial value, null if this key was created by the user
   */
  public String getInitialValue() {
    return initialValue;
  }

  @Override
  @NotEmpty
  public String getName() {
    return persistentKey.getKey();
  }

  @Override
  @Bindable
  public String getValue() {
    return persistentKey.getValue();
  }

  /**
   * Sets the value.<br>
   * Updates the modification count whenever the modified state changes.
   *
   * @param value the value
   */
  @Bindable
  public void setValue(String value) {
    persistentKey.setValue(value);
    if (state == PreferenceState.UNCHANGED && !Objects.equals(value, initialValue)) {
      state = PreferenceState.MODIFIED;
      updateModificationCount(1);
    }
    else if (state == PreferenceState.MODIFIED && Objects.equals(value, initialValue)) {
      state = PreferenceState.UNCHANGED;
      updateModificationCount(-1);
    }
  }

  @Override
  public String getPath() {
    return entry.getPath();
  }

  @Override
  public String getUser() {
    return entry.getUser();
  }

  @Override
  public List<? extends PreferenceNode> getNodes() {
    return null;
  }

  @Override
  public PreferenceState getState() {
    return state;
  }

  @Override
  public void updateModificationCount(int diff) {
    modificationCount += diff;
    entry.updateModificationCount(diff);
  }

  @Override
  public int getModificationCount() {
    return modificationCount;
  }

  /**
   * Marks this key for deletion.<br>
   * Keys that were never persisted are dropped from the model instead.
   */
  public void remove() {
    if (state == PreferenceState.NEW) {
      updateModificationCount(-modificationCount);
      entry.getKeys().remove(getName());
    }
    else if (state != PreferenceState.REMOVED) {
      if (state == PreferenceState.UNCHANGED) {
        updateModificationCount(1);
      }
      state = PreferenceState.REMOVED;
    }
  }

  /**
   * Un-marks this key for deletion.
   */
  public void revive() {
    if (state == PreferenceState.REMOVED) {
      state = Objects.equals(getValue(), initialValue) ? PreferenceState.UNCHANGED : PreferenceState.MODIFIED;
      if (state == PreferenceState.UNCHANGED) {
        updateModificationCount(-1);
      }
      entry.revive();
    }
  }

  @Override
  public String toString() {
    return entry + "/" + getName() + "=" + getValue();
  }

}
