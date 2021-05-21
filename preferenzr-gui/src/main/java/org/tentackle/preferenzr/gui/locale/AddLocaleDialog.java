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
import javafx.scene.Scene;
import javafx.stage.Modality;
import javafx.stage.Stage;

import org.tentackle.bind.Bindable;
import org.tentackle.common.StringHelper;
import org.tentackle.fx.AbstractFxController;
import org.tentackle.fx.Fx;
import org.tentackle.fx.FxControllerService;
import org.tentackle.fx.component.FxButton;
import org.tentackle.fx.component.FxTextField;

import java.text.MessageFormat;
import java.util.Locale;
import java.util.ResourceBundle;
import java.util.function.Consumer;

/**
 * Dialog to add a new locale.
 */
@FxControllerService
public class AddLocaleDialog extends AbstractFxController {

  /**
   * Shows the add locale dialog.
   *
   * @param localeAdder the consumer invoked when locale was added
   */
  public static void show(Consumer<String> localeAdder) {
    Stage stage = Fx.createStage(Modality.NONE);
    AddLocaleDialog dialog = Fx.load(AddLocaleDialog.class);
    dialog.setLocaleAdder(localeAdder);
    stage.setTitle(dialog.resources.getString("add locale"));
    Scene scene = Fx.createScene(dialog.getView());
    stage.setScene(scene);
    stage.show();
  }

  @Bindable
  private String locale;

  @FXML
  private FxTextField localeField;

  @FXML
  private FxButton cancelButton;

  @FXML
  private FxButton addButton;

  @FXML
  private ResourceBundle resources;

  private Consumer<String> localeAdder;


  @FXML
  private void initialize() {
    cancelButton.setOnAction(event -> getStage().close());
    addButton.setOnAction(event -> addLocale());
    localeField.setOnAction(event -> addButton.doClick());
  }

  public void setLocaleAdder(Consumer<String> localeAdder) {
    this.localeAdder = localeAdder;
  }

  private void addLocale() {
    if (!StringHelper.isAllWhitespace(locale)) {
      Locale loc = Locale.forLanguageTag(locale.replace('_', '-'));
      if (loc != null) {
        String language = loc.getLanguage();
        String country = loc.getCountry();
        String variant = loc.getVariant();
        Locale[] availableLocales = Locale.getAvailableLocales();
        Locale found = null;
        for (Locale availableLocale : availableLocales) {
          if (!variant.isEmpty()) {
            if (variant.equals(availableLocale.getVariant()) &&
                country.equals(availableLocale.getCountry()) &&
                language.equals(availableLocale.getLanguage())) {
              found = availableLocale;
              break;
            }
          }
          else if (!country.isEmpty()) {
            if (country.equals(availableLocale.getCountry()) &&
                language.equals(availableLocale.getLanguage())) {
              found = availableLocale;
              break;
            }
          }
          else if (language.equals(availableLocale.getLanguage())) {
            found = availableLocale;
            break;
          }
        }
        if (found != null) {
          Fx.yes(MessageFormat.format(resources.getString("add locale {0}?"), loc), false,
                 () -> localeAdder.accept(loc.toString()));
          getStage().close();
          return;
        }
      }
      Fx.error(MessageFormat.format(resources.getString("invalid locale {0}"), locale));
    }
  }

}
