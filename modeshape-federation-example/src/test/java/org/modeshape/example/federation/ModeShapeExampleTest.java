/*
 * ModeShape (http://www.modeshape.org)
 * See the COPYRIGHT.txt file distributed with this work for information
 * regarding copyright ownership.  Some portions may be licensed
 * to Red Hat, Inc. under one or more contributor license agreements.
 * See the AUTHORS.txt file in the distribution for a full listing of 
 * individual contributors.
 *
 * ModeShape is free software. Unless otherwise indicated, all code in ModeShape
 * is licensed to you under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 * 
 * ModeShape is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */

package org.modeshape.example.federation;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.net.URL;

import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.Repository;
import javax.jcr.Session;

import junit.framework.Assert;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.modeshape.common.collection.Problems;
import org.modeshape.common.util.IoUtil;
import org.modeshape.jcr.ModeShapeEngine;
import org.modeshape.jcr.RepositoryConfiguration;

public class ModeShapeExampleTest {

	static Session session;

	static ModeShapeEngine engine;

	private static final String MODESHAPE_REPO_PATH = "/modeshape-repository";

	private static void createFile(File parent, String path) throws Exception {
		File file = new File(parent, path);
		IoUtil.write(new ByteArrayInputStream("some content".getBytes()),
				new FileOutputStream(file));
	}

	/**
	 * Creates the FS structure for the repo to federate
	 * 
	 * @return
	 * @throws Exception
	 */
	private static File prepareFS() throws Exception {

		// remove the folder where external content is located (this will be
		// recreated by the repository)

		File folder = new File(MODESHAPE_REPO_PATH + "/files");
		org.apache.commons.io.FileUtils.cleanDirectory(new File(
				MODESHAPE_REPO_PATH));
		folder.mkdirs();
		if (!folder.exists() || !folder.canRead() || !folder.isDirectory()) {
			throw new IllegalStateException("The " + folder.getAbsolutePath()
					+ " folder cannot be accessed");
		}

		// create some files in the folder which will be read by the repository
		createFile(folder, "file1.txt");
		createFile(folder, "file2.txt");
		createFile(folder, "file3.txt");
		new File(folder, "folder1").mkdir();

		return folder;
	}

	/**
	 * Logs out from the session and shuts down the repo
	 */
	@AfterClass
	public static void shutDownRepo() {
		if (session != null) {
			session.logout();
		}
		System.out.println("Shutting down engine ...");
		try {
			engine.shutdown().get();
			System.out.println("Success!");
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	/**
	 * Checks out the nodes into the repository
	 * 
	 * @throws Exception
	 */
	public void checkNodes() throws Exception {
		System.out.println("Displaying node names...");
		NodeIterator childIterator = session.getNode("/rootFolder").getNodes();
		while (childIterator.hasNext()) {
			Node child = childIterator.nextNode();
			String primaryType = child.getPrimaryNodeType().getName();
			System.out.println("+---> " + child.getName() + " (" + primaryType
					+ ")");
			child.addMixin("file:anyProperties");
			child.setProperty("customProperty", "custom value");

			NodeIterator ni2 = child.getNodes();
			while (ni2.hasNext()) {
				Node n3 = ni2.nextNode();
				System.out.println("+---> " + n3.getName() + " (" + primaryType
						+ ")");
				n3.addMixin("file:anyProperties");
				n3.setProperty("customProperty", "custom value");
			}
		}
	}

	public void createOwnNode() {
		try {

			Node folder = session.getNode("/rootFolder/files");

			Assert.assertFalse(session.itemExists("/rootFolder/files/test.xml"));

			// Add xml file node
			Node fileNode = folder.addNode("test.xml", "nt:file");

			// Commenting this line solves the problem!!
			fileNode.addMixin("mix:versionable");

			Node resNode = fileNode.addNode("jcr:content", "nt:resource");

			resNode.setProperty(
					"jcr:data",
					session.getValueFactory().createBinary(
							ClassLoader.getSystemResource("test.xml")
									.openStream()));
			session.save();

		} catch (Exception e) {
			e.printStackTrace();
			Assert.fail();
		}
	}

	/**
	 * Initializes the repository over the FS
	 * 
	 * @throws Exception
	 */
	public void createRepo() throws Exception {
		try {
			// create some files on the file system
			File folder = prepareFS();

			// Get the root node ...
			Node root = session.getRootNode();
			assert root != null;
			String workspaceName = session.getWorkspace().getName();
			System.out.println("Found the root node in the \"" + workspaceName
					+ "\" workspace");

			Node rootFolder = session.getNode("/rootFolder");
			System.out.println("/rootFolder (projection root)");

			NodeIterator childIterator = rootFolder.getNodes();
			while (childIterator.hasNext()) {
				Node child = childIterator.nextNode();
				String primaryType = child.getPrimaryNodeType().getName();
				System.out.println("+---> " + child.getName() + " ("
						+ primaryType + ")");
				child.addMixin("file:anyProperties");
				child.setProperty("customProperty", "custom value");
			}

			session.save();

			System.out
					.println("Stored some custom properties in json files, checkout the "
							+ folder.getAbsolutePath() + " folder");
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	/**
	 * Starts a session into the repository using the given configuration files
	 */
	@BeforeClass
	public static void startSession() {

		// Create and start the engine ...
		engine = new ModeShapeEngine();
		engine.start();

		// Load the configuration for a repository via the classloader (can also
		// use path to a file)...
		Repository repository = null;
		String repositoryName = null;
		try {
			URL url = ClassLoader.getSystemResource("repository-config.json");
			Assert.assertTrue(new File(url.toURI()).exists());

			RepositoryConfiguration config = RepositoryConfiguration.read(url);

			// Verify the configuration for the repository ...
			Problems problems = config.validate();
			if (problems.hasErrors()) {
				System.err.println("Problems starting the engine.");
				System.err.println(problems);
				System.exit(-1);
			}

			// Deploy the repository ...
			repository = engine.deploy(config);
			repositoryName = config.getName();
			// Get the repository
			repository = engine.getRepository(repositoryName);
			// Create a session ...
			session = repository.login("default");
		} catch (Throwable e) {
			e.printStackTrace();
			System.exit(-1);
			return;
		}

	}

	@Test
	public void test() {
		try {
			createRepo();
			checkNodes();
			createOwnNode();
		} catch (Exception e) {
			e.printStackTrace();
			Assert.fail();
		}
	}
}
