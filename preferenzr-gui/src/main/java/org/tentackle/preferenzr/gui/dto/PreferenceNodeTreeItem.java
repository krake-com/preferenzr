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

package org.tentackle.preferenzr.gui.dto;

import javafx.collections.ObservableList;
import javafx.scene.control.TreeItem;

import org.tentackle.preferenzr.common.PreferenceNode;

import java.util.ArrayList;
import java.util.List;

/**
 * Tree item for a {@link PreferenceNode}.
 *
 * @param <T> the node type
 */
public class PreferenceNodeTreeItem<T extends PreferenceNode> extends TreeItem<T> {

  private boolean childrenLoaded;

  public PreferenceNodeTreeItem(T node) {
    super(node);
  }

  @Override
  public boolean isLeaf() {
    return getValue() == null || getValue().getNodes() == null;
  }

  @Override
  @SuppressWarnings({ "unchecked", "rawtypes" })
  public ObservableList<TreeItem<T>> getChildren() {
    if (!childrenLoaded) {
      childrenLoaded = true;
      List<TreeItem<T>> childItems = new ArrayList<>();
      if (getValue() != null) {
        List<? extends PreferenceNode> nodes = getValue().getNodes();
        if (nodes != null) {
          for (PreferenceNode node : nodes) {
            childItems.add(new PreferenceNodeTreeItem(node));
          }
        }
      }
      super.getChildren().setAll(childItems);
    }
    return super.getChildren();
  }

}
