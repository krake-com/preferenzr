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
import org.tentackle.i18n.pdo.StoredBundle;
import org.tentackle.pdo.DomainContext;
import org.tentackle.pdo.DomainContextProvider;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.prefs.Preferences;

/**
 * The preference repository encapsulates the StoredBundle and StoredBundleKey persistence.<br>
 * It maps the i18n PDOs to internal DTOs. This allows the other modules to refer to the DTOs
 * only to avoid dependencies on the persistence layer.
 */
public class PreferencesRepository implements DomainContextProvider {

  private final DomainContext context;                      // domain context to load from storage
  private final List<PreferencesUpdatedListener> listeners;     // translation modification listener
  private final Preferences prefs;                          // user preferences for the active locales
  private final Set<String> activeLocales;                  // the selected locales for the current user
  private final Set<String> locales;                        // all locales persisted in the database (including the default "")
  private final Map<String, PreferenceNode> preferenceNames;        // internal preference model, null to force (re)load
  private boolean invalid;                                  // true if repo is outdated and needs to be reloaded
  private int modificationCount;                            // number of modified translations
  private int missingCount;                                 // -1 if pending computation

  public PreferencesRepository() {
    context = AbstractApplication.getRunningApplication().getDomainContext();
    listeners = new ArrayList<>();
    preferenceNames = new TreeMap<>();
    prefs = Preferences.userNodeForPackage(getClass());
    activeLocales = new TreeSet<>();
    locales = new TreeSet<>();
    invalid = true;
  }

  @Override
  public DomainContext getDomainContext() {
    return context;
  }

  /**
   * Returns all locales.<br>
   * The default locale is the empty string.
   *
   * @return the locales
   */
  public Set<String> getLocales() {
    assertLoaded();
    return locales;
  }

  /**
   * Returns all active locales.
   *
   * @return the active locales
   */
  public Set<String> getActiveLocales() {
    assertLoaded();
    return activeLocales;
  }

  /**
   * Returns whether the given locale is active.
   *
   * @param locale the locale
   * @return true if active
   */
  public boolean isLocaleActive(String locale) {
    assertLoaded();
    return activeLocales.contains(locale);
  }

  /**
   * Sets a locale active / deactivated.
   *
   * @param locale the locale
   * @param active true if active, else deactivate
   */
  public void setLocaleActive(String locale, boolean active) {
    if (isLocaleActive(locale) != active) {
      if (active) {
        activeLocales.add(locale);
      }
      else {
        activeLocales.remove(locale);
      }
      prefs.putBoolean(locale, active);
      invalid = true;
      missingCount = -1;
      for (PreferenceNode preferenceName : preferenceNames.values()) {
//        for (BundleKey preferenceKey : preferenceName.getKeys().values()) {
//          preferenceKey.invalidateMissingCount();
//        }
      }
      assertLoaded();
    }
  }

  /**
   * Gets the loaded preference nodes.
   *
   * @return the preferences
   */
  public Collection<? extends PreferenceNode> getBundles() {
    assertLoaded();
    return preferenceNames.values();
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
   * Gets the number of missing translations.
   *
   * @return the missing count
   */
  public int getMissingCount() {
    assertLoaded();
    if (missingCount == -1) {
      missingCount = 0;
      for (PreferenceNode preferenceName : preferenceNames.values()) {
        missingCount += preferenceName.getMissingCount();
      }
    }
    return missingCount;
  }

  /**
   * Adds a preference update listener.
   *
   * @param listener the listener
   */
  public void addBundlesUpdatedListener(PreferencesUpdatedListener listener) {
    listeners.add(listener);
  }

  /**
   * Removes a preference update listener.
   *
   * @param listener the listener
   */
  public void removeBundlesUpdatedListener(PreferencesUpdatedListener listener) {
    listeners.remove(listener);
  }

  /**
   * Updates the modification count.
   *
   * @param diff number of modifications to add
   */
  public void updateModificationCount(int diff) {
    missingCount = -1;
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
    for (PreferenceNode preferenceName : preferenceNames.values()) {
      if (preferenceName.getModificationCount() != 0) {
        for (String locale : activeLocales) {
          persist(preferenceName, locale.isEmpty() ? null : locale);
        }
      }
    }
  }

  /**
   * Renames or moves a preference.
   * <p>
   * Throws {@link org.tentackle.session.ConstraintException} if renaming failed.
   *
   * @param preferenceName the preference
   * @param newName the new name of the preference
   */
  public void renameBundle(PreferenceNode preferenceName, String newName) {
    context.getSession().transaction(() -> {
      for (StoredBundle storedBundle : getStoredBundles(preferenceName)) {
        storedBundle.setName(newName);
        storedBundle.persist();
        invalidate();
      }
      return null;
    });
  }

  /**
   * Renames a package.
   * <p>
   * Throws {@link org.tentackle.session.ConstraintException} if renaming failed.
   *
   * @param oldPackage the old package name
   * @param newPackage the new package name
   */
  public void renamePackage(String oldPackage, String newPackage) {
    String packageName;
    if (!newPackage.isEmpty() && !newPackage.endsWith(".")) {
      packageName = newPackage + ".";
    }
    else {
      packageName = newPackage;
    }
    context.getSession().transaction(() -> {
      for (PreferenceNode preferenceName : getBundlesForPackage(oldPackage)) {
        for (StoredBundle storedBundle : getStoredBundles(preferenceName)) {
          String remainder = storedBundle.getName().substring(oldPackage.length());
          if (remainder.startsWith(".")) {
            remainder = remainder.substring(1);
          }
          storedBundle.setName(packageName + remainder);
          storedBundle.persist();
          invalidate();
        }
      }
      return null;
    });
  }


  /**
   * Persiste the given preference for a locale.
   *
   * @param preferenceName the preference
   * @param locale the locale, null if default locale
   */
  private void persist(PreferenceNode preferenceName, String locale) {
    context.getSession().transaction(() -> {    // one transaction per preference
      StoredBundle storedBundle = on(StoredBundle.class).findByNameAndLocale(preferenceName.getName(), locale);
      if (storedBundle == null) {
        storedBundle = on(StoredBundle.class);
        storedBundle.setName(preferenceName.getName());
        storedBundle.setLocale(locale);
      }
//      for (BundleKey preferenceKey : preferenceName.getKeys().values()) {
//        BundleTranslation translation = preferenceKey.getTranslation(locale == null ? "" : locale);
//        storedBundle.setTranslation(preferenceKey.getName(), translation == null ? null : translation.getValue());
//      }
      if (storedBundle.isModified()) {
        storedBundle.save();
      }
      return null;
    });
  }

  /**
   * Asserts that the repository is loaded.
   */
  private synchronized void assertLoaded() {
    if (invalid) {
      loadBundlesFromStorage();
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
  private void loadBundlesFromStorage() {
    preferenceNames.clear();
    locales.clear();

    // load persisted preferences
    for (StoredBundle storedBundle : on(StoredBundle.class).selectAll()) {
      String locale = storedBundle.getLocale();
      if (locale == null) {
        locale = "";
      }
      locales.add(locale);
      boolean active = prefs.getBoolean(locale, true);
      if (active) {
        activeLocales.add(locale);
      }
      else {
        activeLocales.remove(locale);
      }
//      BundleNode preferenceName = preferenceNames.computeIfAbsent(storedBundle.getName(), n -> new BundleNode(this, n, new TreeMap<>()));
//      for (StoredBundleKey storedKey : storedBundle.getKeys()) {
//        BundleKey preferenceKey = preferenceName.getKeys().computeIfAbsent(storedKey.getKey(), k -> new BundleKey(preferenceName, k, new TreeMap<>()));
//        preferenceKey.getTranslations().computeIfAbsent(locale, l -> new BundleTranslation(preferenceKey, l, storedKey.getValue(), active));
//      }
    }

    // add missing translations (for the active locales only!)
    for (PreferenceNode preferenceName : preferenceNames.values()) {
//      for (BundleKey preferenceKey : preferenceName.getKeys().values()) {
//        for (String locale : activeLocales) {
//          BundleTranslation translation = preferenceKey.getTranslation(locale);
//          if (translation == null) {
//            preferenceKey.getTranslations().put(locale, new BundleTranslation(preferenceKey, locale, null, true));
//          }
//        }
//      }
    }

    modificationCount = 0;
    missingCount = -1;
  }

  /**
   * Returns the stored preferences for the given preference name.
   *
   * @param preferenceName the preferencename
   * @return the stored preferences for all locales
   */
  private List<StoredBundle> getStoredBundles(PreferenceNode preferenceName) {
    return on(StoredBundle.class).findByName(preferenceName.getName());
  }

  /**
   * Returns the stored preferences for the given preference name.
   *
   * @param packageName the package name
   * @return the stored preferences for all locales
   */
  private List<PreferenceNode> getBundlesForPackage(String packageName) {
    if (!packageName.endsWith(".")) {
      packageName += ".";
    }
    List<PreferenceNode> preferences = new ArrayList<>();
    for (PreferenceNode preference : preferenceNames.values()) {
      if (preference.getName().startsWith(packageName)) {
        preferences.add(preference);
      }
    }
    return preferences;
  }

}
