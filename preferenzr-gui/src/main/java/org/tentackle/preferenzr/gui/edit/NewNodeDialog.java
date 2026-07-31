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
import org.tentackle.fx.component.FxTextField;
import org.tentackle.preferenzr.common.PreferenceEntry;

import java.text.MessageFormat;
import java.util.Objects;
import java.util.ResourceBundle;
import java.util.function.Consumer;

/**
 * Dialog to create a sub-node below a preferences node.
 */
@FxControllerService
public class NewNodeDialog extends AbstractFxController {

  /**
   * Shows the dialog.
   *
   * @param owner the owner of the dialog
   * @param parent the node to create the sub-node in
   * @param done invoked with the created node if the user confirmed
   */
  public static void show(Object owner, PreferenceEntry parent, Consumer<PreferenceEntry> done) {
    Stage stage = Fx.createStage(Modality.APPLICATION_MODAL, owner);
    NewNodeDialog dialog = Fx.load(NewNodeDialog.class);
    dialog.parent = Objects.requireNonNull(parent);
    stage.setTitle(dialog.resources.getString("new node"));
    stage.setScene(Fx.createScene(dialog.getView()));
    stage.setOnHidden(event -> {
      if (dialog.created != null) {
        done.accept(dialog.created);
      }
    });
    Fx.show(stage);
    dialog.parentLabel.setText(parent.getPath());
    dialog.getContainer().updateView();
    dialog.nameField.requestFocus();
  }

  @Bindable
  private String name;                  // the basename of the new node

  @FXML
  private FxLabel parentLabel;

  @FXML
  private FxTextField nameField;

  @FXML
  private FxButton cancelButton;

  @FXML
  private FxButton createButton;

  @FXML
  private ResourceBundle resources;

  private PreferenceEntry parent;       // the parent node
  private PreferenceEntry created;      // the created node, null if cancelled

  @FXML
  private void initialize() {
    cancelButton.setOnAction(event -> getStage().close());
    createButton.setOnAction(event -> create());
    nameField.setOnAction(event -> createButton.doClick());
  }

  /**
   * Creates the node if the name is valid.
   */
  private void create() {
    String nodeName = name == null ? null : name.trim();
    if (StringHelper.isAllWhitespace(nodeName) || nodeName.indexOf('/') >= 0) {
      Fx.error(getView(), MessageFormat.format(resources.getString("not a valid node name: {0}"), name));
      return;
    }
    created = parent.createChild(nodeName);
    getStage().close();
  }

}
