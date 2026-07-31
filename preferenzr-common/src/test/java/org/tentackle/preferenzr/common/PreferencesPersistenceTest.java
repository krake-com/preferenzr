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

import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import org.tentackle.common.Constants;
import org.tentackle.common.EncryptedProperties;
import org.tentackle.misc.PropertiesUtilities;
import org.tentackle.pdo.DomainContext;
import org.tentackle.pdo.Pdo;
import org.tentackle.session.ModificationTracker;
import org.tentackle.session.Session;
import org.tentackle.session.SessionInfo;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;

/**
 * Tests loading and persisting preferences against an in-memory H2 database.
 * <p>
 * Preferenzr normally works on the database of an existing Tentackle application, so the schema of the
 * {@code prefnode} and {@code prefkey} tables is created here to have something to read and write.
 */
public class PreferencesPersistenceTest {

  private static final String URL = "jdbc:h2:mem:preferenzr;DB_CLOSE_DELAY=-1";
  private static final String USER = "sa";
  private static final String PASSWORD = "";

  private Connection ddlConnection;
  private Session session;
  private DomainContext context;

  @BeforeClass
  public void createSchemaAndSession() throws Exception {
    ddlConnection = DriverManager.getConnection(URL, USER, PASSWORD);
    try (Statement st = ddlConnection.createStatement()) {
      st.execute("CREATE SEQUENCE IF NOT EXISTS object_sequence_id");
      st.execute("""
          CREATE TABLE IF NOT EXISTS modification (
            id BIGINT NOT NULL PRIMARY KEY,
            serial BIGINT NOT NULL,
            tablename VARCHAR(64))
          """);
      st.execute("""
          CREATE TABLE IF NOT EXISTS prefnode (
            id BIGINT NOT NULL PRIMARY KEY,
            serial BIGINT NOT NULL,
            tableserial BIGINT NOT NULL,
            username VARCHAR(32),
            nodename VARCHAR(192) NOT NULL,
            parentid BIGINT NOT NULL,
            rootnodeid BIGINT NOT NULL)
          """);
      st.execute("CREATE UNIQUE INDEX IF NOT EXISTS prefnode_nodename ON prefnode (nodename, username)");
      st.execute("""
          CREATE TABLE IF NOT EXISTS prefkey (
            id BIGINT NOT NULL PRIMARY KEY,
            serial BIGINT NOT NULL,
            tableserial BIGINT NOT NULL,
            nodeid BIGINT NOT NULL,
            rootnodeid BIGINT NOT NULL,
            pkey VARCHAR(128) NOT NULL,
            pvalue VARCHAR(8192))
          """);
      st.execute("CREATE UNIQUE INDEX IF NOT EXISTS prefkey_path ON prefkey (nodeid, pkey)");
    }

    EncryptedProperties props = PropertiesUtilities.getInstance().create();
    props.setProperty(Constants.BACKEND_URL, URL);
    props.setProperty(Constants.BACKEND_USER, USER);
    props.setProperty(Constants.BACKEND_PASSWORD, PASSWORD);
    SessionInfo sessionInfo = Pdo.createSessionInfo(props);
    session = Pdo.createSession(sessionInfo);
    session.makeCurrent();
    // the tracker is not started (as in PreferenzrFxClient), but it needs a session to persist tracked objects
    ModificationTracker.getInstance().setSession(session.clone("MT"));
    context = Pdo.createDomainContext(session);
  }

  @AfterClass
  public void closeSession() throws Exception {
    ModificationTracker.getInstance().terminate();
    if (session != null) {
      session.close();
    }
    if (ddlConnection != null) {
      ddlConnection.close();
    }
  }

  @Test
  public void testCreateEditRemoveRoundTrip() {
    PreferencesRepository repository = new PreferencesRepository(context);
    assertEquals(repository.getPreferences().size(), 0);

    // create a system and a user tree
    PreferenceEntry systemRoot = repository.createRoot(null);
    PreferenceEntry tentackle = repository.resolveEntry(null, "/org/tentackle");
    tentackle.createKey("columnWidth", "120");
    tentackle.createKey("theme", "atlanta");
    repository.resolveEntry("harald", "/org/tentackle").createKey("theme", "dark");
    assertEquals(systemRoot.getState(), PreferenceState.NEW);
    repository.persist();

    // read back from storage
    repository.invalidate();
    assertEquals(repository.getPreferences().size(), 2);
    assertEquals(repository.getModificationCount(), 0);
    assertEquals(repository.getUsers(), java.util.List.of("harald"));
    assertEquals(repository.getOrphans().size(), 0);

    PreferenceEntry loaded = repository.findEntry(null, "/org/tentackle");
    assertNotNull(loaded);
    assertEquals(loaded.getKey("columnWidth").getValue(), "120");
    assertEquals(loaded.getKey("theme").getValue(), "atlanta");
    assertEquals(repository.findEntry("harald", "/org/tentackle").getKey("theme").getValue(), "dark");
    // the parent chain got proper ids
    assertEquals(loaded.getPersistentNode().getParentId(), repository.findEntry(null, "/org").getPersistentNode().getId());
    assertEquals(loaded.getPersistentNode().getRootNodeId(), repository.getRoot(null).getPersistentNode().getId());

    // edit a value
    loaded.getKey("columnWidth").setValue("240");
    assertEquals(repository.getModificationCount(), 1);
    repository.persist();
    repository.invalidate();
    assertEquals(repository.findEntry(null, "/org/tentackle").getKey("columnWidth").getValue(), "240");

    // remove a key
    repository.findEntry(null, "/org/tentackle").getKey("theme").remove();
    repository.persist();
    repository.invalidate();
    assertNull(repository.findEntry(null, "/org/tentackle").getKey("theme"));
    assertNotNull(repository.findEntry(null, "/org/tentackle").getKey("columnWidth"));

    // remove a whole subtree
    repository.findEntry(null, "/org").remove();
    repository.persist();
    repository.invalidate();
    assertNull(repository.findEntry(null, "/org"));
    assertNotNull(repository.getRoot(null));
    assertNotNull(repository.findEntry("harald", "/org/tentackle"));   // the user tree is untouched
    assertEquals(repository.getOrphans().size(), 0);
  }

  @Test(dependsOnMethods = "testCreateEditRemoveRoundTrip")
  public void testDumpAndLoadRoundTrip() throws Exception {
    PreferencesRepository repository = new PreferencesRepository(context);

    ByteArrayOutputStream os = new ByteArrayOutputStream();
    PreferencesDump.write(os, repository.findEntry("harald", "/org/tentackle"));

    PreferencesDump.DumpedTree tree = PreferencesDump.read(new ByteArrayInputStream(os.toByteArray()));
    assertEquals(tree.getBasePath(), "/org/tentackle");

    // load the user's subtree into the system preferences and save it
    repository.importDump(tree, null, PreferencesDump.MergeMode.MERGE);
    repository.persist();
    repository.invalidate();

    PreferenceEntry imported = repository.findEntry(null, "/org/tentackle");
    assertNotNull(imported);
    assertEquals(imported.getKey("theme").getValue(), "dark");
  }

}
