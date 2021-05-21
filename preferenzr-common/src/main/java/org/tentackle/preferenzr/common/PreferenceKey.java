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

import org.tentackle.validate.Validateable;
import org.tentackle.validate.ValidationScopeFactory;
import org.tentackle.validate.ValidationUtilities;
import org.tentackle.validate.validator.NotEmpty;
import org.tentackle.validate.validator.NotNull;

import java.util.List;
import java.util.Map;

/**
 * A preference key.<br>
 * The key holds the value.
 */
public class PreferenceKey implements PreferenceNode, Validateable {

  private final PreferenceName preferenceName;
  private final String key;
  private String value;
  private int modificationCount;

  /**
   * Creates a preference key.
   *
   * @param preferenceName the preference node name
   * @param key the key
   * @param value the value
   */
  public PreferenceKey(PreferenceName preferenceName, String key, String value) {
    this.preferenceName = preferenceName;
    this.key = key;
    this.value = value;
    validate(ValidationUtilities.getInstance().getDefaultValidationPath(this),
             ValidationScopeFactory.getInstance().getAllScope());
  }

  /**
   * Gets the preference name this key belongs to.
   *
   * @return the preference name
   */
  public PreferenceName getPreferenceName() {
    return preferenceName;
  }

  @Override
  @NotEmpty
  public String getName() {
    return key;
  }

  @Override
  public String getValue() {
    return value;
  }

  @Override
  public List<? extends PreferenceNode> getNodes() {
    return null;
  }

  @Override
  public void updateModificationCount(int diff) {
    modificationCount += diff;
    preferenceName.updateModificationCount(diff);
  }

  @Override
  public int getModificationCount() {
    return modificationCount;
  }

}
