/*******************************************************************************
 * Copyright (c) 2009 Andrei Loskutov.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * Contributor(s):
 * 	Andrei Loskutov - initial API and implementation
 * 	Volker Wandmaker - refactoring for remote functionality
 *******************************************************************************/
package de.loskutov.fs.builder;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import org.eclipse.core.filesystem.URIUtil;
import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IPathVariableManager;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IResourceDelta;
import org.eclipse.core.resources.IWorkspace;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.content.IContentDescription;
import org.eclipse.core.runtime.content.IContentType;
import org.eclipse.core.runtime.preferences.IEclipsePreferences;

import de.loskutov.fs.FileSyncPlugin;
import de.loskutov.fs.command.CopyDelegate;
import de.loskutov.fs.command.FS;
import de.loskutov.fs.command.FileMapping;
import de.loskutov.fs.command.PathVariableHelper;

/**
 * Wizard should has knowledge to allow/diasllow/perform all required sync operations for particular
 * project resource
 *
 * @author Andrei
 */
public class SyncWizard {

    protected static final IContentType TEXT_TYPE = Platform.getContentTypeManager()
    .getContentType("org.eclipse.core.runtime.text"); //$NON-NLS-1$

    /**
     * all known file mappings for this wizard
     */
    protected FileMapping[] mappings;

    /**
     * Default destination root
     */
    protected IPath rootPath;

    protected ProjectProperties projectProps;

    /**
     * True if all existing destination directories/files should be deleted before sync destination
     * to project files. Currently it seems that Eclipse does not use "clean" flag for builders
     */
    private boolean deleteDestinationOnCleanBuild;

    /**
     * To use current date for destination files instead of the source file date
     */
    protected boolean useCurrentDateForDestinationFiles;

    private boolean needRefreshAffectedProjects;

    protected CopyDelegate copyDelegate;

    public SyncWizard() {
        super();
    }

    protected CopyDelegate createCopyDelegate() {
        CopyDelegate ret = new CopyDelegate();
        ret.setUseCurrentDateForDestinationFiles(useCurrentDateForDestinationFiles);
        return ret;
    }

    protected void initCopyDelegate(IFile file, FileMapping fm) {
        if (copyDelegate == null) {
            copyDelegate = createCopyDelegate();
        }
        initCopyDelegate(copyDelegate, file, fm);
    }

    protected CopyDelegate initCopyDelegate(CopyDelegate copyDelegateRef, IFile file, FileMapping fm) {
        try {
            copyDelegateRef.setEncoding(file.getCharset());
        } catch (CoreException e) {
            copyDelegateRef.setEncoding("ISO-8859-1");
            FileSyncPlugin.log("Failed to get charset for file '" + file.getName()
                    + "', ISO-8859-1 used", e, IStatus.WARNING);
        }
        Properties propertiesMap = fm.getVariables();
        copyDelegateRef.setPropertiesMap(propertiesMap);
        return copyDelegateRef;
    }

    public void setProjectProps(ProjectProperties props) throws IllegalArgumentException {
        projectProps = props;
        mappings = props.getMappings();
        if (mappings == null || mappings.length == 0) {
            throw new IllegalArgumentException("FileSync mapping is missing."
                    + " Don't panic, simply call your project owner.");
        }
        IEclipsePreferences preferences = props.getPreferences(false);
        String root = preferences.get(ProjectProperties.KEY_DEFAULT_DESTINATION, "");

        PathVariableHelper pvh = new PathVariableHelper();
        IPath projectPath = props.getProject().getLocation();
        rootPath = pvh.resolveVariable(root, projectPath);

        if ((rootPath == null || rootPath.isEmpty()) && usesDefaultOutputFolder()) {
            throw new IllegalArgumentException("Default target folder is required"
                    + " by one of mappings but not specified in properties!");
        }

        setDeleteDestinationOnCleanBuild(preferences.getBoolean(
                ProjectProperties.KEY_CLEAN_ON_CLEAN_BUILD, false));
        useCurrentDateForDestinationFiles = preferences.getBoolean(
                ProjectProperties.KEY_USE_CURRENT_DATE, false);

    }

    private boolean usesDefaultOutputFolder() {
        for (int i = 0; i < mappings.length; i++) {
            IPath path = mappings[i].getDestinationPath();
            if (path == null || path.isEmpty()) {
                return true;
            }
        }
        return false;
    }

    /**
     * @param res
     * @return true, if given resource is known by project mappings and allowed to be synchronized
     *         with target.
     */
    public boolean checkResource(IResource res) {
        return matchFilter(res);
    }

    /**
     * @param delta
     * @return true, if resource from given delta is known by project mappings and allowed to be
     *         synchronized with target.
     */
    public boolean checkResource(IResourceDelta delta) {
        IResource res = delta.getResource();
        return checkResource(res);
    }

    public boolean hasMappedChildren(IResource resource) {
        if (resource.isPhantom()) {
            // !resource.isAccessible() excludes deleted files - but this is needed here
            return false;
        }
        if (resource.getType() == IResource.PROJECT) {
            return true;
        }
        IPath relativePath = resource.getProjectRelativePath();
        return hasMappedChildren(relativePath, resource.getType() == IResource.FOLDER);
    }

    public boolean hasMappedChildren(IPath path, boolean isFolder) {
        for (int i = 0; i < mappings.length; i++) {
            FileMapping fm = mappings[i];
            if (path.isPrefixOf(fm.getSourcePath())) {
                return true;
            }
        }
        return false;
    }

    public boolean hasMappedChildren(IResourceDelta delta) {
        IResource res = delta.getResource();
        return hasMappedChildren(res);
    }

    /**
     * Performs all required operations to sync given delta with target directory
     *
     * @param delta
     * @param monitor
     * @return true only if this operation was successfull for all mapped files
     */
    public boolean sync(IResourceDelta delta, IProgressMonitor monitor) {
        IResource res = delta.getResource();
        boolean result = false;
        switch (delta.getKind()) {
        case IResourceDelta.ADDED:
            result = copy(res, monitor);
            break;
        case IResourceDelta.REMOVED:
            /*
             * TODO Currently we trying to delete the delta's resource regardless if this resource
             * should be deleted or only the child element. Therefore we create warnings while
             * trying to successive delete directories, because we are going from root to the child
             * entries and as long as childs are exists, deletion of parent will fail. I think there
             * should be some kind of flags in the delta, if the current resource itself should be
             * deleted or only one of the children. In the second case we shouldn't try to delete
             * current resource!!!
             */
            result = true;
            result = delete(res, false, monitor);
            break;
        case IResourceDelta.REPLACED:
            // fall througth
        case IResourceDelta.CHANGED:
            result = copy(res, monitor);
            break;
        default:
            break;
        }
        if (result) {
            needRefreshAffectedProjects = true;
        }
        return result;
    }

    /**
     * Delete target directory / file first (if "deleteDestinationOnFullBuild" is true), then copy
     * source file/dir
     *
     * @param res
     * @param monitor
     * @return true only if this operation was successfull
     */
    public boolean sync(IResource res, IProgressMonitor monitor, boolean clean) {
        boolean ok = true;
        if (clean && deleteDestinationOnCleanBuild) {
            ok = delete(res, clean, monitor);
            if (!ok) {
                return ok;
            }
            needRefreshAffectedProjects = true;
        }
        if (ok) {
            if (!clean || matchFilter(res)) {
                ok = copy(res, monitor);
            }
            if (ok) {
                needRefreshAffectedProjects = true;
            }
        }
        return ok;
    }

    public void cleanUp(IProgressMonitor monitor) {
        if (needRefreshAffectedProjects) {
            List<IContainer>containers = getAffectedResources();
            for (int i = 0; i < containers.size(); i++) {
                IContainer container = containers.get(i);
                try {
                    // this will start all builder for the destination project too...
                    // so that we could have "refresh forever"
                    container.refreshLocal(IResource.DEPTH_INFINITE, monitor);
                } catch (CoreException e) {
                    FileSyncPlugin.log("Failed to refresh destination folder '"
                            + container.getName() + "' after file sync", e, IStatus.WARNING);
                }
            }
        }
        copyDelegate = null;
        needRefreshAffectedProjects = false;
        projectProps = null;
        mappings = null;
    }

    /**
     * @return IContainer list which *should* be affected by the file synchronization as destination
     *         targets. It is NOT the list of *really* affected resources (if build was cancelled or
     *         exception etc).
     */
    private List<IContainer> getAffectedResources() {
        ArrayList<IContainer> list = new ArrayList<IContainer>();
        for (int i = 0; i < mappings.length; i++) {
            IContainer[] containers = mappings[i].getDestinationContainers();
            if (containers.length > 0) {
                for (int j = 0; j < containers.length; j++) {
                    if (!list.contains(containers[j])) {
                        list.add(containers[j]);
                    }
                }
            }
        }
        if (usesDefaultOutputFolder() && rootPath != null && rootPath.toFile() != null) {
            IContainer[] containers = ResourcesPlugin.getWorkspace().getRoot()
            .findContainersForLocationURI(URIUtil.toURI(rootPath.makeAbsolute()));
            if (containers.length > 0) {
                for (int i = 0; i < containers.length; i++) {
                    if (!list.contains(containers[i])) {
                        list.add(containers[i]);
                    }
                }
            }
        }
        return list;
    }

    /**
     * Copy file(s) mapped to given resource according to existing project file mappings
     *
     * @param sourceRoot
     * @param monitor
     * @return true only if this operation was successfull for all mapped files
     */
    /**
     * @param sourceRoot
     * @param monitor
     * @return
     */
    protected boolean copy(IResource sourceRoot, IProgressMonitor monitor) {
        IPath relativePath = sourceRoot.getProjectRelativePath();

        List<FileMapping> mappingList = getMappings(relativePath, sourceRoot.getType() == IResource.FOLDER,
                false);

        if (mappingList == null) {
            return false;
        }

        boolean commonState = true;
        File sourceFile = getSourceFile(sourceRoot);
        for (int i = 0; i < mappingList.size() && !monitor.isCanceled(); i++) {
            FileMapping fm = mappingList.get(i);
            if (!fm.isValid()) {
                continue;
            }
            commonState = copy(sourceRoot, sourceFile, relativePath, fm, monitor) && commonState;

        }

        if (monitor.isCanceled()) {
            FileSyncPlugin.log("Cancelled by user, failed to copy *all* resources, "
                    + "mapped in project '" + sourceRoot.getProject().getName() + "'", null,
                    IStatus.WARNING);
        }
        return commonState;
    }

    protected boolean copy(IResource sourceRoot, File sourceFile, IPath relativePath,
            FileMapping fm, IProgressMonitor monitor) {

        File destinationFile = getDestinationFile(sourceRoot, relativePath, fm);

        if (destinationFile == null) {
            return true;
        }

        boolean commonState = true;
        if (isContainer(sourceRoot)) {
            // this is directory, so we should create it
            commonState = createDir(sourceRoot, destinationFile, monitor) && commonState;
        } else {
            commonState = copy(sourceRoot, sourceFile, destinationFile, relativePath, fm)
            && commonState;
        }
        return commonState;
    }

    protected boolean copy(IResource sourceRoot, File sourceFile, File destinationFile,
            IPath relativePath, FileMapping fm) {
        boolean ok;

        /*
         * single file
         */
        if (!destinationFile.canWrite() || destinationFile.isDirectory()) {
            ok = FS.delete(destinationFile, false);
            if (!ok) {
                FileSyncPlugin.log("Failed to clean old external resource '" + destinationFile
                        + "' mapped in project '" + sourceRoot.getProject().getName() + "'", null,
                        IStatus.WARNING);
                return ok;
            }
        }

        ok = FS.create(destinationFile, true);
        if (!ok) {
            FileSyncPlugin.log("Failed to create new external resource '" + destinationFile
                    + "', mapped in project '" + sourceRoot.getProject().getName() + "'", null,
                    IStatus.WARNING);
            return ok;
        }

        if (isUseCopyDelegate((IFile) sourceRoot, fm)) {
            initCopyDelegate((IFile) sourceRoot, fm);
            ok = copyDelegate.copy(sourceFile, destinationFile);
        } else {
            ok = FS.copy(sourceFile, destinationFile, useCurrentDateForDestinationFiles);
        }

        if (!ok) {
            FileSyncPlugin.log("Failed to copy to external resource '" + destinationFile
                    + "', mapped in project '" + sourceRoot.getProject().getName() + "'", null,
                    IStatus.WARNING);
        }

        return ok;
    }

    protected boolean isUseCopyDelegate(IFile sourceRoot, FileMapping fm) {
        if (fm == null) {
            // fm is needed to use CopyDelegate
            return false;
        }

        boolean ret = fm.getVariablesPath() != null && fm.getVariables() != null;
        if (!ret) {
            return false;
        }

        if (!Boolean.valueOf(hasTextContentType(sourceRoot)).booleanValue()) {
            FileSyncPlugin.log("Variable substitution not used for source '" + sourceRoot
                    + "' (not a text file), mapped in project '"
                    + sourceRoot.getProject().getName() + "'", null, IStatus.WARNING);
            ret = false;
        }

        return ret;
    }

    private boolean createDir(IResource sourceRoot, File destinationFile, IProgressMonitor monitor) {
        boolean ok = FS.create(destinationFile, false);
        if (!ok) {
            FileSyncPlugin.log("Failed to create external folder '" + destinationFile
                    + "', mapped in project '" + sourceRoot.getProject().getName() + "'", null,
                    IStatus.WARNING);
        }
        return ok;
    }

    protected boolean delete(IResource sourceRoot, File rootFile, IPath relativePath,
            FileMapping fm, boolean clean, IProgressMonitor monitor) {

        File destinationFile = getDestinationFile(sourceRoot, relativePath, fm);
        if (destinationFile == null) {
            return true;
        }

        if (destinationFile.equals(rootFile)) {
            // never delete root destination path !!!
            return true;
        }
        boolean result = FS.delete(destinationFile, clean);
        if (!result && destinationFile.isFile()) {
            FileSyncPlugin.log("Failed to delete the external resource '" + destinationFile
                    + "', mapped in project '" + sourceRoot.getProject().getName() + "'", null,
                    IStatus.WARNING);
        }else{
            deleteParent(sourceRoot, clean, monitor, fm, rootFile, relativePath);
            result=true;
        }

        return result;
    }

    /**
     * Deletes file(s) mapped to given resource according to existing project file mappings
     *
     * @param sourceRoot
     * @param clean
     *            true to delete all children of given resource
     * @param monitor
     * @return true only if this operation was successfull for all mapped files
     */
    protected boolean delete(IResource sourceRoot, boolean clean, IProgressMonitor monitor) {
        IPath relativePath = sourceRoot.getProjectRelativePath();
        List<FileMapping> mappingList = getMappings(relativePath, sourceRoot.getType() == IResource.FOLDER,
                clean);
        if (mappingList == null) {
            return true;
        }
        boolean commonState = true;
        File rootFile = rootPath == null ? null : rootPath.toFile();

        for (int i = 0; i < mappingList.size() && !monitor.isCanceled(); i++) {
            FileMapping fm = mappingList.get(i);
            if (!fm.isValid()) {
                continue;
            }

            commonState = delete(sourceRoot, rootFile, relativePath, fm, clean, monitor)
            && commonState;

        }

        if (monitor.isCanceled()) {
            FileSyncPlugin.log("Cancelled by user, failed to delete *all* resources, "
                    + "mapped in project '" + sourceRoot.getProject().getName() + "'", null,
                    IStatus.WARNING);
        }
        return commonState;
    }

    protected void deleteParent(IResource sourceRoot, boolean clean, IProgressMonitor monitor,
            FileMapping fm, File rootFile, IPath relativePath) {
        IContainer parent = sourceRoot.getParent();
        if (parent != null) {
            // try to delete parent directory, if it is empty and is in the mapping
            IPath path = parent.getProjectRelativePath();
            if (path.toString().length() != 0 && matchFilter(path, true)) {
                // start recursion, ignore result value cause this was not explicit requested
                // boolean parentDeleted = delete(parent, clean, monitor);
                delete(parent, rootFile, relativePath, fm, clean, monitor);
                // does we need log for this ??? I think not...
            }
        }
    }

    /**
     * @param source
     * @return File object, corresponding to given resource. This file could be deleted or not yet
     *         created.
     */
    protected File getSourceFile(IResource source) {
        IWorkspace workspace = ResourcesPlugin.getWorkspace();
        // linked resources will be resolved here!
        IPath rawPath = source.getRawLocation();

        // here we should resolve path variables,
        // probably existing at first place of path
        IPathVariableManager pathManager = workspace.getPathVariableManager();
        rawPath = pathManager.resolvePath(rawPath);
        return rawPath.toFile();
    }

    /**
     * @param resource
     * @return true if given resource is folder or project
     */
    protected boolean isContainer(IResource resource) {
        return resource.getType() == IResource.FOLDER || resource.getType() == IResource.PROJECT;
    }

    /**
     * Check if given resource is in included and not in excluded entries patterns in any one of
     * known project files mappings.
     *
     * @param resource
     * @return true
     */
    protected boolean matchFilter(IResource resource) {
        if (resource.isPhantom() || resource.getType() == IResource.PROJECT) {
            // !resource.isAccessible() excludes deleted files - but this is needed here
            return false;
        }
        IPath relativePath = resource.getProjectRelativePath();
        return matchFilter(relativePath, resource.getType() == IResource.FOLDER);
    }

    /**
     * Check if given path is in included and not in excluded entries patterns in any one of known
     * project files mappings.
     *
     * @param path
     * @param isFolder
     * @return true
     */
    protected boolean matchFilter(IPath path, boolean isFolder) {
        // if(path.toString().length() == 0){
        // // prevent AIOBE on :
        // /*
        // * java.lang.ArrayIndexOutOfBoundsException: 0
        // at org.eclipse.jdt.core.compiler.CharOperation.pathMatch(CharOperation.java:1815)
        // at org.eclipse.jdt.internal.core.util.Util.isExcluded(Util.java:966)
        // at de.loskutov.fs.builder.SyncWizard.matchFilter(SyncWizard.java:326)
        // */
        // return false;
        // }
        for (int i = 0; i < mappings.length; i++) {
            FileMapping fm = mappings[i];
            if (fm.getSourcePath().isPrefixOf(path)) {
                char[][] excl = fm.fullExclusionPatternChars();
                char[][] incl = fm.fullInclusionPatternChars();
                boolean ex = isExcluded(path, incl, excl, isFolder);
                if (!ex) {
                    // System.out.println("match: " + path + " to " + fm);
                    return true;
                }
            }
        }
        return false;
    }

    /*
     * Copy from org.eclipse.jdt.internal.core.util.Util
     *
     * Returns whether the given resource path matches one of the inclusion/exclusion patterns.
     * NOTE: should not be asked directly using pkg root pathes
     *
     * @see IClasspathEntry#getInclusionPatterns
     *
     * @see IClasspathEntry#getExclusionPatterns
     */
    public final static boolean isExcluded(IPath resourcePath, char[][] inclusionPatterns,
            char[][] exclusionPatterns, boolean isFolderPath) {
        if (inclusionPatterns == null && exclusionPatterns == null) {
            return false;
        }
        return isExcluded(resourcePath.toString().toCharArray(), inclusionPatterns,
                exclusionPatterns, isFolderPath);
    }

    /*
     * Copy from org.eclipse.jdt.internal.compiler.util.Util.isExcluded
     *
     * ToDO (philippe) should consider promoting it to CharOperation Returns whether the given
     * resource path matches one of the inclusion/exclusion patterns. NOTE: should not be asked
     * directly using pkg root pathes
     *
     * @see IClasspathEntry#getInclusionPatterns
     *
     * @see IClasspathEntry#getExclusionPatterns
     */
    public final static boolean isExcluded(char[] path, char[][] inclusionPatterns,
            char[][] exclusionPatterns, boolean isFolderPath) {
        if (inclusionPatterns == null && exclusionPatterns == null) {
            return false;
        }

        inclusionCheck: if (inclusionPatterns != null) {
            for (int i = 0, length = inclusionPatterns.length; i < length; i++) {
                char[] pattern = inclusionPatterns[i];
                char[] folderPattern = pattern;
                if (isFolderPath) {
                    int lastSlash = CharOperation.lastIndexOf('/', pattern);
                    if (lastSlash != -1 && lastSlash != pattern.length - 1) { // trailing slash ->
                        // adds '**' for free
                        // (see
                        // http://ant.apache.org/manual/dirtasks.html)
                        int star = CharOperation.indexOf('*', pattern, lastSlash);
                        if ((star == -1 || star >= pattern.length - 1 || pattern[star + 1] != '*')) {
                            folderPattern = CharOperation.subarray(pattern, 0, lastSlash);
                        }
                    }
                }
                if (CharOperation.pathMatch(folderPattern, path, true, '/')) {
                    break inclusionCheck;
                }
            }
            return true; // never included
        }
        if (isFolderPath) {
            path = CharOperation.concat(path, new char[] { '*' }, '/');
        }
        if (exclusionPatterns != null) {
            for (int i = 0, length = exclusionPatterns.length; i < length; i++) {
                if (CharOperation.pathMatch(exclusionPatterns[i], path, true, '/')) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Check if given resource is in any (included or excluded) entries patterns in any one of known
     * project files mappings.
     *
     * @param resource
     * @return true
     */
    public boolean mappingExists(IResource resource) {
        if (resource.isPhantom() || resource.getType() == IResource.PROJECT) {
            // !resource.isAccessible() excludes deleted files - but this is needed here
            return false;
        }
        IPath relativePath = resource.getProjectRelativePath();
        return mappingExists(relativePath, resource.getType() == IResource.FOLDER);
    }

    /**
     * Check if given path is in any one of known project files mappings.
     *
     * @param path
     * @param isFolder
     * @return true
     */
    protected boolean mappingExists(IPath path, boolean isFolder) {
        for (int i = 0; i < mappings.length; i++) {
            FileMapping fm = mappings[i];
            if (fm.getSourcePath().isPrefixOf(path)) {
                return true;
            }
        }
        return false;
    }

    protected File getDestinationFile(IResource source, IPath relativePath, FileMapping fm) {
        IPath sourcePath = fm.getSourcePath();
        IPath destinationPath = getDestinationRootPath(fm);

        if (sourcePath.isEmpty() || destinationPath == null) {
            return null;
        }
        IPath relPath = getRelativePath(source, fm);
        destinationPath = destinationPath.append(relPath);

        if (source.getRawLocation().equals(destinationPath)) {
            FileSyncPlugin.log("Source and destination are the same: '" + sourcePath
                    + "', please check mapping for project " + projectProps.getProject().getName(),
                    null, IStatus.WARNING);
            return null;
        }
        File destinationFile = getDestinationFile(destinationPath);
        return destinationFile;
    }

    protected IPath getDestinationRootPath(FileMapping fm) {
        IPath destinationPath = fm.getDestinationPath();
        boolean useGlobal = destinationPath == null;
        if (useGlobal) {
            destinationPath = rootPath;
        }
        return destinationPath;
    }

    protected IPath getRelativePath(IResource resource, FileMapping fm) {
        IPath sourcePath = fm.getSourcePath();
        IPath projectRelativePath = resource.getProjectRelativePath();
        IPath ret = null;
        if (sourcePath.isPrefixOf(projectRelativePath)) {
            ret = projectRelativePath.removeFirstSegments(sourcePath.segmentCount());
        } else {
            // ???
            ret = projectRelativePath;
        }
        return ret;
    }

    /**
     * @param path
     * @param isFolder
     *            true if given path should denote folder
     * @return null if there no matching mappings, or not-empty list with FileMapping objects
     */
    protected List<FileMapping> getMappings(IPath path, boolean isFolder,
            boolean includeExcludes) {
        ArrayList<FileMapping> mappingList = null;
        for (int i = 0; i < mappings.length; i++) {
            FileMapping fm = mappings[i];
            if (fm.getSourcePath().isPrefixOf(path)) {
                if (includeExcludes) {
                    if (mappingList == null) {
                        mappingList = new ArrayList<FileMapping>();
                    }
                    mappingList.add(fm);
                    continue;
                }
                char[][] excl = fm.fullExclusionPatternChars();
                char[][] incl = fm.fullInclusionPatternChars();
                boolean ex = isExcluded(path, incl, excl, isFolder);
                if (!ex) {
                    if (mappingList == null) {
                        mappingList = new ArrayList<FileMapping>();
                    }
                    mappingList.add(fm);
                }
            }
        }
        return mappingList;
    }

    // /**
    // * Collects tree of filtered IResource objects, <br>
    // * in preorder(dfs) : contains each node before its children<br>
    // * or in postorder(bfs): contains children before their parent.
    // * This is important to be able to create first folders and then files
    // * or to delete first files then folders.
    // */
    // private final class FilteredCollector {
    //
    // List collectedSources = new ArrayList();
    //
    // /**
    // * Collects tree of filtered IResource objects, <br>
    // * in preorder(dfs) : contains each node before its children<br>
    // * or in postorder(bfs): contains children before their parent.
    // * This is important to be able to create first folders and then files
    // * or to delete first files then folders.
    // */
    // public boolean visit(IResource resource, boolean preorder) {
    //
    // if(matchFilter(resource)){
    //
    // if(preorder) {
    // collectedSources.add(resource);
    // }
    //
    // if(isContainer(resource)){
    // IResource[] resources;
    // try {
    // resources = ((IContainer)resource).members(false);
    // } catch (CoreException e) {
    // FileSyncPlugin.logError(
    // "Could not access members from " + resource, e);
    // return false;
    // }
    // for (int i = 0; i < resources.length; i++) {
    // boolean b = visit(resources[i], preorder);
    // if(!b){
    // return false;
    // }
    // }
    // }
    //
    // if(!preorder){
    // collectedSources.add(resource);
    // }
    // }
    // return true;
    // }
    //
    // /**
    // * @return Returns the collectedSources.
    // */
    // public List getCollectedSources() {
    // return collectedSources;
    // }
    // }
    //

    /**
     * @param deleteDestinationOnCleanBuild
     *            The deleteDestinationOnCleanBuild to set.
     */
    public void setDeleteDestinationOnCleanBuild(boolean deleteDestinationOnCleanBuild) {
        this.deleteDestinationOnCleanBuild = deleteDestinationOnCleanBuild;
    }

    /**
     * @return Returns the projectProps.
     */
    public ProjectProperties getProjectProps() {
        return projectProps;
    }

    /**
     * @return true if any FileMapping is valid
     */
    public boolean begin() {
        boolean anyValid = false;

        if (rootPath != null && !SyncWizardFactory.getInstance().isRseAvailable()
                && rootPath.toFile() == null) {
            FileSyncPlugin
            .log(
                    "FileSync for project '"
                    + projectProps.getProject().getName()
                    + "' ignored as RSE is not available and default target folder is an URI-expression with a scheme != 'file'.",
                    null, IStatus.WARNING);
        }

        for (int i = 0; i < mappings.length; i++) {
            FileMapping fm = mappings[i];
            File destinationFile = getDestinationFile(getDestinationRootPath(fm));
            boolean created = FS.create(destinationFile, false) && destinationFile != null;
            fm.setValid(fm.isValid() && created);
            anyValid |= created;
        }
        return anyValid;
    }

    public boolean commit() {
        return true;
    }

    /**
     * @param file
     *            must be not null
     * @return true if the file has "text" content description.
     */
    public static boolean hasTextContentType(IFile file) {
        try {
            IContentDescription contentDescr = file.getContentDescription();
            if (contentDescr == null) {
                return false;
            }
            IContentType contentType = contentDescr.getContentType();
            if (contentType == null) {
                return false;
            }
            return contentType.isKindOf(TEXT_TYPE);
            //
        } catch (CoreException e) {
            FileSyncPlugin.log("Could not get content type for: " + file, e, IStatus.WARNING);
        }
        return false;
    }

    public static File getDestinationFile(IPath destinationPath) {
        return destinationPath.toFile();
    }

}
