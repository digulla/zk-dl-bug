package org.zkoss.zk.ui.metainfo;

import static org.junit.Assert.*;

import java.io.IOException;
import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Field;
import java.net.URL;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;

import javax.xml.parsers.ParserConfigurationException;

import org.junit.BeforeClass;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.SAXException;
import org.zkoss.idom.Document;
import org.zkoss.idom.Element;
import org.zkoss.idom.input.SAXBuilder;
import org.zkoss.idom.util.IDOMs;
import org.zkoss.lang.Classes;
import org.zkoss.lang.reflect.Fields;
import org.zkoss.test.definitionloaders.ExtendsCorrectly;
import org.zkoss.test.definitionloaders.SimpleWidget;
import org.zkoss.util.resource.Locator;
import org.zkoss.util.resource.XMLResourcesLocator;
import org.zkoss.util.resource.XMLResourcesLocator.Resource;
import org.zkoss.zk.ui.impl.Utils;
import org.zkoss.zk.ui.sys.ConfigParser;

public class DefinitionLoadersTest {

	private static final String TEXTBOX_ZKBIND = "@ZKBIND(ACCESS=[both], SAVE_EVENT=[onChange], LOAD_REPLACEMENT=[rawValue], LOAD_TYPE=[java.lang.String])";

	private final static Logger LOG = LoggerFactory.getLogger(DefinitionLoadersTest.class);
	
	final XMLResourcesLocator locator = Utils.getXMLResourcesLocator();
	
	@BeforeClass
	public static void resetStaticFieldsInZK() throws Exception {
		// Unfortunately, ZK relies heavily on global variables :-(
		// Note: You can't run these tests in parallel!
		clearMap(LanguageDefinition.class, "_ldefByName");
		clearMap(LanguageDefinition.class, "_ldefsByExt");
		clearMap(LanguageDefinition.class, "_ldefsByClient");
		clearMap(LanguageDefinition.class, "_wgtdefs");
		
		setBoolean(DefinitionLoaders.class, "_loading", true);
		setBoolean(DefinitionLoaders.class, "_loaded", false);
		
		assertNull(peekZul());
	}
	
	private static void setBoolean(Class<?> type, String fieldName, boolean value) throws Exception {
		final AccessibleObject acs = getPrivateField(type, fieldName);
		((Field)acs).set(null, value);
	}

	private static void clearMap(Class<LanguageDefinition> type, String fieldName) throws Exception {
		Map<Object, Object> map = getStaticMap(type, fieldName);
		map.clear();
	}

	private static <K,V> Map<K, V> getStaticMap(Class<?> type, String fieldName)
			throws NoSuchMethodException, IllegalAccessException {
		final AccessibleObject acs = getPrivateField(type, fieldName);
		
		@SuppressWarnings("unchecked")
		Map<K, V> map = (Map<K, V>) ((Field)acs).get(null);
		return map;
	}

	private static AccessibleObject getPrivateField(Class<?> type, String fieldName) throws NoSuchMethodException {
		final AccessibleObject acs = Classes.getAccessibleObject(
				type, fieldName, null,
				Classes.B_GET);
		Fields.setAccessible(acs, true);
		return acs;
	}
	
	private static LanguageDefinition peekLanguageDefinition(String name) throws Exception {
		// Using LanguageDefinition.lookup() would trigger DefinitionLoaders.load() which would corrupt the data structures
		Map<String, LanguageDefinition> ldefByName = getStaticMap(LanguageDefinition.class, "_ldefByName");
		return ldefByName.get(name);
	}

	private static LanguageDefinition peekZul() throws Exception {
		return peekLanguageDefinition("xul/html");
	}

	@Test
	public void testSimpleWidget() throws Exception {
		loadSystemConfig();
		loadLangXml();
		loadLang("simple-widgets.xml");
		
		LanguageDefinition zul = peekZul();
		ComponentDefinition compdef = zul.getComponentDefinition("simpleWidget");
		assertEquals(SimpleWidget.class, compdef.getImplementationClass());
	}
	
	@Test
	public void testTextboxAnnotations() throws Exception {
		loadSystemConfig();
		loadLangXml();
		loadZkbind();
		
		LanguageDefinition zul = peekZul();
		ComponentDefinition label = zul.getComponentDefinition("textbox");
		assertNotNull(label.getAnnotationMap());
		assertEquals(TEXTBOX_ZKBIND, label.getAnnotationMap().getAnnotation("value", "ZKBIND").toString());
	}

	@Test
	public void testExtendsCorrectly() throws Exception {
		loadSystemConfig();
		loadLangXml();
		loadZkbind();
		
		LanguageDefinition zul = peekZul();

		ComponentDefinition label = zul.getComponentDefinition("textbox");
		assertNotNull(label.getAnnotationMap());
		assertEquals(TEXTBOX_ZKBIND, label.getAnnotationMap().getAnnotation("value", "ZKBIND").toString());
		try {
			zul.getComponentDefinition("extendsCorrectly");
			
			fail("Somehow, configuration leaked into the test");
		} catch(DefinitionNotFoundException e) {
			// correct
		}
		
		loadLang("extends-correctly.xml");

		ComponentDefinition compdef = zul.getComponentDefinition("extendsCorrectly");
		assertEquals(ExtendsCorrectly.class, compdef.getImplementationClass());
		assertNotNull(compdef.getAnnotationMap());
		assertEquals(TEXTBOX_ZKBIND, compdef.getAnnotationMap().getAnnotation("value", "ZKBIND").toString());
	}

	@Test
	public void testMissingDependsLucky() throws Exception {
		loadSystemConfig();
		loadLangXml();
		// If we're lucky, zkbind will be early in the classpath. Then, things seem to work
		loadZkbind();
		loadLang("missing-depends.xml");

		LanguageDefinition zul = peekZul();
		ComponentDefinition label = zul.getComponentDefinition("textbox");
		assertNotNull(label.getAnnotationMap());
		assertEquals(TEXTBOX_ZKBIND, label.getAnnotationMap().getAnnotation("value", "ZKBIND").toString());

		ComponentDefinition compdef = zul.getComponentDefinition("missingDepends");
		assertEquals(ExtendsCorrectly.class, compdef.getImplementationClass());
		assertNotNull(compdef.getAnnotationMap());
		assertEquals(TEXTBOX_ZKBIND, compdef.getAnnotationMap().getAnnotation("value", "ZKBIND").toString());
	}

	@Test
	public void testMissingDependsBroken() throws Exception {
		loadSystemConfig();
		loadLangXml();
		// If we're *not* lucky, zkbind will be on the classpath after the extension. Now, the annotations are missing!
		loadLang("missing-depends.xml");
		loadZkbind();

		LanguageDefinition zul = peekZul();
		ComponentDefinition label = zul.getComponentDefinition("textbox");
		assertNotNull(label.getAnnotationMap());
		assertEquals(TEXTBOX_ZKBIND, label.getAnnotationMap().getAnnotation("value", "ZKBIND").toString());

		ComponentDefinition compdef = zul.getComponentDefinition("missingDepends");
		assertEquals(ExtendsCorrectly.class, compdef.getImplementationClass());
		assertNull(compdef.getAnnotationMap()); // This is bad!
	}

	private void loadSystemConfig() {
		final ConfigParser parser = new ConfigParser();
		parser.parseConfigXml(null); //only system default configs
	}
	
	private void loadLangXml() throws Exception {
		List<URL> urls = Collections.list(locator.getResources("metainfo/zk/lang.xml"));
		for(URL url: urls) {
			loadLang(url, false);
		}
	}
	
	private void loadZkbind() throws Exception {
		loadSystemAddon("zkbind");
	}
	
	private void loadSystemAddon(String name) throws Exception {
		List<URL> urls = Collections.list(locator.getResources("metainfo/zk/lang-addon.xml"));
		for (URL url : urls) {
			if (url.toExternalForm().contains(name)) {
				loadLang(url, true);
				return;
			}
		}
		
		throw new AssertionError("Can't find [" + name + "] in " + urls);
	}

	private void loadLang(String resource) throws Exception {
		String folder = getClass().getSimpleName();
		String path = folder + "/" + resource;
		URL url = getClass().getClassLoader().getResource(path);
		assertNotNull("Resource not found: " + path, url);
		
		loadLang(url, true);
	}

	private void loadLang(URL url, boolean addon) throws Exception {
		LOG.info("Loading {}{}", addon ? "addon " : "", url);
		Document doc;
		try {
			doc = new SAXBuilder(true, false, true).build(url);
		} catch (Exception e) {
			throw new RuntimeException("Error parsing XML " + url, e);
		}
		DefinitionLoaders.parseLang(doc, locator, url, addon);
	}
}
