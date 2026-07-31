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

package org.tentackle.preferenzr.gui.editor;

import javafx.fxml.FXML;

import org.tentackle.bind.Bindable;
import org.tentackle.fx.AbstractFxController;
import org.tentackle.fx.FxControllerService;
import org.tentackle.fx.component.FxTextArea;
import org.tentackle.preferenzr.common.CommonBundle;
import org.tentackle.preferenzr.common.PreferenceNode;
import org.tentackle.preferenzr.common.PreferenceState;
import org.tentackle.preferenzr.common.PreferenceValue;
import org.tentackle.preferenzr.common.PreferenzrPreferences;

import java.util.Objects;

/**
 * Editor for the value of a preference key.<br>
 * Shows the scope and the node path of the selected tree item and allows editing the value if the item is a key.
 */
@FxControllerService
public class PreferenceEditor extends AbstractFxController {

  private static final String STYLE_MODIFIED = "value-modified";   // css style if the value differs from the stored one

  @Bindable
  private PreferenceValue preference;         // the edited key, null if none

  @FXML
  private FxTextArea scopeField;                 // system or username

  @FXML
  private FxTextArea pathField;                  // absolute path of the node

  @FXML
  private FxTextArea keyField;                   // name of the key

  @FXML
  private FxTextArea preferenceValue;         // bound to preference.value

  @FXML
  public void initialize() {
    updatePreferredSize();
    PreferenzrPreferences.getInstance().getUserPrefs().addPreferenceChangeListener(evt -> {
      if (PreferenzrPreferences.VALUE_ROWS.equals(evt.getKey())) {
        updatePreferredSize();
      }
    });
    scopeField.setBindable(false);
    pathField.setBindable(false);
    keyField.setBindable(false);
  }

  @Override
  public void configure() {
    preferenceValue.addViewToModelListener(this::valueUpdated);
    setNode(null);
  }

  /**
   * Sets the node to edit.
   *
   * @param node the selected node, null if nothing selected
   */
  public void setNode(PreferenceNode node) {
    preference = node instanceof PreferenceValue value ? value : null;
    scopeField.setText(node == null ? null
                       : node.getUser() == null ? CommonBundle.getString("System") : node.getUser());
    pathField.setText(node == null ? null : node.getPath());
    keyField.setText(preference == null ? null : preference.getName());
    // set the initial value first to allow Ctrl-Z via saveView below
    preferenceValue.setText(preference == null ? null : preference.getInitialValue());
    getContainer().saveView();
    getContainer().updateView();    // this sets the current value
    preferenceValue.setEditable(preference != null && preference.getState() != PreferenceState.REMOVED);
    valueUpdated();
  }

  /**
   * Requests the focus for the value text area.
   */
  public void requestFocus() {
    preferenceValue.requestFocus();
  }

  /**
   * Applies the preferred number of rows configured by the user.
   */
  private void updatePreferredSize() {
    preferenceValue.setPrefRowCount(PreferenzrPreferences.getInstance().getValueRows());
  }

  /**
   * Highlights the value if it differs from the one stored in the database.
   */
  private void valueUpdated() {
    if (preference != null && !Objects.equals(preference.getValue(), preference.getInitialValue())) {
      if (!getView().getStyleClass().contains(STYLE_MODIFIED)) {
        getView().getStyleClass().add(STYLE_MODIFIED);
      }
    }
    else {
      getView().getStyleClass().remove(STYLE_MODIFIED);
    }
  }

}
