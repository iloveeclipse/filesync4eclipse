/*******************************************************************************
 * Copyright (c) 2011 Andrey Loskutov.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * Contributor:  Andrey Loskutov - initial API and implementation
 *******************************************************************************/
package de.loskutov.fs.properties;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.eclipse.core.resources.IPathVariableManager;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.ProjectScope;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.preferences.IEclipsePreferences;
import org.eclipse.core.runtime.preferences.IEclipsePreferences.INodeChangeListener;
import org.eclipse.core.runtime.preferences.IEclipsePreferences.IPreferenceChangeListener;
import org.eclipse.core.runtime.preferences.IEclipsePreferences.NodeChangeEvent;
import org.eclipse.core.runtime.preferences.IEclipsePreferences.PreferenceChangeEvent;
import org.eclipse.core.runtime.preferences.IScopeContext;
import org.osgi.service.prefs.BackingStoreException;

import de.loskutov.fs.FileSyncPlugin;
import de.loskutov.fs.builder.FileSyncBuilder;
import de.loskutov.fs.command.FileMapping;

/**
 * One mapping property should be like:
 * <br>
 * %mapKey%=%source folder%|%destination folder%|inclusionPatternList|exclusionPatternList
 * <br>
 * and inclusionPatternList or exclusionPatternList should be like:
 * <br>
 * %path%;%path%;...
 * <br>
 * where %path% could be either relative path or a path pattern in the "Ant" notation.
 * <br>
 * If either inclusionPatternList or exclusionPatternList
 * or both are not specified, then each missing entry should be replaced with "," character.
 * <br>
 * %source folder% is project relative, %destination folder% is workspace relative or absolute.
 * If %destination folder% is in workspace, then it could be specified as workspace relative,
 * replacing workspace location with FileMapping.MAP_WORKSPACE_RELATIVE char.
 * <br>
 * One could also use path variables for %destination folder% or first part of it.
 * This path variables are listed under
 * "Window ->Preferences ->General ->Workspace->Linked resources-> Defined path variables".
 * Usage is restricted to the first path segment only, see Javadoc for
 * {@link IPathVariableManager#resolvePath(org.eclipse.core.runtime.IPath)}.
 * <br>
 * One %source folder% could be mapped to different (must be distinct!) destination folders.
 * <br>
 * This is an example of project properties:
 * <pre>
 WARNING=DO NOT MODIFY THIS FILE
 map|4=src/application/NFF/WEB-INF|@/test/NFF/WEB-INF|web.xml;weblogic.xml|,
 map|3=src/application/NFF/WEB-INF|D\:/vrp-localdeploy/applications/nff/NFF/WEB-INF|web.xml;weblogic.xml|,
 map|2=src/application/META-INF|D\:/vrp-localdeploy/applications/nff/META-INF|,|,
 map|1=src/application/APP-INF/classes/config|D\:/vrp-localdeploy/applications/nff/APP-INF/classes/config|,|,
 map|0=server/wls81|D\:/vrp-localdeploy/server/nff|,|log.tmp;log.bak
 eclipse.preferences.version=1
 cleanOnCleanBuild=true
 useCurrentDateForDestinationFiles=false
 defaultDestination=D\:\\vrp-localdeploy\\applications\\nff
 </pre>
 * @author Andrei
 */
public class ProjectProperties implements IPreferenceChangeListener, INodeChangeListener {

    /**
     * Any valid file path for the default synchronizing target
     */
    public static final String KEY_DEFAULT_DESTINATION = "defaultDestination";

    // TODO remote: should be a flag per path, not per project
    public static final String KEY_ENABLE_REMOTE = "enableRemoteSync";

    public static final String KEY_DEFAULT_VARIABLES = "defaultVariables";

    /**
     * Default should be false - even if property not set.
     * The values allowed are "true" or "false". If true, then the destination folder will
     * be deleted before performing file synchronization into this folder.
     */
    public static final String KEY_CLEAN_ON_CLEAN_BUILD = "cleanOnCleanBuild";

    /**
     * To use current date for destination files instead of the source file date
     */
    public static final String KEY_USE_CURRENT_DATE = "useCurrentDateForDestinationFiles";

    /** synchronize team private data too (like .svn shit) */
    public static final String KEY_INCLUDE_TEAM_PRIVATE = "includeTeamPrivateFiles";

    /**
     * not for mappings props but only for even notifications use
     */
    public static final String KEY_PROJECT = "project";

    private IProject project;

    private IEclipsePreferences preferences;

    private boolean ignorePreferenceListeners;

    private boolean rebuildPathMap;

    /**
     * Mapping is built as:
     * %sourcePath%|%destinationPath%|inclusionPatternList|exclusionPatternList[|variablesFileFath],
     * and inclusionPatternList/exclusionPatternList looks like:
     * %path%;%path%;... If an entry is missing, then it should be replaced with
     * ","
     */
    private FileMapping[] mappings;

    /**
     * key is IProject, value is corresponding ProjectProperties
     */
    private static Map<IProject, ProjectProperties> projectsToProps = new HashMap<IProject, ProjectProperties>();

    private final List<FileSyncBuilder> prefListeners;

    private Long hashCode;

    public synchronized void addPreferenceChangeListener(FileSyncBuilder listener) {
        if (prefListeners.contains(listener)) {
            return;
        }

        /*
         * it seems that I don't know about real builders lifecycle, but
         * sometimes there are more than one builder instance from same class
         * for the same project.
         */
        String projName = listener.getProject().getName();
        ArrayList<FileSyncBuilder> oldBuilders = new ArrayList<FileSyncBuilder>();
        for (int i = 0; i < prefListeners.size(); i++) {
            FileSyncBuilder ib = prefListeners.get(i);
            if (projName.equals(ib.getProject().getName())) {
                ib.setDisabled(true);
                oldBuilders.add(ib);
            }
        }
        for (int i = 0; i < oldBuilders.size(); i++) {
            prefListeners.remove(oldBuilders.get(i));
        }
        prefListeners.add(listener);
    }

    public List<FileSyncBuilder> getProjectPreferenceChangeListeners() {
        return prefListeners;
    }

    /**
     * @param project
     */
    protected ProjectProperties(IProject project) {
        this.project = project;
        initPreferencesStore();
        prefListeners = new ArrayList<FileSyncBuilder>();
    }

    private void initPreferencesStore() {
        IScopeContext projectScope = new ProjectScope(project);
        preferences = projectScope.getNode(FileSyncPlugin.PLUGIN_ID);
        buildPathMap(preferences);
        preferences.addPreferenceChangeListener(this);
        preferences.addNodeChangeListener(this);
    }

    public static ProjectProperties getInstance(IResource resource) {
        // sanity check
        List<IProject> projects = new ArrayList<IProject>(projectsToProps.keySet());
        for (int i = 0; i < projects.size(); i++) {
            IProject project = projects.get(i);
            if (project == null || !project.isAccessible()) {
                ProjectProperties props = projectsToProps
                        .get(project);
                props.prefListeners.clear();
                projectsToProps.remove(project);
            }
        }

        if (resource == null) {
            return null;
        }
        IProject project = resource.getProject();
        if (project == null) {
            return null;
        }
        ProjectProperties props = projectsToProps.get(project);
        if (props != null) {
            return props;
        }
        props = new ProjectProperties(project);
        projectsToProps.put(project, props);
        return props;
    }

    public static void removeInstance(IProject project) {
        projectsToProps.remove(project);
    }

    /**
     * @param prefs
     */
    private void buildPathMap(IEclipsePreferences prefs) {
        hashCode = null;

        String[] keys;
        try {
            keys = prefs.keys();
        } catch (BackingStoreException e) {
            FileSyncPlugin.log("Could not read preferences for project '"
                    + project.getName() + "'", e, IStatus.ERROR);
            return;
        }
        this.ignorePreferenceListeners = true;

        ArrayList<FileMapping> mappingList = new ArrayList<FileMapping>(keys.length);
        for (int i = 0; i < keys.length; i++) {
            if (keys[i].startsWith(FileMapping.FULL_MAP_PREFIX)) {
                FileMapping mapping = new FileMapping(prefs.get(keys[i], null), project
                        .getLocation());

                if (mappingList.contains(mapping)) {
                    FileSyncPlugin.log("Preferences contains duplicated " + "mapping: '"
                            + mapping + "' for project '" + project.getName() + "'",
                            null, IStatus.WARNING);

                    prefs.remove(keys[i]);
                } else {
                    mappingList.add(mapping);
                }
            }
        }
        ArrayList<FileMapping> mappingList1 = new ArrayList<FileMapping>(mappingList.size());

        while (mappingList.size() > 0) {
            FileMapping fm1 = mappingList.get(0);
            IPath sourcePath = fm1.getSourcePath();
            IPath destinationPath = fm1.getDestinationPath();
            boolean duplicate = false;
            for (int i = 1; i < mappingList.size(); i++) {
                FileMapping fm2 = mappingList.get(i);
                if (sourcePath.equals(fm2.getSourcePath())) {
                    if ((destinationPath != null && destinationPath.equals(fm2
                            .getDestinationPath()))
                            || destinationPath == null
                            && fm2.getDestinationPath() == null) {
                        duplicate = true;
                        FileSyncPlugin.log("Preferences contains duplicated "
                                + "mapping: '" + fm2 + "' for project '"
                                + project.getName() + "'", null, IStatus.WARNING);
                        break;
                    }
                }
            }
            if (!duplicate) {
                mappingList1.add(fm1);
                /*
                 * read properties file for variables, if defined.
                 * Load default first, if exist
                 */
                IPath varPath = fm1.getFullVariablesPath();
                if (varPath != null) {
                    Properties props = new Properties();
                    String defPath = prefs.get(KEY_DEFAULT_VARIABLES, null);
                    File varFile = varPath.toFile();
                    if (defPath != null) {
                        File defFile = new File(fm1.getProjectPath().append(defPath)
                                .toOSString());
                        if (!varFile.equals(defFile)) {
                            if (defFile.exists()) {
                                loadProps(defFile, props);
                            } else {
                                fm1.setVariables(null);
                                FileSyncPlugin.log("Default variables substitution file "
                                        + "not found: " + defFile + ", used in mapping: "
                                        + fm1 + "' for project '" + project.getName()
                                        + "'", null, IStatus.ERROR);
                            }
                        }
                    }

                    if (varFile.exists()) {
                        loadProps(varFile, props);
                        fm1.setVariables(props);
                    } else {
                        fm1.setVariables(null);
                        FileSyncPlugin.log("Variables substitution file not found: "
                                + varFile + ", used in mapping: " + fm1
                                + "' for project '" + project.getName() + "'", null,
                                IStatus.ERROR);
                    }

                }
            }
            mappingList.remove(0);
        }

        mappings = mappingList1.toArray(new FileMapping[mappingList1
                                                                        .size()]);

        this.ignorePreferenceListeners = false;
        this.rebuildPathMap = false;
    }

    private void loadProps(File file, Properties props) {
        FileInputStream fis = null;
        try {
            fis = new FileInputStream(file);
            props.load(fis);
        } catch (IOException e) {
            FileSyncPlugin.log("Error during reading of properties file: '" + file
                    + "' for project '" + project.getName() + "'", null, IStatus.WARNING);
        } finally {
            if (fis != null) {
                try {
                    fis.close();
                } catch (IOException e) {
                    // ignore
                }
            }
        }
    }

    public void preferenceChange(PreferenceChangeEvent event) {
        if (!isIgnorePreferenceListeners()) {
            buildPathMap(preferences);
        } else {
            rebuildPathMap = true;
        }
    }

    /**
     * @return Returns the preferences.
     */
    public IEclipsePreferences getPreferences(boolean forceSync) {
        if (forceSync) {
            refreshPreferences();
        }
        return preferences;
    }

    /**
     *
     */
    public void refreshPreferences() {
        this.ignorePreferenceListeners = true;
        try {
            hashCode = null;
            preferences.clear();
            preferences.sync();
            buildPathMap(preferences);
        } catch (BackingStoreException e) {
            FileSyncPlugin.log("Could not sync to preferences for project:" + project, e,
                    IStatus.ERROR);
        } catch (IllegalStateException e) {
            // settings deleted?
            initPreferencesStore();
        }
        this.ignorePreferenceListeners = false;
    }

    public void refreshPathMap() {
        this.ignorePreferenceListeners = true;
        buildPathMap(preferences);
        this.ignorePreferenceListeners = false;
    }

    /**
     * @param preferences
     *            The preferences to set.
     */
    protected void setPreferences(IEclipsePreferences preferences) {
        this.preferences = preferences;
    }

    /**
     * @return Returns the project.
     */
    public IProject getProject() {
        return project;
    }

    /**
     * @param project
     *            The project to set.
     */
    protected void setProject(IProject project) {
        this.project = project;
    }

    /**
     * @return Returns the mappings.
     */
    public FileMapping[] getMappings() {
        return mappings;
    }

    /**
     * @param mappings
     *            The mappings to set.
     */
    public void setMappings(FileMapping[] mappings) {
        this.mappings = mappings;
    }

    public void added(NodeChangeEvent event) {
        if (!isIgnorePreferenceListeners()) {
            buildPathMap(preferences);
        } else {
            rebuildPathMap = true;
        }
    }

    public void removed(NodeChangeEvent event) {
        try {
            // in case preferences are entirely deleted
            if(event.getParent() == preferences && !preferences.nodeExists("")) {
                // code below throws exception
                // preferences.removeNodeChangeListener(this);
                // preferences.removePreferenceChangeListener(this);
                return;
            }
        } catch (BackingStoreException e) {
            // ignore
            return;
        }
        if (!isIgnorePreferenceListeners()) {
            buildPathMap(preferences);
        } else {
            rebuildPathMap = true;
        }
    }

    /**
     * @return Returns the ignorePreferenceListeners.
     */
    public boolean isIgnorePreferenceListeners() {
        return ignorePreferenceListeners;
    }

    /**
     * @param ignorePreferenceListeners
     *            The ignorePreferenceListeners to set.
     */
    public void setIgnorePreferenceListeners(boolean ignorePreferenceListeners) {
        this.ignorePreferenceListeners = ignorePreferenceListeners;
        if (!ignorePreferenceListeners && rebuildPathMap) {
            buildPathMap(preferences);
            rebuildPathMap = false;
            for (int i = 0; i < prefListeners.size(); i++) {
                IPreferenceChangeListener listener = prefListeners
                        .get(i);
                IEclipsePreferences.PreferenceChangeEvent event = new IEclipsePreferences.PreferenceChangeEvent(
                        preferences, KEY_PROJECT, project, project);
                listener.preferenceChange(event);
            }
        }
    }

    public Long getHashCode(){
        if(hashCode != null){
            return hashCode;
        }
        long code = 31;
        code += preferences.get(KEY_CLEAN_ON_CLEAN_BUILD, "").hashCode();
        code += preferences.get(KEY_ENABLE_REMOTE, "").hashCode();
        code += preferences.get(KEY_DEFAULT_DESTINATION, "").hashCode();
        code += preferences.get(KEY_DEFAULT_VARIABLES, "").hashCode();
        code += preferences.get(KEY_USE_CURRENT_DATE, "").hashCode();
        code += preferences.get(KEY_INCLUDE_TEAM_PRIVATE, "").hashCode();
        if(mappings != null){
            for (int i = 0; i < mappings.length; i++) {
                code += mappings[i].hashCode();
            }
        }
        hashCode = new Long(code);
        return hashCode;
    }

}
