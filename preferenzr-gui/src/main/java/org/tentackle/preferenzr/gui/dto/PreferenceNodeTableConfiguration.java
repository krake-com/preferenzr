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

import org.tentackle.preferenzr.common.PreferenceNode;
import org.tentackle.fx.table.DefaultTableConfiguration;

import java.util.function.Predicate;

/**
 * Table configuration for a preference node.
 *
 * @param <T> the node type
 */
public class PreferenceNodeTableConfiguration<T extends PreferenceNode> extends DefaultTableConfiguration<T> {

  private Predicate<T> predicate;    // filters the tree items, null if show all

  public PreferenceNodeTableConfiguration(Class<T> objectClass, String name) {
    super(objectClass, name);
  }

  /**
   * Gets the filter applied to the tree items.
   *
   * @return the predicate, null if all nodes are shown
   */
  public Predicate<T> getPredicate() {
    return predicate;
  }

  /**
   * Sets the filter applied to the tree items.<br>
   * Takes effect when the tree items are rebuilt, i.e. on the next {@code updateView()}.
   *
   * @param predicate the predicate, null to show all nodes
   */
  public void setPredicate(Predicate<T> predicate) {
    this.predicate = predicate;
  }

}
