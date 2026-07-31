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

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.StringTokenizer;

/**
 * Reads and writes preference trees in the standard XML format of {@link java.util.prefs.Preferences}.
 * <p>
 * Dumps written by this class can be fed into any plain JDK application via
 * {@link java.util.prefs.Preferences#importPreferences(InputStream)} and, vice versa, files written by
 * {@code Preferences.exportSubtree()} can be loaded into Preferenzr.
 */
public class PreferencesDump {

  /** the system id of the preferences DTD. */
  public static final String PREFS_DTD_URI = "http://java.sun.com/dtd/preferences.dtd";

  /** the external XML version written and understood by this class. */
  public static final String EXTERNAL_XML_VERSION = "1.0";

  /** how a dump is applied to an existing preferences tree. */
  public enum MergeMode {

    /** add and overwrite the dumped keys, leave everything else untouched. */
    MERGE,

    /** remove the dumped subtree from the target scope before applying the dump. */
    REPLACE
  }

  /**
   * A preference tree read from a dump file.
   *
   * @param systemScope true if the dump was taken from the system preferences
   * @param keysByPath the keys of each dumped node by absolute path, parents before their children
   */
  public record DumpedTree(boolean systemScope, Map<String, Map<String, String>> keysByPath) {

    /**
     * Gets the path of the node that was originally dumped.<br>
     * A dump always starts at the root of a tree, with the ancestors of the dumped node holding no keys.
     * This method walks down that chain of single childless nodes again.
     *
     * @return the absolute path, {@link PreferencesRepository#ROOT_PATH} if the whole tree was dumped
     */
    public String getBasePath() {
      String basePath = PreferencesRepository.ROOT_PATH;
      for (;;) {
        Map<String, String> keys = keysByPath.get(basePath);
        if (keys != null && !keys.isEmpty()) {
          return basePath;
        }
        String onlyChild = null;
        for (String path : keysByPath.keySet()) {
          if (isChildPath(basePath, path)) {
            if (onlyChild != null) {
              return basePath;    // more than one child: stop here
            }
            onlyChild = path;
          }
        }
        if (onlyChild == null) {
          return basePath;
        }
        basePath = onlyChild;
      }
    }

    /**
     * Returns whether a path is a direct child of another path.
     *
     * @param parentPath the parent's absolute path
     * @param path the absolute path to check
     * @return true if path is a direct child of parentPath
     */
    private boolean isChildPath(String parentPath, String path) {
      String prefix = parentPath.endsWith("/") ? parentPath : parentPath + "/";
      return path.length() > prefix.length() &&
             path.startsWith(prefix) &&
             path.indexOf('/', prefix.length()) < 0;
    }
  }

  /**
   * Writes a preference entry and all of its sub-nodes and keys to a stream.<br>
   * Nodes and keys marked for deletion are not written. The ancestors of the entry are written as empty nodes,
   * exactly as the JDK does, so that the dump can be re-imported at the same path.
   *
   * @param os the output stream
   * @param entry the entry to dump
   * @throws IOException if the dump could not be written
   */
  public static void write(OutputStream os, PreferenceEntry entry) throws IOException {
    try {
      Document doc = createDocumentBuilder().newDocument();
      Element preferences = (Element) doc.appendChild(doc.createElement("preferences"));
      preferences.setAttribute("EXTERNAL_XML_VERSION", EXTERNAL_XML_VERSION);
      Element root = (Element) preferences.appendChild(doc.createElement("root"));
      root.setAttribute("type", entry.getUser() == null ? "system" : "user");

      // create the chain of empty ancestor nodes down to the dumped entry
      Element parent = root;
      StringTokenizer stok = new StringTokenizer(entry.getPath(), PreferencesRepository.ROOT_PATH);
      while (stok.hasMoreTokens()) {
        parent.appendChild(doc.createElement("map"));    // ancestors hold no keys
        Element node = (Element) parent.appendChild(doc.createElement("node"));
        node.setAttribute("name", stok.nextToken());
        parent = node;
      }
      writeEntry(doc, parent, entry);

      Transformer transformer = TransformerFactory.newInstance().newTransformer();
      transformer.setOutputProperty(OutputKeys.DOCTYPE_SYSTEM, PREFS_DTD_URI);
      transformer.setOutputProperty(OutputKeys.ENCODING, StandardCharsets.UTF_8.name());
      transformer.setOutputProperty(OutputKeys.INDENT, "yes");
      transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
      transformer.transform(new DOMSource(doc), new StreamResult(os));
    }
    catch (ParserConfigurationException | TransformerException ex) {
      throw new IOException("cannot write preferences dump", ex);
    }
  }

  /**
   * Reads a preference tree from a stream.
   *
   * @param is the input stream
   * @return the dumped tree
   * @throws IOException if the dump could not be read
   */
  public static DumpedTree read(InputStream is) throws IOException {
    try {
      Document doc = createDocumentBuilder().parse(new InputSource(is));
      Element preferences = doc.getDocumentElement();
      if (!"preferences".equals(preferences.getTagName())) {
        throw new IOException("not a preferences dump: root element is <" + preferences.getTagName() + ">");
      }
      String version = preferences.getAttribute("EXTERNAL_XML_VERSION");
      if (!version.isEmpty() && version.compareTo(EXTERNAL_XML_VERSION) > 0) {
        throw new IOException("unsupported external XML version " + version);
      }
      Element root = getChild(preferences, "root");
      if (root == null) {
        throw new IOException("not a preferences dump: <root> element is missing");
      }
      Map<String, Map<String, String>> keysByPath = new LinkedHashMap<>();
      readNode(root, PreferencesRepository.ROOT_PATH, keysByPath);
      return new DumpedTree("system".equals(root.getAttribute("type")), keysByPath);
    }
    catch (ParserConfigurationException | SAXException ex) {
      throw new IOException("cannot read preferences dump", ex);
    }
  }

  /**
   * Writes an entry with its keys and sub-nodes.
   *
   * @param doc the document
   * @param element the element of the entry
   * @param entry the entry
   */
  private static void writeEntry(Document doc, Element element, PreferenceEntry entry) {
    Element map = (Element) element.appendChild(doc.createElement("map"));
    for (PreferenceValue key : entry.getKeys().values()) {
      if (key.getState() != PreferenceState.REMOVED) {
        Element mapEntry = (Element) map.appendChild(doc.createElement("entry"));
        mapEntry.setAttribute("key", key.getName());
        mapEntry.setAttribute("value", key.getValue() == null ? "" : key.getValue());
      }
    }
    for (PreferenceEntry child : entry.getChildren().values()) {
      if (child.getState() != PreferenceState.REMOVED) {
        Element childElement = (Element) element.appendChild(doc.createElement("node"));
        childElement.setAttribute("name", child.getName());
        writeEntry(doc, childElement, child);
      }
    }
  }

  /**
   * Reads a node element with its keys and sub-nodes.
   *
   * @param element the {@code root} or {@code node} element
   * @param path the absolute path of the element
   * @param keysByPath collects the keys by path
   * @throws IOException if a sub-node has no name
   */
  private static void readNode(Element element, String path, Map<String, Map<String, String>> keysByPath)
         throws IOException {

    Map<String, String> keys = new LinkedHashMap<>();
    Element map = getChild(element, "map");
    if (map != null) {
      NodeList entries = map.getChildNodes();
      for (int i = 0; i < entries.getLength(); i++) {
        Node node = entries.item(i);
        if (node instanceof Element mapEntry && "entry".equals(mapEntry.getTagName())) {
          keys.put(mapEntry.getAttribute("key"), mapEntry.getAttribute("value"));
        }
      }
    }
    keysByPath.put(path, keys);

    NodeList children = element.getChildNodes();
    for (int i = 0; i < children.getLength(); i++) {
      Node node = children.item(i);
      if (node instanceof Element child && "node".equals(child.getTagName())) {
        String name = child.getAttribute("name");
        if (name.isEmpty()) {
          throw new IOException("node below " + path + " has no name");
        }
        readNode(child, path.endsWith("/") ? path + name : path + "/" + name, keysByPath);
      }
    }
  }

  /**
   * Gets the first child element with the given tag name.
   *
   * @param element the parent element
   * @param tagName the tag name
   * @return the child element, null if none
   */
  private static Element getChild(Element element, String tagName) {
    NodeList children = element.getChildNodes();
    for (int i = 0; i < children.getLength(); i++) {
      Node node = children.item(i);
      if (node instanceof Element child && tagName.equals(child.getTagName())) {
        return child;
      }
    }
    return null;
  }

  /**
   * Creates a document builder that never tries to load the preferences DTD from the network.
   *
   * @return the document builder
   * @throws ParserConfigurationException if the parser could not be configured
   */
  private static DocumentBuilder createDocumentBuilder() throws ParserConfigurationException {
    DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
    factory.setIgnoringElementContentWhitespace(true);
    DocumentBuilder builder = factory.newDocumentBuilder();
    builder.setEntityResolver((publicId, systemId) ->
        new InputSource(new ByteArrayInputStream(new byte[0])));
    return builder;
  }

  private PreferencesDump() {
  }

}
