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

package org.tentackle.preferenzr.gui.load;

import javafx.fxml.FXML;
import javafx.scene.control.ToggleGroup;
import javafx.stage.Modality;
import javafx.stage.Stage;

import org.tentackle.common.StringHelper;
import org.tentackle.fx.AbstractFxController;
import org.tentackle.fx.Fx;
import org.tentackle.fx.FxControllerService;
import org.tentackle.fx.component.FxButton;
import org.tentackle.fx.component.FxComboBox;
import org.tentackle.fx.component.FxLabel;
import org.tentackle.fx.component.FxRadioButton;
import org.tentackle.preferenzr.common.PreferencesDump;
import org.tentackle.preferenzr.common.PreferencesRepository;

import java.text.MessageFormat;
import java.util.Objects;
import java.util.ResourceBundle;

/**
 * Dialog to load a dumped preferences tree into the model.<br>
 * Lets the user pick the target scope and whether the dumped subtree replaces the existing one.
 * The dump is applied to the in-memory model only, i.e. it must be saved explicitly.
 */
@FxControllerService(binding = FxControllerService.BINDING.NO)
public class LoadDumpDialog extends AbstractFxController {

  /**
   * Shows the dialog.
   *
   * @param owner the owner of the dialog
   * @param repository the preferences repository
   * @param fileName the name of the dump file, for display only
   * @param tree the dumped tree
   * @param done invoked if the dump was applied to the model
   */
  public static void show(Object owner, PreferencesRepository repository, String fileName,
                          PreferencesDump.DumpedTree tree, Runnable done) {
    Stage stage = Fx.createStage(Modality.APPLICATION_MODAL, owner);
    LoadDumpDialog dialog = Fx.load(LoadDumpDialog.class);
    dialog.repository = Objects.requireNonNull(repository);
    dialog.tree = Objects.requireNonNull(tree);
    stage.setTitle(dialog.resources.getString("load preferences"));
    stage.setScene(Fx.createScene(dialog.getView()));
    stage.setOnHidden(event -> {
      if (dialog.loaded) {
        done.run();
      }
    });
    Fx.show(stage);
    dialog.setup(fileName);
  }

  @FXML
  private FxLabel fileLabel;

  @FXML
  private FxLabel pathLabel;

  @FXML
  private FxRadioButton systemButton;

  @FXML
  private FxRadioButton userButton;

  @FXML
  private FxComboBox<String> userBox;

  @FXML
  private FxRadioButton mergeButton;

  @FXML
  private FxRadioButton replaceButton;

  @FXML
  private FxButton cancelButton;

  @FXML
  private FxButton loadButton;

  @FXML
  private ResourceBundle resources;

  private PreferencesRepository repository;         // the repository to load into
  private PreferencesDump.DumpedTree tree;          // the dumped tree
  private boolean loaded;                           // true if applied to the model

  @FXML
  private void initialize() {
    ToggleGroup scopeGroup = new ToggleGroup();
    systemButton.setToggleGroup(scopeGroup);
    userButton.setToggleGroup(scopeGroup);
    userBox.setEditable(true);
    userBox.disableProperty().bind(userButton.selectedProperty().not());

    ToggleGroup modeGroup = new ToggleGroup();
    mergeButton.setToggleGroup(modeGroup);
    replaceButton.setToggleGroup(modeGroup);
    mergeButton.setSelected(true);

    cancelButton.setOnAction(event -> getStage().close());
    loadButton.setOnAction(event -> load());
  }

  /**
   * Presets the dialog from the dumped tree.
   *
   * @param fileName the name of the dump file
   */
  private void setup(String fileName) {
    fileLabel.setText(fileName);
    pathLabel.setText(tree.getBasePath());
    userBox.getItems().setAll(repository.getUsers());
    if (tree.systemScope()) {
      systemButton.setSelected(true);
    }
    else {
      userButton.setSelected(true);
      if (!userBox.getItems().isEmpty()) {
        userBox.setValue(userBox.getItems().get(0));
      }
    }
  }

  /**
   * Applies the dump to the model.
   */
  private void load() {
    String user = null;
    if (userButton.isSelected()) {
      user = userBox.getEditor().getText();
      if (StringHelper.isAllWhitespace(user)) {
        user = userBox.getValue();
      }
      if (StringHelper.isAllWhitespace(user)) {
        Fx.error(getView(), resources.getString("please enter the target user"));
        return;
      }
      user = user.trim();
    }
    PreferencesDump.MergeMode mode = replaceButton.isSelected() ? PreferencesDump.MergeMode.REPLACE
                                                                : PreferencesDump.MergeMode.MERGE;
    try {
      repository.importDump(tree, user, mode);
      loaded = true;
    }
    catch (RuntimeException rex) {
      Fx.error(getView(), MessageFormat.format(resources.getString("cannot load the dump into {0}"),
                                               user == null ? resources.getString("the system preferences") : user), rex);
    }
    getStage().close();
  }

}
