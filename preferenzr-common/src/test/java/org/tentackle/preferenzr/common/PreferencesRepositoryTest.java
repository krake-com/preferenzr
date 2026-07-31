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

import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.List;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

/**
 * Tests for building and modifying the preference trees.
 */
public class PreferencesRepositoryTest extends AbstractRepositoryTest {

  @Test
  public void testSystemAndUserTrees() {
    long systemRoot = node(0, null, "/");
    long systemOrg = node(systemRoot, null, "/org");
    node(systemOrg, null, "/org/tentackle");
    key(systemOrg, "orgKey", "orgValue");

    long haraldRoot = node(0, "harald", "/");
    node(haraldRoot, "harald", "/org");

    node(0, "anna", "/");

    PreferencesRepository repository = build();
    List<? extends PreferenceNode> roots = new ArrayList<>(repository.getPreferences());
    assertEquals(roots.size(), 3);

    // the system preferences come first, then the users in alphabetical order
    assertNull(roots.get(0).getUser());
    assertEquals(roots.get(1).getUser(), "anna");
    assertEquals(roots.get(2).getUser(), "harald");

    PreferenceEntry system = (PreferenceEntry) roots.get(0);
    assertEquals(system.getPath(), "/");
    assertTrue(system.isRoot());

    PreferenceEntry org = system.getChild("org");
    assertNotNull(org);
    assertEquals(org.getPath(), "/org");
    assertEquals(org.getName(), "org");
    assertNotNull(org.getChild("tentackle"));

    // sub-nodes come before the keys
    List<? extends PreferenceNode> orgNodes = org.getNodes();
    assertEquals(orgNodes.size(), 2);
    assertEquals(orgNodes.get(0).getName(), "tentackle");
    assertEquals(orgNodes.get(1).getName(), "orgKey");
    assertEquals(orgNodes.get(1).getValue(), "orgValue");
    assertNull(orgNodes.get(1).getNodes());     // a key is always a leaf
    assertEquals(orgNodes.get(1).getPath(), "/org");

    assertTrue(repository.getOrphans().isEmpty());
    assertEquals(repository.getUsers(), List.of("anna", "harald"));
    assertEquals(repository.getModificationCount(), 0);
  }

  @Test
  public void testFindEntry() {
    long root = node(0, "harald", "/");
    long org = node(root, "harald", "/org");
    node(org, "harald", "/org/tentackle");

    PreferencesRepository repository = build();
    assertEquals(repository.findEntry("harald", "/org/tentackle").getPath(), "/org/tentackle");
    assertEquals(repository.findEntry("harald", "/").getPath(), "/");
    assertNull(repository.findEntry("harald", "/org/nowhere"));
    assertNull(repository.findEntry(null, "/org"));       // system space is empty
    assertNull(repository.findEntry("anna", "/"));
  }

  @Test
  public void testOrphansAreDetected() {
    long root = node(0, null, "/");
    node(root, null, "/org");
    node(4711, null, "/lost");               // parent does not exist
    key(4712, "lostKey", "lostValue");       // node does not exist

    PreferencesRepository repository = build();
    assertEquals(repository.getPreferences().size(), 1);
    assertEquals(repository.getOrphans().size(), 2);
  }

  @Test
  public void testModificationCountPropagatesToTheRoot() {
    long root = node(0, null, "/");
    long org = node(root, null, "/org");
    key(org, "key", "value");

    PreferencesRepository repository = build();
    PreferenceEntry systemRoot = repository.getRoot(null);
    PreferenceValue value = systemRoot.getChild("org").getKey("key");

    value.setValue("changed");
    assertEquals(value.getState(), PreferenceState.MODIFIED);
    assertEquals(repository.getModificationCount(), 1);
    assertEquals(systemRoot.getModificationCount(), 1);

    // back to the original value: not modified anymore
    value.setValue("value");
    assertEquals(value.getState(), PreferenceState.UNCHANGED);
    assertEquals(repository.getModificationCount(), 0);

    value.remove();
    assertEquals(value.getState(), PreferenceState.REMOVED);
    assertEquals(repository.getModificationCount(), 1);

    value.revive();
    assertEquals(value.getState(), PreferenceState.UNCHANGED);
    assertEquals(repository.getModificationCount(), 0);
  }

  @Test
  public void testRemovingANodeRemovesItsSubtree() {
    long root = node(0, null, "/");
    long org = node(root, null, "/org");
    long tentackle = node(org, null, "/org/tentackle");
    key(tentackle, "key", "value");

    PreferencesRepository repository = build();
    PreferenceEntry orgEntry = repository.findEntry(null, "/org");
    orgEntry.remove();

    assertEquals(orgEntry.getState(), PreferenceState.REMOVED);
    assertEquals(orgEntry.getChild("tentackle").getState(), PreferenceState.REMOVED);
    assertEquals(orgEntry.getChild("tentackle").getKey("key").getState(), PreferenceState.REMOVED);
    assertEquals(repository.getModificationCount(), 3);
  }

  @Test
  public void testCreatedNodesAreDroppedWhenRemovedAgain() {
    node(0, null, "/");

    PreferencesRepository repository = build();
    PreferenceEntry systemRoot = repository.getRoot(null);
    PreferenceEntry org = systemRoot.createChild("org");
    org.createKey("key", "value");

    assertEquals(org.getState(), PreferenceState.NEW);
    assertEquals(org.getPath(), "/org");
    assertEquals(repository.getModificationCount(), 2);

    // a node that was never persisted vanishes instead of being marked for deletion
    org.remove();
    assertNull(systemRoot.getChild("org"));
    assertEquals(repository.getModificationCount(), 0);
  }

  @Test
  public void testImportDumpMerges() throws Exception {
    long root = node(0, null, "/");
    long org = node(root, null, "/org");
    key(org, "keep", "original");
    key(org, "overwrite", "original");

    PreferencesRepository repository = build();
    repository.importDump(dump("/org", "overwrite", "fromDump"), null, PreferencesDump.MergeMode.MERGE);

    PreferenceEntry orgEntry = repository.findEntry(null, "/org");
    assertEquals(orgEntry.getKey("keep").getValue(), "original");
    assertEquals(orgEntry.getKey("keep").getState(), PreferenceState.UNCHANGED);
    assertEquals(orgEntry.getKey("overwrite").getValue(), "fromDump");
    assertEquals(orgEntry.getKey("overwrite").getState(), PreferenceState.MODIFIED);
  }

  @Test
  public void testImportDumpReplaces() throws Exception {
    long root = node(0, null, "/");
    long org = node(root, null, "/org");
    key(org, "keep", "original");
    key(org, "overwrite", "original");

    PreferencesRepository repository = build();
    repository.importDump(dump("/org", "overwrite", "fromDump"), null, PreferencesDump.MergeMode.REPLACE);

    PreferenceEntry orgEntry = repository.findEntry(null, "/org");
    // the key not contained in the dump is gone, the dumped one survives
    assertEquals(orgEntry.getKey("keep").getState(), PreferenceState.REMOVED);
    assertEquals(orgEntry.getKey("overwrite").getValue(), "fromDump");
    assertEquals(orgEntry.getKey("overwrite").getState(), PreferenceState.MODIFIED);
    assertEquals(orgEntry.getState(), PreferenceState.UNCHANGED);   // the node itself was revived
  }

  @Test
  public void testImportDumpIntoAnotherScope() throws Exception {
    node(0, null, "/");

    PreferencesRepository repository = build();
    repository.importDump(dump("/org/tentackle", "theme", "atlanta"), "anna", PreferencesDump.MergeMode.MERGE);

    PreferenceEntry annaRoot = repository.getRoot("anna");
    assertNotNull(annaRoot);
    assertEquals(annaRoot.getState(), PreferenceState.NEW);
    PreferenceEntry imported = repository.findEntry("anna", "/org/tentackle");
    assertNotNull(imported);
    assertEquals(imported.getKey("theme").getValue(), "atlanta");
    assertEquals(repository.getUsers(), List.of("anna"));
  }

  /**
   * Creates a dumped tree holding a single key.
   *
   * @param path the absolute path of the node
   * @param key the key name
   * @param value the value
   * @return the dumped tree
   * @throws Exception if the dump could not be created
   */
  private PreferencesDump.DumpedTree dump(String path, String key, String value) throws Exception {
    StringBuilder buf = new StringBuilder("<preferences EXTERNAL_XML_VERSION=\"1.0\"><root type=\"system\"><map/>");
    int depth = 0;
    for (String name : path.split("/")) {
      if (!name.isEmpty()) {
        buf.append("<node name=\"").append(name).append("\"><map/>");
        depth++;
      }
    }
    buf.setLength(buf.length() - "<map/>".length());
    buf.append("<map><entry key=\"").append(key).append("\" value=\"").append(value).append("\"/></map>");
    buf.append("</node>".repeat(depth)).append("</root></preferences>");
    return PreferencesDump.read(new java.io.ByteArrayInputStream(buf.toString().getBytes("UTF-8")));
  }

}
