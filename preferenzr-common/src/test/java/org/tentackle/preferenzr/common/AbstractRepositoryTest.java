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

import org.testng.annotations.BeforeMethod;

import org.tentackle.dbms.prefs.DbPreferencesKey;
import org.tentackle.dbms.prefs.DbPreferencesNode;

import java.util.ArrayList;
import java.util.List;

/**
 * Base class for repository tests.<br>
 * Builds a {@link PreferencesRepository} from hand-made persistent objects, i.e. without a database.
 */
public abstract class AbstractRepositoryTest {

  private final List<DbPreferencesNode> nodes = new ArrayList<>();
  private final List<DbPreferencesKey> keys = new ArrayList<>();
  private long nextId;

  @BeforeMethod
  public void clearPersistentObjects() {
    nodes.clear();
    keys.clear();
    nextId = 1;
  }

  /**
   * Builds the repository from the nodes and keys created so far.
   *
   * @return the repository
   */
  protected PreferencesRepository build() {
    PreferencesRepository repository = new PreferencesRepository((org.tentackle.pdo.DomainContext) null);
    repository.buildTrees(nodes, keys);
    return repository;
  }

  /**
   * Creates a persistent node.
   *
   * @param parentId the ID of the parent node, 0 if root
   * @param user the username, null if system
   * @param path the absolute path
   * @return the ID of the created node
   */
  protected long node(long parentId, String user, String path) {
    DbPreferencesNode node = new DbPreferencesNode();
    node.setId(nextId++);
    node.setParentId(parentId);
    node.setUser(user);
    node.setName(path);
    nodes.add(node);
    return node.getId();
  }

  /**
   * Creates a persistent key.
   *
   * @param nodeId the ID of the node the key belongs to
   * @param name the key name
   * @param value the value
   */
  protected void key(long nodeId, String name, String value) {
    DbPreferencesKey key = new DbPreferencesKey();
    key.setId(nextId++);
    key.setNodeId(nodeId);
    key.setKey(name);
    key.setValue(value);
    keys.add(key);
  }

}
