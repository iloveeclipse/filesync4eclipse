/*******************************************************************************
 * All rights reserved. This program and the accompanying materials
 * Copyright (c) 2009 Andrei Loskutov.
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * Contributor(s):
 * 	Andrei Loskutov - initial API and implementation
 *  Volker Wandmaker - #appendEncodeString(String, StringBuffer) added
 *******************************************************************************/
package de.loskutov.fs.properties;

import java.net.URL;
import java.util.ArrayList;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Path;

import de.loskutov.fs.command.FileMapping;

public class PathListElement {

	public static final String DESTINATION = "output";

	public static final String VARIABLES = "variables";

	public static final String EXCLUSION = "exclusion";

	public static final String INCLUSION = "inclusion";

	private final IProject fProject;

	private IPath fPath;

	private final IResource fResource;

	private FileMapping cachedMapping;

	private final ArrayList fChildren;

	public PathListElement(IProject project, IPath path, IResource res,
			IValueCallback defPath, IValueCallback defVars) {
		fProject = project;

		fPath = path;
		fChildren = new ArrayList();
		fResource = res;
		cachedMapping = null;

		createAttributeElement(INCLUSION, new Path[0], null);
		createAttributeElement(EXCLUSION, new Path[0], null);
		createAttributeElement(DESTINATION, null, defPath);
		createAttributeElement(VARIABLES, null, defVars);
	}

	public PathListElement(IProject project, FileMapping mapping,
			IValueCallback defPath, IValueCallback defVars) {
		fProject = project;

		fPath = new Path(project.getName());
		fPath = fPath.append(mapping.getSourcePath());

		fChildren = new ArrayList();
		fResource = null;
		cachedMapping = mapping;
		createAttributeElement(INCLUSION, mapping.getInclusionPatterns(), null);
		createAttributeElement(EXCLUSION, mapping.getExclusionPatterns(), null);
		createAttributeElement(DESTINATION, mapping.getDestinationPath(), defPath);
		createAttributeElement(VARIABLES, mapping.getVariablesPath(), defVars);
	}

	public FileMapping getMapping() {
		if (cachedMapping == null) {
			cachedMapping = createMapping();
		}
		return cachedMapping;
	}

	private FileMapping createMapping() {
		IPath outputLocation = (IPath) getAttribute(DESTINATION);
		IPath variables = (IPath) getAttribute(VARIABLES);
		IPath[] inclusionPattern = (IPath[]) getAttribute(INCLUSION);
		IPath[] exclusionPattern = (IPath[]) getAttribute(EXCLUSION);
		return new FileMapping(getWithoutProject(fPath), outputLocation, variables,
				inclusionPattern, exclusionPattern, fProject.getLocation());
	}

	private IPath getWithoutProject(IPath path) {
		if (fProject.getName().equals(path.segment(0))) {
			return path.removeFirstSegments(1);
		}
		return path;
	}

	/**
	 * Gets the class path entry path.
	 */
	public IPath getPath() {
		return fPath;
	}

	/**
	 * Entries without resource are either non existing or a variable entry
	 * External jars do not have a resource
	 */
	public IResource getResource() {
		return fResource;
	}

	public PathListElementAttribute setAttribute(String key, Object value,
			Object defaultValue) {
		PathListElementAttribute attribute = findAttributeElement(key);
		if (attribute == null) {
			return null;
		}
		attribute.setValue(value);
		attribute.setDefaultValue(defaultValue);
		attributeChanged(key);
		return attribute;
	}

	private PathListElementAttribute findAttributeElement(String key) {
		for (int i = 0; i < fChildren.size(); i++) {
			Object curr = fChildren.get(i);
			if (curr instanceof PathListElementAttribute) {
				PathListElementAttribute elem = (PathListElementAttribute) curr;
				if (key.equals(elem.getKey())) {
					return elem;
				}
			}
		}
		return null;
	}

	public Object getAttribute(String key) {
		PathListElementAttribute attrib = findAttributeElement(key);
		if (attrib != null) {
			return attrib.getValue();
		}
		return null;
	}

	private void createAttributeElement(String key, Object value, Object defaultValue) {
		fChildren.add(new PathListElementAttribute(this, key, value, defaultValue));
	}

	public Object[] getChildren(boolean hideOutputFolder, boolean hideVariables) {
		if (hideOutputFolder && hideVariables) {
			return new Object[] { findAttributeElement(INCLUSION),
					findAttributeElement(EXCLUSION) };
		}
		if (hideOutputFolder && !hideVariables) {
			return new Object[] { findAttributeElement(INCLUSION),
					findAttributeElement(EXCLUSION), findAttributeElement(VARIABLES) };
		}
		if (!hideOutputFolder && hideVariables) {
			return new Object[] { findAttributeElement(INCLUSION),
					findAttributeElement(EXCLUSION), findAttributeElement(DESTINATION) };
		}
		return fChildren.toArray();
	}

	private void attributeChanged(String key) {
		cachedMapping = null;
	}

	/*
	 * @see Object#equals(java.lang.Object)
	 */
	public boolean equals(Object other) {
		if (other != null && other.getClass().equals(getClass())) {
			PathListElement elem = (PathListElement) other;
			return elem.fPath.equals(fPath) && getMapping().equals(elem.getMapping());
		}
		return false;
	}

	/*
	 * @see Object#hashCode()
	 */
	public int hashCode() {
		return getMapping().hashCode();
	}

	/* (non-Javadoc)
	 * @see java.lang.Object#toString()
	 */
	public String toString() {
		return getMapping().toString();
	}

	/**
	 * Gets the project.
	 * @return Returns a IJavaProject
	 */
	public IProject getProject() {
		return fProject;
	}

	public static StringBuffer appendEncodeString(String pathStr, StringBuffer buf) {
		if (pathStr == null) {
			buf.append('[').append(']');
		}else{
			buf.append('[').append(pathStr.length()).append(']').append(pathStr);
		}
		return buf;
	}
	public static StringBuffer appendEncodePath(IPath path, StringBuffer buf) {
		return appendEncodeString(path==null?null:path.toString(), buf);
	}

	public static StringBuffer appendEncodedURL(URL url, StringBuffer buf) {
		return appendEncodeString(url==null?null:url.toExternalForm(), buf);
	}

	public StringBuffer appendEncodedSettings(StringBuffer buf) {
		appendEncodePath(fPath, buf).append(';');
		buf.append("false").append(';');
		IPath variables = (IPath) getAttribute(VARIABLES);
		appendEncodePath(variables, buf).append(';');
		IPath output = (IPath) getAttribute(DESTINATION);
		appendEncodePath(output, buf).append(';');
		IPath[] exclusion = (IPath[]) getAttribute(EXCLUSION);
		buf.append('[').append(exclusion.length).append(']');
		for (int i = 0; i < exclusion.length; i++) {
			appendEncodePath(exclusion[i], buf).append(';');
		}
		IPath[] inclusion = (IPath[]) getAttribute(INCLUSION);
		buf.append('[').append(inclusion.length).append(']');
		for (int i = 0; i < inclusion.length; i++) {
			appendEncodePath(inclusion[i], buf).append(';');
		}
		return buf;
	}

}
