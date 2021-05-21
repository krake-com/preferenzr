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

import org.tentackle.bind.Bindable;

import java.util.List;

/**
 * A preference node.
 */
public interface PreferenceNode {

  /**
   * Gets the node's name.
   *
   * @return the name, never null or empty
   */
  @Bindable(ordinal = 1)
  String getName();

  /**
   * Gets the value.
   *
   * @return the value, null if none
   */
  @Bindable(ordinal = 2)
  String getValue();

  /**
   * Gets the optional subnodes of this node.
   *
   * @return the nodes, null if this node has no subnodes
   */
  List<? extends PreferenceNode> getNodes();

  /**
   * Invoked whenever there were changes.
   *
   * @param diff number of modifications to add
   */
  void updateModificationCount(int diff);

  /**
   * Returns the number of modifications made by the user.
   *
   * @return the modification count
   */
  int getModificationCount();

}
