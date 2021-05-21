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
import org.tentackle.validate.Validateable;
import org.tentackle.validate.ValidationScopeFactory;
import org.tentackle.validate.ValidationUtilities;
import org.tentackle.validate.validator.NotEmpty;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class PreferenceName implements PreferenceNode, Validateable {

  private final PreferencesRepository repository;
  private final String name;
  private final Map<String, PreferenceKey> keys;
  private int modificationCount;

  public PreferenceName(PreferencesRepository repository, String name, Map<String, PreferenceKey> keys) {
    this.repository = repository;
    this.name = name;
    this.keys = keys;
    validate(ValidationUtilities.getInstance().getDefaultValidationPath(this),
             ValidationScopeFactory.getInstance().getAllScope());
  }

  /**
   * Gets the preference repository.
   *
   * @return the repository
   */
  public PreferencesRepository getRepository() {
    return repository;
  }

  @Override
  @NotEmpty
  @Bindable
  public String getName() {
    return name;
  }

  @Override
  public String getValue() {
    return null;
  }

  public Map<String, PreferenceKey> getKeys() {
    return keys;
  }

  @Override
  public List<? extends PreferenceNode> getNodes() {
    return new ArrayList<>(keys.values());
  }

  @Override
  public void updateModificationCount(int diff) {
    modificationCount += diff;
    repository.updateModificationCount(diff);
  }

  @Override
  public int getModificationCount() {
    return modificationCount;
  }

}
