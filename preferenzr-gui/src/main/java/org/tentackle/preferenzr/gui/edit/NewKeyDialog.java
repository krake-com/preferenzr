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

package org.tentackle.preferenzr.gui.edit;

import javafx.fxml.FXML;
import javafx.stage.Modality;
import javafx.stage.Stage;

import org.tentackle.bind.Bindable;
import org.tentackle.common.StringHelper;
import org.tentackle.fx.AbstractFxController;
import org.tentackle.fx.Fx;
import org.tentackle.fx.FxControllerService;
import org.tentackle.fx.component.FxButton;
import org.tentackle.fx.component.FxLabel;
import org.tentackle.fx.component.FxTextArea;
import org.tentackle.fx.component.FxTextField;
import org.tentackle.preferenzr.common.PreferenceEntry;
import org.tentackle.preferenzr.common.PreferenceValue;

import java.text.MessageFormat;
import java.util.Objects;
import java.util.ResourceBundle;
import java.util.function.Consumer;

/**
 * Dialog to create a key within a preferences node.
 */
@FxControllerService
public class NewKeyDialog extends AbstractFxController {

  /**
   * Shows the dialog.
   *
   * @param owner the owner of the dialog
   * @param entry the node to create the key in
   * @param done invoked with the created key if the user confirmed
   */
  public static void show(Object owner, PreferenceEntry entry, Consumer<PreferenceValue> done) {
    Stage stage = Fx.createStage(Modality.APPLICATION_MODAL, owner);
    NewKeyDialog dialog = Fx.load(NewKeyDialog.class);
    dialog.entry = Objects.requireNonNull(entry);
    stage.setTitle(dialog.resources.getString("new key"));
    stage.setScene(Fx.createScene(dialog.getView()));
    stage.setOnHidden(event -> {
      if (dialog.created != null) {
        done.accept(dialog.created);
      }
    });
    Fx.show(stage);
    dialog.nodeLabel.setText(entry.getPath());
    dialog.getContainer().updateView();
    dialog.keyField.requestFocus();
  }

  @Bindable
  private String key;                   // the name of the new key

  @Bindable
  private String value;                 // the value of the new key

  @FXML
  private FxLabel nodeLabel;

  @FXML
  private FxTextField keyField;

  @FXML
  private FxTextArea valueField;

  @FXML
  private FxButton cancelButton;

  @FXML
  private FxButton createButton;

  @FXML
  private ResourceBundle resources;

  private PreferenceEntry entry;        // the node the key belongs to
  private PreferenceValue created;      // the created key, null if cancelled

  @FXML
  private void initialize() {
    cancelButton.setOnAction(event -> getStage().close());
    createButton.setOnAction(event -> create());
    keyField.setOnAction(event -> valueField.requestFocus());
  }

  /**
   * Creates the key if the name is valid.
   */
  private void create() {
    getContainer().updateModel();
    String keyName = key == null ? null : key.trim();
    if (StringHelper.isAllWhitespace(keyName)) {
      Fx.error(getView(), MessageFormat.format(resources.getString("not a valid key name: {0}"), key));
      return;
    }
    created = entry.createKey(keyName, value);
    getStage().close();
  }

}
