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

import org.tentackle.app.AbstractApplication;
import org.tentackle.app.Application;
import org.tentackle.dbms.Db;
import org.tentackle.dbms.prefs.DbPreferencesFactory;
import org.tentackle.dbms.prefs.DbPreferencesKey;
import org.tentackle.dbms.prefs.DbPreferencesNode;
import org.tentackle.pdo.DomainContext;
import org.tentackle.pdo.DomainContextProvider;
import org.tentackle.prefs.PersistedPreferences;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * The preference repository encapsulates the StoredBundle and StoredBundleKey persistence.<br>
 * It maps the i18n PDOs to internal DTOs. This allows the other modules to refer to the DTOs
 * only to avoid dependencies on the persistence layer.
 */
public class PreferencesRepository implements DomainContextProvider {

  private final DomainContext context;                          // domain context to load from storage
  private final List<PreferencesUpdatedListener> listeners;     // preferences modification listener
  private final Map<String, PreferenceNode> preferenceNodes;    // internal preference model
  private boolean invalid;                                      // true if repo is outdated and needs to be reloaded
  private int modificationCount;                                // number of modified preferences

  public PreferencesRepository() {
    context = Application.getInstance().getDomainContext();
    listeners = new ArrayList<>();
    preferenceNodes = new TreeMap<>();
    invalid = true;
  }

  @Override
  public DomainContext getDomainContext() {
    return context;
  }

  /**
   * Gets the loaded preference nodes.
   *
   * @return the preferences
   */
  public Collection<? extends PreferenceNode> getPreferences() {
    assertLoaded();
    return preferenceNodes.values();
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
   * Persists the preferences back to storage.
   */
  public void persist() {
    assertLoaded();
    for (PreferenceNode preferenceNode : preferenceNodes.values()) {
      if (preferenceNode.getModificationCount() != 0) {
        persist(preferenceNode);
      }
    }
  }




  /**
   * Persiste the given preference for a locale.
   *
   * @param preferenceName the preference
   */
  private void persist(PreferenceNode preferenceName) {
    context.getSession().transaction(() -> {    // one transaction per preference

      return null;
    });
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
   * Loads the preferences from the database.
   */
  private void loadFromStorage() {
    preferenceNodes.clear();

    // select all low-level (bypassing the factory!)
    List<DbPreferencesNode> dbPreferencesNodes = new DbPreferencesNode((Db) getDomainContext().getSession()).selectAllObjects();
    List<DbPreferencesKey> dbPreferencesKeys = new DbPreferencesKey((Db) getDomainContext().getSession()).selectAllObjects();

    // TODO: convert to Preferenzr DTOs (PreferenceNode, PreferenceKey)

    modificationCount = 0;
  }

}
