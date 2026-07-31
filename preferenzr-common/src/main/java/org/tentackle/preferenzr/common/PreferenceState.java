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

/**
 * The pending modification of a preference node.<br>
 * Determines how a node is rendered and what happens to it when the model is persisted.
 */
public enum PreferenceState {

  /** in sync with the database. */
  UNCHANGED,

  /** created by the user, not persisted yet. */
  NEW,

  /** value changed by the user. */
  MODIFIED,

  /** marked for deletion by the user. */
  REMOVED

}
