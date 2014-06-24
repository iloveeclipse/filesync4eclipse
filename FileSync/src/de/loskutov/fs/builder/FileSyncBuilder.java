/*******************************************************************************
 * Copyright (c) 2009 Andrey Loskutov.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * Contributor:  Andrey Loskutov - initial API and implementation
 *******************************************************************************/
package de.loskutov.fs.builder;

import java.util.HashMap;
import java.util.Map;

import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IResourceDelta;
import org.eclipse.core.resources.IResourceDeltaVisitor;
import org.eclipse.core.resources.IResourceVisitor;
import org.eclipse.core.resources.IncrementalProjectBuilder;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.core.runtime.preferences.IEclipsePreferences;
import org.eclipse.core.runtime.preferences.IEclipsePreferences.IPreferenceChangeListener;
import org.eclipse.core.runtime.preferences.IEclipsePreferences.PreferenceChangeEvent;

import de.loskutov.fs.FileSyncPlugin;
import de.loskutov.fs.command.FileMapping;
import de.loskutov.fs.properties.ProjectProperties;

/**
 * @author Andrey
 */
public class FileSyncBuilder extends IncrementalProjectBuilder

implements IPreferenceChangeListener {

    public static final String BUILDER_ID = FileSyncPlugin.PLUGIN_ID + ".FSBuilder";

    /**
     * I don't know who and where in Eclipse creates the ".settings" dir
     */
    public static final String SETTINGS_DIR = ".settings";

    /**
     * I don't know who and where in Eclipse adds the ".prefs" suffix
     */
    public static final String SETTINGS_FILE = FileSyncPlugin.PLUGIN_ID + ".prefs";

    private static final IPath SETTINGS_PATH = new Path(SETTINGS_DIR)
    .append(SETTINGS_FILE);

    public static final int MAPPING_CHANGED_IN_GUI_BUILD = 999;

    public static final Integer MAPPING_CHANGED_IN_GUI = new Integer(
            MAPPING_CHANGED_IN_GUI_BUILD);

    private boolean wizardNotAvailable;

    private boolean disabled;

    private IProject project2;

    private long modificationStamp;

    private Long mappingHashCode;

    private final Map pathToTimeStamp;

    volatile boolean ignorePrefChange;

    volatile private int visitorFlags;

    /** called by Eclipse through reflection */
    public FileSyncBuilder() {
        super();
        pathToTimeStamp = new HashMap();
    }

    /** caled by us on click */
    public FileSyncBuilder(IProject project) {
        this();
        project2 = project;
    }

    protected IProject getProjectInternal() {
        IProject project = null;
        try {
            project = getProject();
        } catch (NullPointerException e) {
            // TODO: since Eclipse 3.7 getProject() throws NPE if the builder
            // was created manually (as we do for the manually triggered build)
            // Have no time to fix properly, so just catch the NPE
        }
        if (project == null) {
            project = project2;
        }
        return project;
    }

    public boolean isDisabled() {
        return disabled;
    }

    public void setDisabled(boolean disabled) {
        this.disabled = disabled;
    }

    public void build(int kind, IProgressMonitor monitor) {
        build(kind, new HashMap(), monitor);
    }

    /*
     * (non-Javadoc)
     * @see org.eclipse.core.resources.IncrementalProjectBuilder#clean(org.eclipse.core.runtime.IProgressMonitor)
     */
    @Override
    protected void clean(IProgressMonitor monitor) throws CoreException {
        build(CLEAN_BUILD, new HashMap(), monitor);
    }

    /*
     * @see org.eclipse.core.internal.events.InternalBuilder#build(int, Map, IProgressMonitor)
     */
    @Override
    protected IProject[] build(int kind, Map args, IProgressMonitor monitor) {
        if (isDisabled()) {
            return null;
        }
        if (args == null) {
            args = new HashMap();
        }
        ProjectProperties props = ProjectProperties.getInstance(getProjectInternal());
        updateVisitorFlags(props);

        SyncWizard wizard = new SyncWizard();
        IProject[] result = null;
        try {
            switch (kind) {
            case AUTO_BUILD:
                result = buildAuto(args, props, wizard, monitor);
                break;
            case INCREMENTAL_BUILD:
                result = buildIncremental(args, props, wizard, monitor);
                break;
            case FULL_BUILD:
                result = buildFull(args, props, wizard, monitor);
                break;
            case CLEAN_BUILD:
                // Currently it seems that Eclipse does not use "clean" flag for builders
                // on "clean project" action...
                result = buildClean(args, props, wizard, monitor);
                break;
            case MAPPING_CHANGED_IN_GUI_BUILD:
                args.put(MAPPING_CHANGED_IN_GUI, MAPPING_CHANGED_IN_GUI);
                result = buildFull(args, props, wizard, monitor);
                break;
            default:
                result = buildFull(args, props, wizard, monitor);
                break;
            }
            wizardNotAvailable = false;
        } catch (IllegalArgumentException e) {
            if (!wizardNotAvailable) {
                FileSyncPlugin.log("Couldn't run file sync for project '"
                        + getProjectInternal().getName() + "': " + e.getMessage(), e,
                        IStatus.WARNING);
                wizardNotAvailable = true;
            }
            return null;
        } catch (IllegalStateException e) {
            if (!wizardNotAvailable) {
                FileSyncPlugin.log("Couldn't run file sync for project '"
                        + getProjectInternal().getName() + "': " + e.getMessage(), e,
                        IStatus.WARNING);
                wizardNotAvailable = true;
            }
            return null;
        }

        return result;
    }

    /**
     * Automatic build
     * @param args build parameters
     * @param wizard
     * @param monitor progress indicator
     * @return IProject[] related projects list
     */
    private IProject[] buildAuto(Map args, ProjectProperties props, SyncWizard wizard,
            IProgressMonitor monitor) {
        return buildIncremental(args, props, wizard, monitor);
    }

    /**
     * Full build
     * @param args build parameters
     * @param wizard
     * @param monitor progress indicator
     * @return IProject[] related projects list
     */
    private IProject[] buildFull(Map args, ProjectProperties props, SyncWizard wizard,
            IProgressMonitor monitor) {
        IProject currentProject = getProjectInternal();
        if (currentProject != null) {
            fullProjectBuild(args, currentProject, props, wizard, monitor, false);
        }
        return null;
    }

    /**
     * Full build
     * @param args build parameters
     * @param wizard
     * @param monitor progress indicator
     * @return IProject[] related projects list
     */
    private IProject[] buildClean(Map args, ProjectProperties props, SyncWizard wizard,
            IProgressMonitor monitor) {
        IProject currentProject = getProjectInternal();
        if (currentProject != null) {
            fullProjectBuild(args, currentProject, props, wizard, monitor, true);
        }
        return null;
    }

    /**
     * Incremental build
     * @param args build parameters
     * @param wizard
     * @param monitor progress indicator
     * @return IProject[] related projects list
     */
    private IProject[] buildIncremental(final Map args, final ProjectProperties props,
            final SyncWizard wizard, final IProgressMonitor monitor) {
        IProject result[] = null;

        final IProject currentProject = getProjectInternal();
        if (currentProject != null) {
            final IResourceDelta resourceDelta = getDelta(currentProject);
            if (resourceDelta == null) {
                /*
                 * Builder deltas may be null. If a builder has never been invoked before,
                 * any request for deltas will return null. Also, if a builder is not run
                 *  for a long time, the platform reserves the right to return a null delta
                 */
                return buildFull(args, props, wizard, monitor);
            }
            if (resourceDelta.getAffectedChildren().length == 0) {
                //                FileSyncPlugin.log("nothing happens because delta is empty", null, IStatus.INFO);
            } else {
                /*
                 * check if my own props file is changed - before going to
                 * synchronize all other files
                 */
                FSPropsChecker propsChecker = new FSPropsChecker(monitor, props);
                try {
                    resourceDelta.accept(propsChecker, false);
                } catch (CoreException e) {
                    FileSyncPlugin.log("Errors during sync of the resource delta:"
                            + resourceDelta + " for project '" + currentProject.getName()
                            + "'", e, IStatus.ERROR);
                }
                // props are in-sync now
                wizard.setProjectProps(props);
                int elementCount = countDeltaElement(resourceDelta);

                if (propsChecker.propsChanged) {
                    Job[] jobs = Job.getJobManager().find(FileSyncBuilder.class);
                    if (jobs.length == 0) {
                        // start full build (not clean!) because properties are changed!!!
                        Job job = new Job("Filesync") {
                            @Override
                            public boolean belongsTo(Object family) {
                                return family == FileSyncBuilder.class;
                            }

                            @Override
                            protected IStatus run(IProgressMonitor monitor1) {
                                build(FULL_BUILD, monitor1);
                                return Status.OK_STATUS;
                            }
                        };
                        /*
                         * we starting the full build intensionally asynchron, because the current
                         * build need to be finished first. The background is not completely clear for
                         * me, but interrupting the build here lead to failures of "delete"
                         * test case, if variables files are deleted too.
                         * So let the current build finish and shedule another one to do
                         * the full sync again, with changed preferences
                         */
                        job.setUser(false);
                        job.schedule(1000);
                    }
                } else {
                    try {
                        monitor.beginTask("Incremental file sync", elementCount);
                        final FSDeltaVisitor visitor = new FSDeltaVisitor(monitor, wizard);
                        resourceDelta.accept(visitor, visitorFlags);
                    } catch (CoreException e) {
                        FileSyncPlugin.log(
                                "Errors during sync of the resource delta:"
                                        + resourceDelta + " for project '"
                                        + currentProject + "'", e, IStatus.ERROR);
                    } finally {
                        wizard.cleanUp(monitor);
                        monitor.done();
                    }
                }
            }
        }

        return result;
    }

    /**
     * Process all files in the project
     * @param project the project
     * @param monitor a progress indicator
     * @param wizard
     */
    protected void fullProjectBuild(Map args, final IProject project,
            ProjectProperties props, SyncWizard wizard, final IProgressMonitor monitor,
            boolean clean) {

        if (!args.containsKey(MAPPING_CHANGED_IN_GUI) && wizard.getProjectProps() == null) {
            /*
             * check if my own props file is changed - before going to
             * synchronize all other files, but only if the build was *not*
             * initiated by changing mapping in the GUI
             */
            FSPropsChecker propsChecker = new FSPropsChecker(monitor, props);
            try {
                project.accept(propsChecker, IResource.DEPTH_INFINITE, false);
            } catch (CoreException e) {
                FileSyncPlugin.log("Error during visiting project: " + project.getName(),
                        e, IStatus.ERROR);
            }
        }
        // props are in-sync now
        wizard.setProjectProps(props);

        int elementCount = countProjectElements(project);
        try {
            if (clean) {
                monitor.beginTask("Clean project sync", elementCount);
            } else {
                monitor.beginTask("Full project sync", elementCount);
            }
            final FSResourceVisitor visitor = new FSResourceVisitor(monitor, wizard,
                    clean);
            project.accept(visitor, IResource.DEPTH_INFINITE, visitorFlags);
        } catch (CoreException e) {
            FileSyncPlugin.log("Error during visiting project: " + project.getName(), e,
                    IStatus.ERROR);
        } finally {
            wizard.cleanUp(monitor);
            monitor.done();
        }
    }

    /**
     * Count the number of sub-resources of a project
     * @param project a project
     * @return the element count
     */
    private int countProjectElements(IProject project) {
        CountVisitor visitor = new CountVisitor();
        try {
            project.accept(visitor, IResource.DEPTH_INFINITE, visitorFlags);
        } catch (CoreException e) {
            FileSyncPlugin.log("Exception when counting elements of a project '"
                    + project.getName() + "'", e, IStatus.ERROR);
        }
        return visitor.count;
    }

    /**
     * Count the number of sub-resources of a delta
     * @param delta a resource delta
     * @return the element count
     */
    private int countDeltaElement(IResourceDelta delta) {
        CountVisitor visitor = new CountVisitor();
        try {
            delta.accept(visitor, visitorFlags);
        } catch (CoreException e) {
            FileSyncPlugin.log("Exception counting elements in the delta: " + delta, e,
                    IStatus.ERROR);
        }
        return visitor.count;
    }

    @Override
    protected void startupOnInitialize() {
        super.startupOnInitialize();
        checkSettingsTimestamp(getProject().getFile(SETTINGS_PATH));
        ProjectProperties props = ProjectProperties.getInstance(getProjectInternal());
        // add self to listeners - if we already listen on ProjectProperties,
        // then this operation has no effect
        props.addPreferenceChangeListener(this);
        updateVisitorFlags(props);
        mappingHashCode = props.getHashCode();
    }

    private void updateVisitorFlags(ProjectProperties props) {
        IEclipsePreferences preferences = props.getPreferences(false);
        boolean includeTeamFiles = preferences.getBoolean(
                ProjectProperties.KEY_INCLUDE_TEAM_PRIVATE, false);
        visitorFlags = includeTeamFiles? IContainer.INCLUDE_TEAM_PRIVATE_MEMBERS : IResource.NONE;
    }

    /**
     * remember the timestamp for the project settings file
     * @return true, if the timestamp was changed since first run
     */
    protected boolean checkSettingsTimestamp(IResource settingsFile) {
        long oldStamp = modificationStamp;
        long localTimeStamp = settingsFile.getLocation().toFile().lastModified();
        boolean changed = oldStamp != 0 && oldStamp != localTimeStamp;
        if (oldStamp == 0 || changed) {
            modificationStamp = localTimeStamp;
        }
        return changed;
    }

    protected void checkCancel(IProgressMonitor monitor, SyncWizard wizard) {
        if (monitor.isCanceled()) {
            wizard.cleanUp(monitor);
            //            forgetLastBuiltState();//not always necessary
            throw new OperationCanceledException();
        }
    }

    /**
     * @author Andrey
     */
    private class FSDeltaVisitor implements IResourceDeltaVisitor {
        private final IProgressMonitor monitor;

        private final SyncWizard wizard;

        /**
         * @param monitor
         */
        public FSDeltaVisitor(IProgressMonitor monitor, SyncWizard wizard) {
            this.monitor = monitor;
            this.wizard = wizard;
        }

        @Override
        public boolean visit(IResourceDelta delta) {
            if (delta == null) {
                return false;
            }
            checkCancel(monitor, wizard);
            monitor.worked(1);
            if (delta.getResource().getType() == IResource.PROJECT) {
                return true;
            }
            boolean shouldVisit = wizard.checkResource(delta);
            if (!shouldVisit) {
                // return true, if there children with mappings to visit
                return wizard.hasMappedChildren(delta);
            }
            String resStr = delta.getResource().toString();
            monitor.subTask("sync: " + resStr);
            boolean ok = wizard.sync(delta, monitor);
            if (!ok) {
                FileSyncPlugin.log("Errors during sync of the resource delta: '" + resStr
                        + "' in project '" + delta.getResource().getProject().getName()
                        + "'", null, IStatus.WARNING);
            }
            // always visit children
            return true;
        }
    }

    /**
     * @author Andrey
     */
    private class FSResourceVisitor implements IResourceVisitor {
        private final IProgressMonitor monitor;

        private final SyncWizard wizard;

        private final boolean clean;

        /**
         * @param monitor
         * @param clean
         */
        public FSResourceVisitor(IProgressMonitor monitor, SyncWizard wizard,
                boolean clean) {
            this.monitor = monitor;
            this.wizard = wizard;
            this.clean = clean;
        }

        public Object getMonitor() {
            return monitor;
        }

        /* (non-Javadoc)
         * @see org.eclipse.core.resources.IResourceVisitor#visit(org.eclipse.core.resources.IResource)
         */
        @Override
        public boolean visit(IResource resource) {
            monitor.worked(1);
            checkCancel(monitor, wizard);
            if (resource.getType() == IResource.PROJECT) {
                return true;
            }

            boolean shouldVisit = wizard.checkResource(resource);
            if (clean && !shouldVisit) {
                // this resource could be on the mapping path but filtered out -
                // on "clean" build it should be deleted
                shouldVisit = wizard.mappingExists(resource);
            }
            if (!shouldVisit) {
                // return true, if there children with mappings to visit
                return wizard.hasMappedChildren(resource);
            }
            String resStr = resource.getProjectRelativePath().toString();
            monitor.subTask("check for " + resStr);
            boolean ok = wizard.sync(resource, monitor, clean);
            if (!ok) {
                FileSyncPlugin.log("Errors during sync of the resource '" + resStr
                        + "' in project '" + resource.getProject().getName() + "'", null,
                        IStatus.WARNING);
            }
            // always visit children
            return true;
        }
    }

    /**
     * @author Andrey
     */
    private class FSPropsChecker implements IResourceVisitor, IResourceDeltaVisitor {
        private final IProgressMonitor monitor;

        private final ProjectProperties props;

        boolean propsChanged;

        /**
         * @param monitor
         * @param props
         */
        public FSPropsChecker(IProgressMonitor monitor, ProjectProperties props) {
            this.monitor = monitor;
            this.props = props;
        }

        public Object getMonitor() {
            return monitor;
        }

        /* (non-Javadoc)
         * @see org.eclipse.core.resources.IResourceVisitor#visit(org.eclipse.core.resources.IResource)
         */
        @Override
        public boolean visit(IResource resource) {
            if (monitor.isCanceled()) {
                throw new OperationCanceledException();
            }
            if (resource.getType() == IResource.PROJECT) {
                return true;
            }

            boolean continueVisit = isSettingsDir(resource);
            if (continueVisit && isSettingsFile(resource)
                    && checkSettingsTimestamp(resource)) {
                // mappings changed
                ignorePrefChange = true;
                props.refreshPreferences();
                Long hashCode = props.getHashCode();
                ignorePrefChange = false;
                if (!hashCode.equals(mappingHashCode)) {
                    propsChanged = true;
                    continueVisit = false;
                    mappingHashCode = hashCode;
                    updateVisitorFlags(props);
                }
            } else {
                // check if variables are changed in any directory
                continueVisit = true;
                FileMapping[] mappings = props.getMappings();
                for (int i = 0; i < mappings.length; i++) {
                    IPath variablesPath = mappings[i].getVariablesPath();
                    if (variablesPath != null) {
                        boolean match = variablesPath.equals(resource
                                .getProjectRelativePath());
                        if (match) {
                            Long time = (Long) pathToTimeStamp.get(variablesPath);
                            long newTime = resource.getLocation().toFile().lastModified();
                            if (time != null && time.longValue() != newTime) {
                                time = new Long(newTime);
                                pathToTimeStamp.put(variablesPath, time);
                                // we could stop and do full build, because vars are changed
                                props.refreshPathMap();
                                Long hashCode = props.getHashCode();
                                if (!hashCode.equals(mappingHashCode)) {
                                    continueVisit = false;
                                    propsChanged = true;
                                    mappingHashCode = hashCode;
                                    break;
                                }
                            } else if (time == null) {
                                time = new Long(newTime);
                                pathToTimeStamp.put(variablesPath, time);
                            }
                        }
                    }
                }
            }
            // visit children only from settings directory
            return continueVisit;
        }

        /**
         * @param file
         * @return true if this resource is my own prefs file
         */
        private boolean isSettingsFile(IResource file) {
            // the directory is already ok, so we check only for the file name
            IPath relativePath = file.getProjectRelativePath();
            if (relativePath == null) {
                return false;
            }
            return SETTINGS_FILE.equals(relativePath.lastSegment());
        }

        /**
         * @param dir
         * @return true if this resource is my own prefs directory
         */
        private boolean isSettingsDir(IResource dir) {
            IPath relativePath = dir.getProjectRelativePath();
            // should match 1:1 to the settings dir, which is always under the
            // project root
            if (relativePath == null || relativePath.segmentCount() > 2) {
                return false;
            }
            return SETTINGS_DIR.equals(relativePath.segment(0));
        }

        /* (non-Javadoc)
         * @see org.eclipse.core.resources.IResourceDeltaVisitor#visit(org.eclipse.core.resources.IResourceDelta)
         */
        @Override
        public boolean visit(IResourceDelta delta) {
            return visit(delta.getResource());
        }
    }

    /* (non-Javadoc)
     * @see org.eclipse.core.runtime.preferences.IEclipsePreferences.IPreferenceChangeListener#preferenceChange(org.eclipse.core.runtime.preferences.IEclipsePreferences.PreferenceChangeEvent)
     */
    @Override
    public void preferenceChange(PreferenceChangeEvent event) {
        if (ignorePrefChange) {
            return;
        }
        String key = event.getKey();
        if (!ProjectProperties.KEY_PROJECT.equals(key)) {
            return;
        }
        Job[] jobs = Job.getJobManager().find(getClass());
        if (jobs.length == 0) {
            final Job myJob = new Job("Mapping is changed => full project sync") {
                @Override
                public boolean belongsTo(Object family) {
                    return family == FileSyncBuilder.class;
                }

                @Override
                public IStatus run(IProgressMonitor monitor) {
                    build(MAPPING_CHANGED_IN_GUI_BUILD, monitor);
                    return Status.OK_STATUS;//new JobStatus(IStatus.INFO, 0, this, "", null);
                }
            };
            myJob.setUser(false);
            myJob.schedule();
        }
    }

    /**
     * Visitor which only counts visited resources
     * @author Andrey
     */
    protected final static class CountVisitor implements IResourceDeltaVisitor,
    IResourceVisitor {
        public int count = 0;

        @Override
        public boolean visit(IResourceDelta delta) {
            count++;
            return true;
        }

        @Override
        public boolean visit(IResource resource) {
            count++;
            return true;
        }
    }
}
