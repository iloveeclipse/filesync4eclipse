/*******************************************************************************
 * Copyright (c) 2009 Andrei Loskutov.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * Contributor(s):
 * 	Andrei Loskutov - initial API and implementation
 * 	Volker Wandmaker - add URI-functionality
 *******************************************************************************/
package de.loskutov.fs.command;

import java.net.URI;

import org.eclipse.core.resources.IPathVariableManager;
import org.eclipse.core.resources.IWorkspace;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.variables.IStringVariableManager;
import org.eclipse.core.variables.VariablesPlugin;

import de.loskutov.fs.FileSyncPlugin;

/**
 * Resolves the path with variables to the full OS path and back. The helper should be used only for
 * one single path entry, because it maintains a list of resolved variables for this path and their
 * substitutes. Using same helper for different paths can lead to unexpected issues if at least one
 * of the paths contains a variable part.
 * 
 * @author Andrei
 */
public class PathVariableHelper {

    /** path variable name. may be null. */
    private String pathVariableName;

    /** path variable value. may be null. */
    private IPath pathVariableValue;

    /** some contributed variable */
    private Variable anyVariable;

    static class Variable {

        final String variableValue;
        final String variableName;

        public Variable(String before, String after) {
            if (before.equals(after)) {
                variableValue = null;
                variableName = null;
                return;
            }
            // resolve //server/share/${env_var:CLIENT}/data to //server/share/andrei/data
            // or ${env_var:CLIENT}/data/${env_var:TODAY} to c:/andrei/data/20090131
            char[] char1 = before.toCharArray();
            char[] char2 = after.toCharArray();
            int start = -1;
            int stop = -1;
            for (int i = 0; i < char1.length; i++) {
                char c = char1[i];
                if (i >= char2.length || char2[i] != c) {
                    start = i;
                    break;
                }
            }
            for (int i = 1; i <= char1.length; i++) {
                char c = char1[char1.length - i];
                if (char2.length - i <= 0 || char2[char2.length - i] != c) {
                    stop = i - 1;
                    break;
                }
            }

            variableName = before.substring(start, before.length() - stop);
            variableValue = after.substring(start, after.length() - stop);
        }

        String unResolve(String path) {
            if (variableName == null) {
                return path;
            }
            int start = path.indexOf(variableValue);
            if (start < 0) {
                return path;
            }
            int stop = start + variableValue.length();
            return path.substring(0, start) + variableName + path.substring(stop, path.length());
        }

    }

    public static void main(String[] args) {
        Variable v = new Variable("hello{env:test}Huston", "hello_O_Huston");
        System.out.println(v.variableName + " = " + v.variableValue);
        String newValue = "hello_O_Apollo";
        System.out.println(newValue + " > " + v.unResolve(newValue));

        v = new Variable("//server/share/${env_var:CLIENT}/data", "//server/share/Andrei/data");
        System.out.println(v.variableName + " = " + v.variableValue);
        newValue = "//server2/share2/Andrei/test";
        System.out.println(newValue + " > " + v.unResolve(newValue));

        v = new Variable("${env_var:CLIENT}/data/${env_var:TODAY}", "c:/andrei/data/20090131");
        System.out.println(v.variableName + " = " + v.variableValue);
        newValue = "c:/andrei/data/20090131/early_morning";
        System.out.println(newValue + " > " + v.unResolve(newValue));
    }

    public PathVariableHelper() {
        super();
    }

    /**
     * The path variables are listed under
     * "Window ->Preferences ->General ->Workspace->Linked resources-> Defined path variables".
     * Usage is restricted to the first path segment only, see Javadoc for
     * {@link IPathVariableManager#resolvePath(org.eclipse.core.runtime.IPath)}.
     * 
     * @param path
     */
    public IPath resolveVariable(String path, IPath projectPath) {
        if (path == null || path.length() == 0) {
            return null;
        }
        // resolve //server/share/${env_var:CLIENT}/data to //server/share/andrei/data
        // or c:/${env_var:CLIENT}/data/${env_var:TODAY} to c:/andrei/data/20090131
        IStringVariableManager manager = VariablesPlugin.getDefault().getStringVariableManager();
        try {
            String substitution = manager.performStringSubstitution(path);
            if (!path.equals(substitution)) {
                anyVariable = new Variable(path, substitution);
                path = substitution;
            }
        } catch (CoreException e) {
            // ignore
        }

        IPath ipath;
        IWorkspace workspace = ResourcesPlugin.getWorkspace();

        if (FS.isWin32() && path.indexOf("/") >= 0 && !isUriIncluded(path)) {
            path = path.replace('/', '\\');
        }

        if (path.startsWith(FileMapping.MAP_WORKSPACE_RELATIVE)) {
            // make a real path relatetd to workspace
            ipath = workspace.getRoot().getLocation().makeAbsolute().append(path.substring(1));
            return ipath;
        }
        if (path.startsWith(FileMapping.MAP_PROJECT_RELATIVE)) {
            if (projectPath == null) {
                // project is deleted or closed?
                FileSyncPlugin.log("Cannot compute project relative path for: " + path, null,
                        IStatus.ERROR);
                return null;
            }
            // make a real path related to the project area
            ipath = projectPath.append(path.substring(1));
            return ipath;
        }

        IPathVariableManager pvm = workspace.getPathVariableManager();

        ipath = create(path);
        IPath path2 = pvm.resolvePath(ipath);
        if (!ipath.equals(path2)) {
            // here we could remember the path and variable
            // to be able later to encode it back
            pathVariableName = ipath.segment(0);
            pathVariableValue = path2.removeLastSegments(ipath.segmentCount() - 1);
            ipath = path2.makeAbsolute();
        } else {
            if (isUriIncluded(path)) {
                return ipath;
            }
            // This is the case where we could have an unresolved path variable.
            ipath = ipath.makeAbsolute();
            if (FS.isWin32()) {
                /*
                 * On Win32 it is very simply to detect, as all path names shoul be prepend with
                 * device or \\ as windows share name
                 */
                if (ipath.getDevice() == null && !ipath.isUNC()) {
                    FileSyncPlugin.log("Destination path does not have a device: " + ipath
                            + " and therefore default destination " + "will be used (if any)",
                            null, IStatus.ERROR);
                    return null;
                }
            } else {
                if (ipath.isEmpty() || !ipath.toOSString().startsWith("/")) {
                    /*
                     * On Linux we could not really easy distinguish it. For example WORK/blabla/
                     * means that WORK is directory name or just a variable? Unfortunately we need
                     * to sync even if destination folder is not yet exist (that's our mission :) As
                     * a solution, we should assume that path should always start with slash,
                     * otherwise it is not resolved path variable
                     */
                    FileSyncPlugin.log("Destination path does not have a leading slash: " + ipath
                            + " and therefore default destination " + "will be used (if any)",
                            null, IStatus.ERROR);
                    return null;
                }
            }
        }
        return ipath;
    }

    public String unResolveVariable(IPath path, IPath projectPath) {
        if (path == null) {
            return null;
        }
        // TODO un-resolve project
        IPath workspaceLocation = ResourcesPlugin.getWorkspace().getRoot().getLocation()
        .makeAbsolute();

        String pathStr;
        if (projectPath != null && projectPath.isPrefixOf(path) && !isUriIncluded(path)) {
            pathStr = FileMapping.MAP_PROJECT_RELATIVE
            + removeFirstSegments(path, projectPath.segmentCount());
        } else if (workspaceLocation.isPrefixOf(path) && !isUriIncluded(path)) {
            pathStr = FileMapping.MAP_WORKSPACE_RELATIVE
            + removeFirstSegments(path, workspaceLocation.segmentCount());
        } else if (pathVariableValue != null && pathVariableValue.isPrefixOf(path)) {
            pathStr = pathVariableName + "/"
            + removeFirstSegments(path, pathVariableValue.segmentCount());
        } else {
            pathStr = path.toPortableString();
        }
        if (anyVariable != null) {
            pathStr = anyVariable.unResolve(pathStr);
        }
        if (isUriIncluded(path)) {
            pathStr = getUri(create(path, pathStr)).toString();
        }
        return pathStr;
    }

    /**
     * Removes count segments *WITH* the device id. This is different to implementation from Path
     * class.
     * 
     * @param path
     * @param segmentCount
     */
    private static String removeFirstSegments(IPath path, int segmentCount) {
        path = path.removeFirstSegments(segmentCount);
        String strPath = path.toPortableString();
        int idx = strPath.indexOf(IPath.DEVICE_SEPARATOR);
        if (idx < 0) {
            return strPath;
        }
        strPath = strPath.substring(idx + 1);
        return strPath;
    }

    private boolean isUriIncluded(String pathStr) {
        return FileSyncPlugin.getDefault().getFsPathUtil().isUri(pathStr);
    }

    private boolean isUriIncluded(IPath path) {
        return FileSyncPlugin.getDefault().getFsPathUtil().isUriIncluded(path);
    }

    private IPath create(String path) {
        return FileSyncPlugin.getDefault().getFsPathUtil().create(path);
    }

    private IPath create(IPath path, String newPathStr) {
        return FileSyncPlugin.getDefault().getFsPathUtil().create(path, newPathStr);
    }

    private URI getUri(IPath path) {
        return FileSyncPlugin.getDefault().getFsPathUtil().getUri(path);
    }

}
