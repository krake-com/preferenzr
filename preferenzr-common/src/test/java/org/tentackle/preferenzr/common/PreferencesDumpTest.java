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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;
import java.util.prefs.Preferences;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

/**
 * Tests for reading and writing preference dumps in the standard {@link Preferences} XML format.
 */
public class PreferencesDumpTest extends AbstractRepositoryTest {

  private static final String JDK_EXPORT = """
      <?xml version="1.0" encoding="UTF-8" standalone="no"?>
      <!DOCTYPE preferences SYSTEM "http://java.sun.com/dtd/preferences.dtd">
      <preferences EXTERNAL_XML_VERSION="1.0">
        <root type="user">
          <map/>
          <node name="org">
            <map/>
            <node name="tentackle">
              <map>
                <entry key="columnWidth" value="120"/>
                <entry key="visible" value="true"/>
              </map>
              <node name="fx">
                <map>
                  <entry key="theme" value="atlanta"/>
                </map>
              </node>
            </node>
          </node>
        </root>
      </preferences>
      """;

  @Test
  public void testReadJdkExport() throws IOException {
    PreferencesDump.DumpedTree tree = PreferencesDump.read(toStream(JDK_EXPORT));
    assertFalse(tree.systemScope());
    assertEquals(tree.keysByPath().keySet(), Set.of("/", "/org", "/org/tentackle", "/org/tentackle/fx"));
    assertEquals(tree.keysByPath().get("/"), Map.of());
    assertEquals(tree.keysByPath().get("/org/tentackle"), Map.of("columnWidth", "120", "visible", "true"));
    assertEquals(tree.keysByPath().get("/org/tentackle/fx"), Map.of("theme", "atlanta"));
  }

  @Test
  public void testBasePathIsTheOriginallyDumpedNode() throws IOException {
    // the ancestors "/" and "/org" hold no keys and have a single child each
    assertEquals(PreferencesDump.read(toStream(JDK_EXPORT)).getBasePath(), "/org/tentackle");
  }

  @Test
  public void testBasePathOfAWholeTree() throws IOException {
    long root = node(0, "harald", "/");
    node(root, "harald", "/org");
    node(root, "harald", "/com");

    // more than one child below the root: the whole tree was dumped
    assertEquals(readBack(build().findEntry("harald", "/")).getBasePath(), "/");
  }

  @Test
  public void testWriteAndReadBack() throws IOException {
    long root = node(0, null, "/");
    key(root, "rootKey", "rootValue");
    long org = node(root, null, "/org");
    long tentackle = node(org, null, "/org/tentackle");
    key(tentackle, "columnWidth", "120");
    key(tentackle, "multiLine", "first\nsecond");
    key(tentackle, "umlauts", "Grüße & <Zeichen>");

    PreferencesDump.DumpedTree tree = readBack(build().findEntry(null, "/"));
    assertTrue(tree.systemScope());
    assertEquals(tree.keysByPath().get("/"), Map.of("rootKey", "rootValue"));
    assertEquals(tree.keysByPath().get("/org"), Map.of());
    assertEquals(tree.keysByPath().get("/org/tentackle"),
                 Map.of("columnWidth", "120", "multiLine", "first\nsecond", "umlauts", "Grüße & <Zeichen>"));
    assertEquals(tree.getBasePath(), "/");     // the root itself holds a key
  }

  @Test
  public void testWriteASubtree() throws IOException {
    long root = node(0, "harald", "/");
    long org = node(root, "harald", "/org");
    long tentackle = node(org, "harald", "/org/tentackle");
    key(tentackle, "theme", "atlanta");

    // dumping a subtree writes its ancestors as empty nodes, so it can be re-imported at the same path
    PreferencesDump.DumpedTree tree = readBack(build().findEntry("harald", "/org/tentackle"));
    assertFalse(tree.systemScope());
    assertEquals(tree.keysByPath().get("/"), Map.of());
    assertEquals(tree.keysByPath().get("/org"), Map.of());
    assertEquals(tree.keysByPath().get("/org/tentackle"), Map.of("theme", "atlanta"));
    assertEquals(tree.getBasePath(), "/org/tentackle");
  }

  @Test
  public void testRemovedNodesAndKeysAreNotDumped() throws IOException {
    long root = node(0, null, "/");
    long org = node(root, null, "/org");
    key(org, "kept", "1");
    key(org, "gone", "2");
    node(org, null, "/org/gone");
    node(org, null, "/org/kept");

    PreferencesRepository repository = build();
    repository.findEntry(null, "/org").getKey("gone").remove();
    repository.findEntry(null, "/org/gone").remove();

    PreferencesDump.DumpedTree tree = readBack(repository.findEntry(null, "/"));
    assertEquals(tree.keysByPath().get("/org"), Map.of("kept", "1"));
    assertNotNull(tree.keysByPath().get("/org/kept"));
    assertFalse(tree.keysByPath().containsKey("/org/gone"));
  }

  @Test
  public void testDumpIsReadableByTheJdk() throws Exception {
    long root = node(0, "harald", "/");
    long org = node(root, "harald", "/preferenzrtest");
    key(org, "jdkKey", "jdkValue");

    ByteArrayOutputStream os = new ByteArrayOutputStream();
    PreferencesDump.write(os, build().findEntry("harald", "/"));

    // the JDK accepts the dump and stores it in the local java.util.prefs tree
    Preferences.importPreferences(new ByteArrayInputStream(os.toByteArray()));
    Preferences imported = Preferences.userRoot().node("preferenzrtest");
    try {
      assertEquals(imported.get("jdkKey", null), "jdkValue");
    }
    finally {
      imported.removeNode();
      Preferences.userRoot().flush();
    }
  }

  @Test
  public void testReadRejectsForeignXml() {
    try {
      PreferencesDump.read(toStream("<?xml version=\"1.0\"?><something><else/></something>"));
      fail("a foreign XML document must be rejected");
    }
    catch (IOException ex) {
      assertTrue(ex.getMessage().contains("not a preferences dump"), ex.getMessage());
    }
  }

  /**
   * Writes an entry and reads it back.
   *
   * @param entry the entry to dump
   * @return the dumped tree
   * @throws IOException if writing or reading failed
   */
  private PreferencesDump.DumpedTree readBack(PreferenceEntry entry) throws IOException {
    ByteArrayOutputStream os = new ByteArrayOutputStream();
    PreferencesDump.write(os, entry);
    return PreferencesDump.read(new ByteArrayInputStream(os.toByteArray()));
  }

  /**
   * Creates a stream over an XML document.
   *
   * @param xml the document
   * @return the stream
   */
  private InputStream toStream(String xml) {
    return new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8));
  }

}
