/*******************************************************************************
 * Copyright (c) 2005 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.core.tests.runtime.perf;

import java.io.*;
import java.net.MalformedURLException;
import java.net.URL;
import junit.framework.Test;
import junit.framework.TestSuite;
import org.eclipse.core.internal.content.*;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.content.*;
import org.eclipse.core.runtime.preferences.InstanceScope;
import org.eclipse.core.tests.harness.*;
import org.eclipse.core.tests.runtime.RuntimeTest;
import org.eclipse.core.tests.runtime.RuntimeTestsPlugin;
import org.eclipse.core.tests.session.PerformanceSessionTestSuite;
import org.eclipse.core.tests.session.SessionTestSuite;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleException;

public class ContentTypePerformanceTest extends RuntimeTest {

	private final static String CONTENT_TYPE_PREF_NODE = Platform.PI_RUNTIME + IPath.SEPARATOR + "content-types"; //$NON-NLS-1$	
	private static final String DEFAULT_NAME = "file_" + ContentTypePerformanceTest.class.getName();
	private static final int ELEMENTS_PER_LEVEL = 4;
	private static final int NUMBER_OF_LEVELS = 4;
	private static final String TEST_DATA_ID = "org.eclipse.core.tests.runtime.contenttype.perf.testdata";
	private static final int TOTAL_NUMBER_OF_ELEMENTS = computeTotalTypes(NUMBER_OF_LEVELS, ELEMENTS_PER_LEVEL);

	private static int computeTotalTypes(int levels, int elementsPerLevel) {
		double sum = 0;
		for (int i = 0; i <= levels; i++)
			sum += Math.pow(elementsPerLevel, i);
		return (int) sum;
	}

	private static String createContentType(Writer writer, int number, String baseTypeId) throws IOException {
		String id = "performance" + number;
		String definition = generateContentType(number, id, baseTypeId, new String[] {DEFAULT_NAME}, null);
		writer.write(definition);
		writer.write(System.getProperty("line.separator"));
		return id;
	}

	public static int createContentTypes(Writer writer, String baseTypeId, int created, int numberOfLevels, int nodesPerLevel) throws IOException {
		if (numberOfLevels == 0)
			return 0;
		int local = nodesPerLevel;
		for (int i = 0; i < nodesPerLevel; i++) {
			String id = createContentType(writer, created + i, baseTypeId);
			local += createContentTypes(writer, id, created + local, numberOfLevels - 1, nodesPerLevel);
		}
		return local;
	}

	private static String generateContentType(int number, String id, String baseTypeId, String[] fileNames, String[] fileExtensions) {
		StringBuffer result = new StringBuffer();
		result.append("<content-type id=\"");
		result.append(id);
		result.append("\" name=\"");
		result.append(id);
		result.append("\" ");
		if (baseTypeId != null) {
			result.append("base-type=\"");
			result.append(baseTypeId);
			result.append("\" ");
		}
		String fileNameList = Util.toListString(fileNames);
		if (fileNameList != null) {
			result.append("file-names=\"");
			result.append(fileNameList);
			result.append("\" ");
		}
		String fileExtensionsList = Util.toListString(fileExtensions);
		if (fileExtensions != null && fileExtensions.length > 0) {
			result.append("file-extensions=\"");
			result.append(fileExtensionsList);
			result.append("\" ");
		}
		result.append("describer=\"");
		result.append(BinarySignatureDescriber.class.getName());
		result.append(":");
		result.append(getSignatureString(number));
		result.append("\"/>");
		return result.toString();
	}

	private static String getContentTypeId(int i) {
		return TEST_DATA_ID + ".performance" + i;
	}

	private static byte[] getSignature(int number) {
		byte[] result = new byte[4];
		for (int i = 0; i < result.length; i++)
			result[i] = (byte) ((number >> (i * 8)) & 0xFFL);
		return result;
	}

	private static String getSignatureString(int number) {
		byte[] signature = getSignature(number);
		StringBuffer result = new StringBuffer(signature.length * 3 - 1);
		for (int i = 0; i < signature.length; i++) {
			result.append(Integer.toHexString(0xFF & signature[i]));
			result.append(' ');
		}
		result.deleteCharAt(result.length() - 1);
		return result.toString();
	}

	public static Test suite() {
		TestSuite suite = new TestSuite(ContentTypePerformanceTest.class.getName());

		//		suite.addTest(new ContentTypePerformanceTest("testDoSetUp"));
		//		suite.addTest(new ContentTypePerformanceTest("testContentMatching"));
		//		suite.addTest(new ContentTypePerformanceTest("testContentTXTMatching"));
		//		suite.addTest(new ContentTypePerformanceTest("testContentXMLMatching"));
		//		suite.addTest(new ContentTypePerformanceTest("testDoTearDown"));

		SessionTestSuite setUp = new SessionTestSuite(PI_RUNTIME_TESTS, "testDoSetUp");
		setUp.addTest(new ContentTypePerformanceTest("testDoSetUp"));
		suite.addTest(setUp);

		TestSuite singleRun = new PerformanceSessionTestSuite(PI_RUNTIME_TESTS, 1, "singleSessionTests");
		singleRun.addTest(new ContentTypePerformanceTest("testContentMatching"));
		singleRun.addTest(new ContentTypePerformanceTest("testNameMatching"));
		singleRun.addTest(new ContentTypePerformanceTest("testIsKindOf"));
		suite.addTest(singleRun);

		TestSuite loadCatalog = new PerformanceSessionTestSuite(PI_RUNTIME_TESTS, 10, "multipleSessionTests");
		loadCatalog.addTest(new ContentTypePerformanceTest("testLoadCatalog"));
		suite.addTest(loadCatalog);

		TestSuite tearDown = new SessionTestSuite(PI_RUNTIME_TESTS, "testDoTearDown");
		tearDown.addTest(new ContentTypePerformanceTest("testDoTearDown"));
		suite.addTest(tearDown);
		return suite;
	}

	public ContentTypePerformanceTest(String name) {
		super(name);
	}

	private int countTestContentTypes(IContentType[] all) {
		String namespace = TEST_DATA_ID + '.';
		int count = 0;
		for (int i = 0; i < all.length; i++)
			if (all[i].getId().startsWith(namespace))
				count++;
		return count;
	}

	private Bundle installContentTypes(String tag, int numberOfLevels, int nodesPerLevel) {
		TestRegistryChangeListener listener = new TestRegistryChangeListener(Platform.PI_RUNTIME, ContentTypeBuilder.PT_CONTENTTYPES, null, null);
		Bundle installed = null;
		listener.register();
		try {
			IPath pluginLocation = getRandomLocation();
			pluginLocation.toFile().mkdirs();
			URL installURL = null;
			try {
				installURL = pluginLocation.toFile().toURL();
			} catch (MalformedURLException e) {
				fail(tag + ".0.5", e);
			}
			Writer writer = null;
			try {
				writer = new BufferedWriter(new FileWriter(pluginLocation.append("plugin.xml").toFile()), 0x10000);
				writer.write("<plugin id=\"" + TEST_DATA_ID + "\" name=\"Content Type Performance Test Data\" version=\"1\">");
				writer.write(System.getProperty("line.separator"));
				writer.write("<requires><import plugin=\"" + PI_RUNTIME_TESTS + "\"/></requires>");
				writer.write(System.getProperty("line.separator"));
				writer.write("<extension point=\"org.eclipse.core.runtime.contentTypes\">");
				writer.write(System.getProperty("line.separator"));
				String root = createContentType(writer, 0, null);
				createContentTypes(writer, root, 1, numberOfLevels, nodesPerLevel);
				writer.write("</extension></plugin>");
			} catch (IOException e) {
				fail(tag + ".1.0", e);
			} finally {
				if (writer != null)
					try {
						writer.close();
					} catch (IOException e) {
						fail("1.1", e);
					}
			}
			try {
				installed = RuntimeTestsPlugin.getContext().installBundle(installURL.toExternalForm());
			} catch (BundleException e) {
				fail(tag + ".3.0", e);
			}
			BundleTestingHelper.refreshPackages(RuntimeTestsPlugin.getContext(), new Bundle[] {installed});
			assertNotNull(tag + ".4.0", listener.getEvent(10000));
		} finally {
			listener.unregister();
		}
		return installed;
	}

	/**
	 * Warms up the content type registry. 
	 */
	private void loadChildren() {
		final IContentTypeManager manager = Platform.getContentTypeManager();
		IContentType[] allTypes = manager.getAllContentTypes();
		for (int i = 0; i < allTypes.length; i++) {
			String[] fileNames = allTypes[i].getFileSpecs(IContentType.IGNORE_USER_DEFINED | IContentType.FILE_NAME_SPEC);
			for (int j = 0; j < fileNames.length; j++)
				manager.findContentTypeFor(fileNames[j]);
			String[] fileExtensions = allTypes[i].getFileSpecs(IContentType.IGNORE_USER_DEFINED | IContentType.FILE_EXTENSION_SPEC);
			for (int j = 0; j < fileExtensions.length; j++)
				manager.findContentTypeFor("anyname." + fileExtensions[j]);
		}
	}

	/**
	 * Returns a loaded content type manager. Except for load time tests, this method should
	 * be called outside the scope of performance monitoring.
	 */
	private IContentTypeManager loadContentTypeManager() {
		// any cheap interaction that causes the catalog to be built				
		Platform.getContentTypeManager().getContentType(IContentTypeManager.CT_TEXT);
		return Platform.getContentTypeManager();
	}

	/** Forces all describers to be loaded.*/
	private void loadDescribers() {
		final IContentTypeManager manager = Platform.getContentTypeManager();
		IContentType[] allTypes = manager.getAllContentTypes();
		for (int i = 0; i < allTypes.length; i++)
			((ContentTypeHandler) allTypes[i]).getTarget().getDescriber();
	}

	private void loadPreferences() {
		new InstanceScope().getNode(CONTENT_TYPE_PREF_NODE);
	}

	/** Tests how much the size of the catalog affects the performance of content type matching by content analysis */
	public void testContentMatching() {
		loadPreferences();
		// warm up content type registry
		final IContentTypeManager manager = loadContentTypeManager();
		loadDescribers();
		loadChildren();
		new PerformanceTestRunner() {
			protected void test() {
				try {
					for (int i = 0; i < TOTAL_NUMBER_OF_ELEMENTS; i++) {
						String id = getContentTypeId(i);
						IContentType[] result = manager.findContentTypesFor(new ByteArrayInputStream(getSignature(i)), DEFAULT_NAME);
						assertEquals("1.0." + i, 1, result.length);
						assertEquals("1.1." + i, id, result[0].getId());
					}
				} catch (IOException e) {
					fail("2.0", e);
				}
			}
		}.run(this, 10, 2);
	}

	public void testDoSetUp() {
		installContentTypes("1.0", NUMBER_OF_LEVELS, ELEMENTS_PER_LEVEL);
	}

	public void testDoTearDown() {
		Bundle bundle = Platform.getBundle(TEST_DATA_ID);
		if (bundle == null)
			// there is nothing to clean up (install failed?) 
			fail("1.0 nothing to clean-up");
		try {
			bundle.uninstall();
			ensureDoesNotExistInFileSystem(new File(new URL(bundle.getLocation()).getFile()));
		} catch (MalformedURLException e) {
			fail("2.0", e);
		} catch (BundleException e) {
			fail("3.0", e);
		}
	}

	public void testIsKindOf() {
		// warm up preference service		
		loadPreferences();
		// warm up content type registry
		final IContentTypeManager manager = loadContentTypeManager();
		loadChildren();
		final IContentType root = manager.getContentType(getContentTypeId(0));
		assertNotNull("2.0", root);
		new PerformanceTestRunner() {
			protected void test() {
				for (int i = 0; i < TOTAL_NUMBER_OF_ELEMENTS; i++) {
					IContentType type = manager.getContentType(getContentTypeId(i));
					assertNotNull("3.0." + i, type);
					assertTrue("3.1." + i, type.isKindOf(root));
				}
			}
		}.run(this, 10, 500);
	}

	/**
	 * This test is intended for running as a session test.
	 */
	public void testLoadCatalog() {
		// warm up preference service		
		loadPreferences();
		PerformanceTestRunner runner = new PerformanceTestRunner() {
			protected void test() {
				// any interation that will cause the registry to be loaded
				Platform.getContentTypeManager().getContentType(IContentTypeManager.CT_TEXT);
			}
		};
		runner.setRegressionReason("Loading the content types traverses all extensions to the content type extension point, and that operation became more expensive in 3.1. Thankfully, it happens only once per session.");
		runner.run(this, 1, /* must run only once - the suite controls how many sessions are run */1);
		// sanity check to make sure we are running with good data		
		assertEquals("missing content types", TOTAL_NUMBER_OF_ELEMENTS, countTestContentTypes(Platform.getContentTypeManager().getAllContentTypes()));
	}

	/** Tests how much the size of the catalog affects the performance of content type matching by name */
	public void testNameMatching() {
		// warm up preference service		
		loadPreferences();
		// warm up content type registry
		final IContentTypeManager manager = loadContentTypeManager();
		loadDescribers();
		loadChildren();
		new PerformanceTestRunner() {
			protected void test() {
				IContentType[] associated = manager.findContentTypesFor("foo.txt");
				// we know at least the etxt content type should be here
				assertTrue("2.0", associated.length >= 1);
				// and it is supposed to be the first one (since it is at the root)
				assertEquals("2.1", IContentTypeManager.CT_TEXT, associated[0].getId());
			}
		}.run(this, 10, 200000);
	}
}