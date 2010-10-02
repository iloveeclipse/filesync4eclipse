package test;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import junit.framework.TestCase;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IPathVariableManager;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IResourceVisitor;
import org.eclipse.core.resources.IWorkspace;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.IncrementalProjectBuilder;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.jobs.IJobManager;
import org.eclipse.core.runtime.preferences.IEclipsePreferences;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.jface.viewers.StructuredSelection;
import org.osgi.framework.Bundle;

import de.loskutov.fs.FileSyncPlugin;
import de.loskutov.fs.actions.ForceFileSyncActionDelegate;
import de.loskutov.fs.builder.FileSyncBuilder;
import de.loskutov.fs.command.FileMapping;
import de.loskutov.fs.preferences.FileSyncConstants;
import de.loskutov.fs.properties.ProjectHelper;
import de.loskutov.fs.properties.ProjectProperties;

public class TestBuilder extends TestCase {
	private static final String PREFIX_EXCL_FROM_SYNC = "resources";

	private static final String SUFFIX_ORIG_FILE = "_orig";

	protected List<IProject> destProjects;

	protected String[] destPaths;

	protected String defDestination;

	protected Map<String, IPath> pathVarsToValues;

	protected Map<String, String> projNameToVariable;

	protected/*static final*/String DEFAULT_PROPS_NAME = "test";

	protected/*static final*/String PROPS_PATH = "resources/";

	protected String[] pathVars;

	protected IProject srcProj;

	protected String[] srcRootDirs;

	protected NullProgressMonitor monitor;

	protected List<File> allDestFiles;

	private String defVars;

	private String extraVars;

	private final String TEMP_DIR = "${java.io.temp}";



	public TestBuilder() {
		super();
		IPreferenceStore store = FileSyncPlugin.getDefault().getPreferenceStore();
		store.setValue(FileSyncConstants.KEY_ASK_USER, false);
		monitor = new NullProgressMonitor();
		allDestFiles = new ArrayList<File>();
	}

	@Override
	protected void setUp() throws Exception {
		super.setUp();
		Properties properties = readProps(getName());
		initResources(properties);
	}

	private Properties readProps(String name) throws Exception {
		Properties props = new Properties();
		InputStream openStream = null;
		try {
			Bundle bundle = Platform.getBundle(TestFS.PLUGUN_ID);
			URL resource = bundle.getResource(PROPS_PATH + name + ".properties");
			if (resource == null) {
				resource = bundle.getResource(PROPS_PATH + DEFAULT_PROPS_NAME
						+ ".properties");
			}
			openStream = resource.openStream();
			props.load(openStream);
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			if (openStream != null) {
				openStream.close();
			}
		}
		return props;
	}

	private InputStream openStream(String path) throws Exception {
		Bundle bundle = Platform.getBundle(TestFS.PLUGUN_ID);
		URL resource = bundle.getResource(path);
		return resource.openStream();
	}

	private void initResources(Properties props) throws Exception {
		ResourcesPlugin.getWorkspace().getDescription().setAutoBuilding(false);
		IProject srcProject = createSourceProject(props, true);

		createDestinationProjects(props);

		destPaths = props.getProperty("dest_paths").split("\\|");
		String tmpDir = System.getProperty("java.io.temp", "/tmp");
		for (int i = 0; i < destPaths.length; i++) {
			String path = destPaths[i];
			if(path.startsWith(TEMP_DIR)) {
				destPaths[i] = tmpDir + path.substring(TEMP_DIR.length());
			}
		}

		defDestination = props.getProperty("def_dest");

		defVars = props.getProperty("def_vars");
		extraVars = props.getProperty("extra_vars");
		createPathVariables(props);

		createProjectMapping(srcProject);

		//        srcProject.touch(monitor);
		createProjectProperties(srcProject);

		ResourcesPlugin.getWorkspace().getDescription().setAutoBuilding(true);
	}

	protected FileSyncBuilder createBuilder(IProject srcProject) throws Exception {
		ForceFileSyncActionDelegate fsd = new ForceFileSyncActionDelegate();
		fsd.selectionChanged(null, new StructuredSelection(srcProject));
		FileSyncBuilder builder = fsd.getOrCreateBuilder();
		waitForBuilder();
		assertTrue(ProjectHelper.hasBuilder(srcProject));
		return builder;
	}

	private void createProjectProperties(IProject srcProject) {
		ProjectProperties properties = ProjectProperties.getInstance(srcProject);

		// we need to refresh props because of previous test run
		IEclipsePreferences preferences = properties.getPreferences(true);
		FileMapping[] mappings = properties.getMappings();
		System.out.println("\nProject props: \n");
		for (FileMapping mapping : mappings) {
			System.out.println(mapping);
		}
		System.out.println("\ndef. dest: "
				+ preferences.get(ProjectProperties.KEY_DEFAULT_DESTINATION, ""));
	}

	/*
	 * 1 create mapping
	 *  - for each src dir
	 *      - for each dest project
	 *      - for each path variable
	 *      - for each dest path
	 * 2 copy to project
	 *
     map|0=/dir1|E\:/Temp/test1|,|,
     map|1=/dir2|E\:/Temp/test2|,|,
     map|2=/dir1|:/TestProjectB/testdir|,|,
     map|3=/dir2|:/TestProjectC/testdir|,|,
     map|4=/dir1|PATH_1/testdir|,|,
     map|5=/dir2|PATH_2/testdir|,|,
     useCurrentDateForDestinationFiles=false
     cleanOnCleanBuild=true
	 */
	private void createProjectMapping(IProject srcProject) throws IOException,
	CoreException {
		int idx = 0;
		StringBuilder mapFile = new StringBuilder();
		for (String dir : srcRootDirs) {
			for (IProject project : destProjects) {
				if (projNameToVariable.get(project.getName()) != null) {
					// the project is addressed by the path variable
					continue;
				}
				mapFile.append("map|");
				mapFile.append(idx).append("=/").append(dir).append("|:/");
				mapFile.append(project.getName()).append("/").append(dir);
				if (extraVars != null) {
					mapFile.append("|,|,|" + extraVars + "\n");
				} else {
					mapFile.append("|,|,\n");
				}
				idx++;
			}
			for (String pathVar : pathVars) {
				mapFile.append("map|");
				mapFile.append(idx).append("=/").append(dir).append("|");
				mapFile.append(pathVar).append("/").append(dir);
				if (extraVars != null) {
					mapFile.append("|,|,|" + extraVars + "\n");
				} else {
					mapFile.append("|,|,\n");
				}
				idx++;
			}
			for (String path : destPaths) {
				if (pathVarsToValues.containsValue(new Path(path))) {
					// the path is addressed by the path variable
					continue;
				}
				mapFile.append("map|");
				mapFile.append(idx).append("=/").append(dir).append("|");
				mapFile.append(path).append("/").append(dir);
				if (extraVars != null) {
					mapFile.append("|,|,|" + extraVars + "\n");
				} else {
					mapFile.append("|,|,\n");
				}
				idx++;
			}
			if (defDestination != null && defDestination.length() > 0) {
				mapFile.append("map|");
				mapFile.append(idx).append("=/").append(dir);
				if (extraVars != null) {
					mapFile.append("|,|,|,|" + extraVars + "\n");
				} else {
					mapFile.append("|,|,|,\n");
				}
				idx++;
			}
		}
		mapFile.append("useCurrentDateForDestinationFiles=false\n");
		mapFile.append("cleanOnCleanBuild=true\n");
		mapFile.append("defaultDestination=");
		if (defDestination != null) {
			mapFile.append(defDestination);
		}
		mapFile.append('\n');
		mapFile.append("defaultVariables=");
		if (defVars != null) {
			mapFile.append(defVars);
		}
		mapFile.append('\n');

		String toOSString = srcProject.getLocation().append(
		".settings/de.loskutov.FileSync.prefs").toOSString();
		File f = new File(toOSString);
		f.getParentFile().mkdirs();
		if (f.exists()) {
			f.delete();
		}
		f.createNewFile();
		FileWriter fw = new FileWriter(f);
		fw.write(mapFile.toString());
		fw.flush();
		fw.close();
		srcProject.refreshLocal(IResource.DEPTH_INFINITE, monitor);
		System.out.println("Project map file: \n\n");
		System.out.println(mapFile.toString());
		System.out.println("\n");
	}

	private void createDestinationProjects(Properties props) throws CoreException {
		IWorkspace workspace = ResourcesPlugin.getWorkspace();
		IWorkspaceRoot root = workspace.getRoot();
		String destProjProp = props.getProperty("dest_projects");
		String[] destProjNames = destProjProp.split("\\|");

		destProjects = new ArrayList<IProject>();
		for (String projectName : destProjNames) {
			// creates project
			IProject project = root.getProject(projectName);
			if (!project.isAccessible()) {
				project.create(null, monitor);
				project.open(monitor);
			}
			destProjects.add(project);
			IResource[] resources = project.members();
			for (IResource resource : resources) {
				if (resource.exists() && resource.getType() == IResource.FOLDER) {
					resource.delete(true, monitor);
				}
			}
		}
	}

	private void createPathVariables(Properties props) throws CoreException {
		IWorkspace workspace = ResourcesPlugin.getWorkspace();
		IWorkspaceRoot root = workspace.getRoot();
		String pathVarProp = props.getProperty("path_variables");

		pathVars = pathVarProp.split("\\|");

		pathVarsToValues = new HashMap<String, IPath>();
		projNameToVariable = new HashMap<String, String>();
		IProject[] projects = root.getProjects();
		String tmpDir = System.getProperty("java.io.temp", "/tmp");


		for (String key : pathVars) {
			String value = props.getProperty(key);
			if(value != null && value.startsWith(TEMP_DIR)) {
				value = tmpDir + value.substring(TEMP_DIR.length());
			}
			IProject project = getProject(projects, value);
			if (project != null) {
				pathVarsToValues.put(key, project.getLocation());
				projNameToVariable.put(project.getName(), key);
			} else {
				if (value == null) {
					pathVarsToValues.put(key, null);
				} else {
					pathVarsToValues.put(key, new Path(value));
				}
			}
			workspace.getPathVariableManager().setValue(key, pathVarsToValues.get(key));
		}
	}

	@SuppressWarnings("null")
	private IProject createSourceProject(Properties props, boolean deleteExisting)
	throws Exception {
		IWorkspace workspace = ResourcesPlugin.getWorkspace();
		IWorkspaceRoot root = workspace.getRoot();
		String srcProjectName = props.getProperty("src_project");
		// creates project
		srcProj = root.getProject(srcProjectName);
		if (deleteExisting) {
			srcProj.delete(true, true, monitor);
		}
		srcProj = root.getProject(srcProjectName);
		if (!srcProj.isAccessible()) {
			srcProj.create(null, monitor);
			srcProj.open(monitor);
		}

		String srcDirsProp = props.getProperty("src_root_dirs");
		String[] srcDirs = srcDirsProp.split("\\|");
		srcRootDirs = srcDirs;
		srcDirsProp = props.getProperty("src_dirs");
		List<String> srcDirsList = new ArrayList<String>(Arrays.asList(srcDirs));
		List<String> asList = Arrays.asList(srcDirsProp.split("\\|"));
		srcDirsList.addAll(asList);
		srcDirs = srcDirsList.toArray(new String[0]);
		String srcFilesProp = props.getProperty("src_files");
		String[] srcFiles = srcFilesProp.split("\\|");
		long fileSize = Long.parseLong(props.getProperty("fileSize"));
		String defVarsPath = props.getProperty("def_vars");
		String extraVarsPath = props.getProperty("extra_vars");
		boolean useVars = defVarsPath != null;
		Properties[] varProperties = null;
		if (useVars) {
			varProperties = createVarsFiles(defVarsPath, extraVarsPath);
			varProperties[0].putAll(varProperties[1]);
		}
		for (String dir : srcDirs) {
			IFolder folder = srcProj.getFolder(dir);
			if (folder.exists()) {
				folder.delete(true, false, monitor);
			}
			// re-create in workspace
			folder = srcProj.getFolder(dir);
			// create in file system
			folder.create(true, true, monitor);
			folder.setLocal(true, IResource.DEPTH_INFINITE, monitor);
			IPath projectRelativePath = folder.getProjectRelativePath();

			for (String path : srcFiles) {
				IPath ipath = projectRelativePath.append(path);
				IFile file = srcProj.getFile(ipath);
				if (useVars) {
					InputStream[] sources = TestFS.createRandomContent(varProperties[0]);
					if (file.exists()) {
						file.delete(true, false, monitor);
					}
					srcProj.getFile(ipath).create(sources[0], true, monitor);
					ipath = new Path(ipath.toPortableString() + SUFFIX_ORIG_FILE);
					file = srcProj.getFile(ipath);
					if (file.exists()) {
						file.delete(true, false, monitor);
					}
					srcProj.getFile(ipath).create(sources[1], true, monitor);
				} else {
					InputStream source = TestFS.createRandomContent(fileSize++);
					if (file.exists()) {
						file.delete(true, false, monitor);
					}
					srcProj.getFile(ipath).create(source, true, monitor);
				}
			}
		}
		return srcProj;
	}

	private Properties[] createVarsFiles(String defVarsPath, String extraVarsPath)
	throws Exception {
		Properties props1 = new Properties();
		Properties props2 = new Properties();
		IFile file = srcProj.getFile(defVarsPath);
		InputStream source = null;
		if (file.exists()) {
			file.delete(true, false, monitor);
		}

		try {
			source = openStream(defVarsPath);
			file = srcProj.getFile(defVarsPath);
			IPath parentDir = file.getParent().getProjectRelativePath();
			IFolder parentFolder = srcProj.getFolder(parentDir);
			if (!parentFolder.exists()) {
				parentFolder.create(true, true, monitor);
			}
			file.create(source, true, monitor);
			source.close();
			source = file.getContents();
			props1.load(source);
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			if (source != null) {
				source.close();
			}
		}
		file = srcProj.getFile(extraVarsPath);
		if (file.exists()) {
			file.delete(true, false, monitor);
		}
		try {
			source = openStream(extraVarsPath);
			file = srcProj.getFile(extraVarsPath);
			IPath parentDir = file.getParent().getProjectRelativePath();
			IFolder parentFolder = srcProj.getFolder(parentDir);
			if (!parentFolder.exists()) {
				parentFolder.create(true, true, monitor);
			}
			file.create(source, true, monitor);
			source.close();
			source = file.getContents();
			props2.load(source);
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			if (source != null) {
				source.close();
			}
		}
		return new Properties[] { props1, props2 };
	}

	private IProject getProject(IProject[] projects, String name) {
		if (name == null) {
			return null;
		}
		for (IProject project : projects) {
			if (name.equals(project.getName())) {
				return project;
			}
		}
		return null;
	}

	@Override
	protected void tearDown() throws Exception {
		ProjectProperties.removeInstance(srcProj);
		super.tearDown();
	}

	public void testPropsFileChange() throws Exception {
		IFile file = srcProj.getFile(FileSyncBuilder.SETTINGS_DIR + "/"
				+ FileSyncBuilder.SETTINGS_FILE);
		assertTrue(file.exists());
		File file2 = file.getLocation().toFile();
		long localTimeStamp = file2.lastModified();
		long modificationStamp = file.getModificationStamp();

		file.refreshLocal(IResource.DEPTH_ONE, monitor);
		assertEquals(localTimeStamp, file2.lastModified());
		assertEquals(modificationStamp, file.getModificationStamp());

		createBuilder(srcProj);

		// first time the props file shouldnt'be changed
		assertEquals(localTimeStamp, file2.lastModified());
		assertEquals(modificationStamp, file.getModificationStamp());

		long newTime = file2.lastModified() + 111111;
		assertTrue(file2.setLastModified(newTime));
		// line below doesn't work on Ubuntu 10.04
		//		assertEquals(newTime, file2.lastModified());
		localTimeStamp = file2.lastModified();
		file.refreshLocal(IResource.DEPTH_ONE, monitor);
		waitForBuilder();

		// the builder should re-read the props. this causes props to be written back to file
		assertFalse(localTimeStamp == file2.lastModified());
		assertFalse(modificationStamp == file.getModificationStamp());

		modificationStamp = file.getModificationStamp();
		localTimeStamp = file2.lastModified();

		// if builder is disabled, the file content schouldn't be changed
		ProjectHelper.disableBuilder(srcProj);
		file2.setLastModified(newTime);
		localTimeStamp = file2.lastModified();
		file.refreshLocal(IResource.DEPTH_ONE, monitor);
		waitForBuilder();

		assertFalse(modificationStamp == file.getModificationStamp());
		assertTrue(localTimeStamp == file2.lastModified());
	}

	public void testWithDefaultPath() throws Exception {
		createBuilder(srcProj);

		change();
		waitForBuilder();
		// remembers all visited destination files in field "allDestFiles"
		checkAfterChange();

		delete();
		waitForBuilder();
		// uses "allDestFiles" to check if all files are deleted now
		checkAfterDelete();

		create();
		waitForBuilder();
		checkAfterChange();

		delete();
		waitForBuilder();
	}

	public void testWithResolvedPathVariable() throws Exception {
		createBuilder(srcProj);

		change();
		waitForBuilder();
		// remembers all visited destination files in field "allDestFiles"
		checkAfterChange();

		delete();
		waitForBuilder();
		// uses "allDestFiles" to check if all files are deleted now
		checkAfterDelete();

		create();
		waitForBuilder();
		checkAfterChange();

		delete();
		waitForBuilder();
	}

	public void testWithVariablesSubstitution() throws Exception {
		createBuilder(srcProj);

		change();
		waitForBuilder();
		// remembers all visited destination files in field "allDestFiles"
		checkAfterChange();

		delete();
		synchronized (this) {
			wait(500);
		}
		waitForBuilder();

		// uses "allDestFiles" to check if all files are deleted now
		checkAfterDelete();

		create();
		waitForBuilder();
		checkAfterChange();

		delete();
		synchronized (this) {
			wait(500);
		}
		waitForBuilder();
	}

	private void waitForBuilder() throws Exception {
		IJobManager jobManager = Platform.getJobManager();
		synchronized (this) {
			wait(300);
		}
		try {
			jobManager.join(ResourcesPlugin.FAMILY_MANUAL_BUILD, monitor);
			jobManager.join(ResourcesPlugin.FAMILY_AUTO_BUILD, monitor);
			jobManager.join(FileSyncBuilder.class, monitor);
			jobManager.join(ResourcesPlugin.FAMILY_MANUAL_BUILD, monitor);
			jobManager.join(ResourcesPlugin.FAMILY_AUTO_BUILD, monitor);
			jobManager.join(FileSyncBuilder.class, monitor);
		} catch (InterruptedException e) {
			// just continue.
		}
	}

	private void create() throws Exception {
		/*IProject project = */
		createSourceProject(readProps(getName()), false);
		//        project.touch(new NullProgressMonitor());
	}

	private void change() throws CoreException {
		final long time = System.currentTimeMillis();
		IResourceVisitor visitor = new IResourceVisitor() {
			public boolean visit(IResource resource) throws CoreException {
				File file = resource.getLocation().makeAbsolute().toFile();
				file.setLastModified(time);
				resource.touch(monitor);
				return true;
			}
		};
		srcProj.accept(visitor);
		srcProj.refreshLocal(IResource.DEPTH_INFINITE, monitor);
		srcProj.build(IncrementalProjectBuilder.FULL_BUILD, monitor);
	}

	private void delete() throws CoreException {
		IResourceVisitor visitor = new IResourceVisitor() {
			public boolean visit(IResource resource) throws CoreException {
				if (resource.getName().startsWith(".")) {
					return false;
				}
				// there is a possibility for bugs if variables file is deleted too
				// then sync breaks ... so this is intensionally
				// disabled to simulate deletion of variables files
				//                if (resource.getName().startsWith(PREFIX_EXCL_FROM_SYNC)) {
				//                    return false;
				//                }
				if (resource.getType() == IResource.FOLDER) {
					resource.delete(true, monitor);
					return false;
				}
				return true;
			}
		};
		srcProj.accept(visitor);
	}

	private void checkAfterDelete() throws Exception {
		for (File file : allDestFiles) {
			assertFalse("not deleted: " + file.getAbsolutePath(), file.exists());
		}
	}

	/**
	 * This function remembers all visited destination files in field "allDestFiles"
	 * @throws CoreException
	 */
	private void checkAfterChange() throws CoreException {
		// clear remembered files
		allDestFiles.clear();
		System.out.println("\nSynchronized files :\n");

		final IPathVariableManager pmanager = ResourcesPlugin.getWorkspace()
		.getPathVariableManager();
		IResourceVisitor visitor = new IResourceVisitor() {
			public boolean visit(IResource resource) throws CoreException {
				if (resource instanceof IProject) {
					return true;
				}
				if (resource.getName().startsWith(".")) {
					return false;
				}
				if (resource.getName().startsWith(PREFIX_EXCL_FROM_SYNC)) {
					// special dir only for JUnit
					return false;
				}
				IPath relativePath = resource.getProjectRelativePath();
				File srcFile = resource.getLocation().makeAbsolute().toFile();
				assertTrue(destProjects.size() > 0);
				for (IProject project : destProjects) {
					if (projNameToVariable.get(project.getName()) != null) {
						// the project is addressed by the path variable
						continue;
					}

					IResource res = project.findMember(relativePath);
					File destFile = res.getLocation().makeAbsolute().toFile();

					// remember file for later test cases
					allDestFiles.add(destFile);
					System.out.println(destFile);

					if (resource.getType() == IResource.FILE) {
						assertEquals(destFile.lastModified(), srcFile.lastModified());
						String absolutePath = destFile.getAbsolutePath();
						if (!absolutePath.endsWith(SUFFIX_ORIG_FILE)) {
							File origFile = new File(absolutePath + SUFFIX_ORIG_FILE);
							if (origFile.exists()) {
								assertEquals(origFile.length(), destFile.length());
								boolean same = TestFS.isSame(destFile, origFile, true, false);
								assertTrue(same);
							} else {
								assertEquals(srcFile.length(), destFile.length());
							}
						}
					} else {
						assertTrue(destFile.isDirectory());
					}
				}

				assertTrue(pathVars.length > 0);
				for (String pathVar : pathVars) {
					IPath path = pmanager.getValue(pathVar);
					path = path.append(relativePath);
					File destFile = path.makeAbsolute().toFile();
					// remember file for later test cases
					allDestFiles.add(destFile);
					System.out.println(destFile);

					if (resource.getType() == IResource.FILE) {
						assertEquals(destFile.lastModified(), srcFile.lastModified());
						String absolutePath = destFile.getAbsolutePath();
						if (!absolutePath.endsWith(SUFFIX_ORIG_FILE)) {
							File origFile = new File(absolutePath + SUFFIX_ORIG_FILE);
							if (origFile.exists()) {
								assertEquals(origFile.length(), destFile.length());
								assertTrue(TestFS.isSame(destFile, origFile, true, false));
							} else {
								assertEquals(srcFile.length(), destFile.length());
							}
						}
					} else {
						assertTrue(destFile.isDirectory());
					}
				}

				assertTrue(destPaths.length > 0);
				for (String dpath : destPaths) {
					IPath path = new Path(dpath);
					if (pathVarsToValues.containsValue(path)) {
						// the path is addressed by the path variable
						continue;
					}
					path = path.append(relativePath);
					File destFile = path.makeAbsolute().toFile();
					// remember file for later test cases
					allDestFiles.add(destFile);
					System.out.println(destFile);

					if (resource.getType() == IResource.FILE) {
						assertEquals(destFile.lastModified(), srcFile.lastModified());
						String absolutePath = destFile.getAbsolutePath();
						if (!absolutePath.endsWith(SUFFIX_ORIG_FILE)) {
							File origFile = new File(absolutePath + SUFFIX_ORIG_FILE);
							if (origFile.exists()) {
								assertEquals(origFile.length(), destFile.length());
								assertTrue(TestFS.isSame(destFile, origFile, true, false));
							} else {
								assertEquals(srcFile.length(), destFile.length());
							}
						}
					} else {
						assertTrue(destFile.isDirectory());
					}
				}
				return true;
			}
		};

		srcProj.accept(visitor);
	}

}
