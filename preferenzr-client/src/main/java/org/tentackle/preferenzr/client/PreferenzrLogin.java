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

package org.tentackle.preferenzr.client;

import org.tentackle.common.Constants;
import org.tentackle.fx.rdc.login.Login;
import org.tentackle.session.BackendConfiguration;

/**
 * Login controller with copy of user/password from the backend configuration.<br>
 * In standard tentackle applications, the backend configuration for 2-tier mode contains the credentials for the database connection only
 * and the user/password entered by the user is checked at the application level.
 * Preferenzr, however, does not provide its own user PDOs and the user's input <em>is</em> used for the connection.<br>
 * If autologin is required, backend.properties must be configured like so:
 * <pre>
 * &#64;user:test
 * autologin
 * </pre>
 */
public class PreferenzrLogin extends Login {

  @Override
  protected boolean determineLoginPaneVisibility(double progress) {
    // for autologin: don't show the misleading user/password which is not used at all
    return !getSessionInfo().getProperties().containsKey(Constants.BACKEND_AUTOLOGIN) && super.determineLoginPaneVisibility(progress);
  }

  @Override
  public void presetUserAndPassword(BackendConfiguration backendConfiguration) {
    // only used in interactive mode! (not if autologin)
    if (backendConfiguration != null && backendConfiguration.getUrl() != null &&
        backendConfiguration.getUser() != null) {
      // copy the user and password from the backend config
      setUsername(backendConfiguration.getUser());
      if (backendConfiguration.getPassword() != null) {
        setPassword(backendConfiguration.getPassword().toCharArray());
      }
      else {
        setPassword(null);
      }
    }
  }

}
