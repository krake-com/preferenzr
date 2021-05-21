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

import org.tentackle.common.Service;
import org.tentackle.prefs.DefaultPreferences;
import org.tentackle.prefs.DefaultPreferencesFactory;
import org.tentackle.prefs.PersistedPreferencesFactory;

/**
 * Use java util prefs.<br>
 * Don't talk to the database!
 * <p>
 * See module in module-info annotated with &#64;ModuleOrdinal.
 * <p>
 * {@link java.util.spi.ResourceBundleControlProvider} is not necessary to remap since this is a modular application.
 */
@Service(PersistedPreferencesFactory.class)
public class PreferenzrPreferencesFactory extends DefaultPreferencesFactory {

  @Override
  public boolean isSystemOnly() {
    return false;
  }

  @Override
  public DefaultPreferences userRoot() {
    return super.userRoot().node("Preferenzr");
  }

  @Override
  public DefaultPreferences systemRoot() {
    return userRoot();
  }

  @Override
  public DefaultPreferences systemNodeForPackage(Class<?> c) {
    return userNodeForPackage(c);
  }
}
