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

package org.tentackle.preferenzr.gui.locale;

import javafx.fxml.FXML;
import javafx.scene.control.RadioButton;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;

import org.tentackle.preferenzr.common.PreferencesRepository;
import org.tentackle.preferenzr.common.PreferencesUpdatedListener;
import org.tentackle.fx.AbstractFxController;
import org.tentackle.fx.Fx;
import org.tentackle.fx.FxControllerService;
import org.tentackle.fx.component.FxButton;
import org.tentackle.fx.component.FxRadioButton;

import java.util.Map;
import java.util.ResourceBundle;
import java.util.TreeMap;

/**
 * Editor for the active locales.
 */
@FxControllerService(binding = FxControllerService.BINDING.NO)
public class LocaleSelector extends AbstractFxController implements PreferencesUpdatedListener {

  @FXML
  private HBox localePane;

  @FXML
  private FxButton addLocaleButton;

  @FXML
  private ResourceBundle resources;

  private final Map<String, FxRadioButton> localeRadioButtonMap = new TreeMap<>();
  private PreferencesRepository repository;


  @FXML
  public void initialize() {
    addLocaleButton.setOnAction(event -> addLocaleDialog());
  }

  @Override
  public void configure() {
    addLocaleButton.setTooltip(new Tooltip(resources.getString("add_locale_tt")));
  }

  /**
   * Sets the preference repository.
   *
   * @param repository the repository
   */
  public void setRepository(PreferencesRepository repository) {
    this.repository = repository;
    repository.removeBundlesUpdatedListener(this);
    repository.addBundlesUpdatedListener(this);
    localeRadioButtonMap.clear();
    for (String locale : repository.getLocales()) {
      addLocaleRadioButton(locale);
    }
    updateLocalePane();
    preferencesUpdated(repository.getModificationCount(), true);
  }

  @Override
  public void preferencesUpdated(int modificationCount, boolean reloaded) {
    // disable adding/activating locales as long as modifications are pending since this will trigger a reload of the repository
    boolean disable = modificationCount != 0;
    addLocaleButton.setDisable(disable);
    localeRadioButtonMap.values().forEach(radioButton -> radioButton.setDisable(disable));
  }


  /**
   * Adds a locale radio button.
   *
   * @param locale the locale
   */
  private void addLocaleRadioButton(String locale) {
    localeRadioButtonMap.computeIfAbsent(locale, loc -> {
      FxRadioButton radioButton = Fx.createNode(RadioButton.class);
      radioButton.setOnAction(event -> repository.setLocaleActive(radioButton.getBindingPath(), radioButton.isSelected()));
      radioButton.setBindingPath(locale);
      radioButton.setText(locale == "" ? resources.getString("default") : locale);
      radioButton.setSelected(repository.isLocaleActive(locale));
      return radioButton;
    });
  }

  private void updateLocalePane() {
    localePane.getChildren().remove(1, localePane.getChildren().size());
    localePane.getChildren().addAll(localeRadioButtonMap.values());
  }

  /**
   * Shows the dialog to add a locale.
   */
  private void addLocaleDialog() {
    AddLocaleDialog.show(this::addLocale);
  }

  /**
   * Adds a locale.
   *
   * @param locale the locale
   */
  private void addLocale(String locale) {
    if (repository.getLocales().add(locale)) {
      repository.setLocaleActive(locale, true);
      addLocaleRadioButton(locale);
      updateLocalePane();
      preferencesUpdated(repository.getModificationCount(), true);
    }
  }

}
