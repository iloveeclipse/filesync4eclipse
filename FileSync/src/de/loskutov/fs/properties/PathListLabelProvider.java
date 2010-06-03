/*******************************************************************************
 * Copyright (c) 2009 Andrei Loskutov.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * Contributor:  Andrei Loskutov - initial API and implementation
 *******************************************************************************/
package de.loskutov.fs.properties;

import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.IPath;
import org.eclipse.jface.viewers.LabelProvider;
import org.eclipse.swt.graphics.Image;
import org.eclipse.ui.ISharedImages;
import org.eclipse.ui.ide.IDE;

import de.loskutov.fs.FileSyncPlugin;

public class PathListLabelProvider extends LabelProvider {

    private static final String DEFAULT_FILE_WITH_VARIABLES = "(Default file)";

    private static final String FILE_WITH_VARIABLES = "Variables substitution: ";

    private static final String DEFAULT_TARGET_FOLDER = "(Default)";

    private static final String TARGET_FOLDER = "Target folder: ";

    public static final String EXCLUDED = "Excluded: ";

    public static final String INCLUDED = "Included: ";

    public PathListLabelProvider() {
        super();
    }

    @Override
    public String getText(Object element) {
        if (element instanceof PathListElement) {
            return getPathListElementText((PathListElement) element);
        } else if (element instanceof PathListElementAttribute) {
            return getPathListElementAttributeText((PathListElementAttribute) element);
        }
        return super.getText(element);
    }

    public static int compare(String label1, String label2){
        int sourceOrder = getOrder(label1);
        int targetOrder = getOrder(label2);
        if(sourceOrder > targetOrder){
            return 1;
        } else if(sourceOrder < targetOrder){
            return -1;
        }
        return 0;
    }

    public static int getOrder(String s){
        if(s.startsWith(INCLUDED)){
            return 1;
        }
        if(s.startsWith(EXCLUDED)){
            return 2;
        }
        if(s.startsWith(DEFAULT_TARGET_FOLDER) || s.startsWith(TARGET_FOLDER)){
            return 3;
        }
        if(s.startsWith(DEFAULT_FILE_WITH_VARIABLES) || s.startsWith(FILE_WITH_VARIABLES)
                || s.startsWith("Variables substitution disabled")){
            return 4;
        }
        return 0;
    }

    public String getPathListElementAttributeText(PathListElementAttribute attrib) {
        String notAvailable = "(None)";
        StringBuffer buf = new StringBuffer();
        String key = attrib.getKey();
        if (key.equals(PathListElement.DESTINATION)) {
            buf.append(TARGET_FOLDER);
            IPath path = (IPath) attrib.getValue();
            if (path != null) {
                buf.append(path.toOSString());
            } else {
                buf.append(DEFAULT_TARGET_FOLDER);
            }
        } else if (key.equals(PathListElement.VARIABLES)) {
            IPath path = (IPath) attrib.getValue();
            if (path == null) {
                buf.append("Variables substitution disabled");
            } else if (!path.equals(attrib.getDefaultValue())) {
                buf.append(FILE_WITH_VARIABLES);
                if(attrib.getDefaultValue() != null) {
                    buf.append("Default file + ");
                }
                buf.append(path.toPortableString());
            } else if(attrib.getDefaultValue() != null){
                buf.append(FILE_WITH_VARIABLES);
                buf.append(DEFAULT_FILE_WITH_VARIABLES);
            } else {
                buf.append("Variables substitution disabled");
            }
        } else if (key.equals(PathListElement.EXCLUSION)) {
            buf.append(EXCLUDED);
            IPath[] patterns = (IPath[]) attrib.getValue();
            if (patterns != null && patterns.length > 0) {
                for (int i = 0; i < patterns.length; i++) {
                    if (i > 0) {
                        buf.append("; ");
                    }
                    buf.append(patterns[i].toString());
                }
            } else {
                buf.append(notAvailable);
            }
        } else if (key.equals(PathListElement.INCLUSION)) {
            buf.append(INCLUDED);
            IPath[] patterns = (IPath[]) attrib.getValue();
            if (patterns != null && patterns.length > 0) {
                for (int i = 0; i < patterns.length; i++) {
                    if (i > 0) {
                        buf.append("; ");
                    }
                    buf.append(patterns[i].toString());
                }
            } else {
                buf.append("(All)");
            }
        }
        return buf.toString();
    }

    public String getPathListElementText(PathListElement cpentry) {
        IPath path = cpentry.getPath();

        StringBuffer buf = new StringBuffer(path.makeRelative().toString());
        IResource resource = cpentry.getResource();
        if (resource != null && !resource.exists()) {
            buf.append(' ');
            buf.append("new");
        }
        return buf.toString();
    }


    @Override
    public Image getImage(Object element) {
        if (element instanceof PathListElement) {
            PathListElement cpentry = (PathListElement) element;
            String key = null;
            if (cpentry.getPath().segmentCount() == 1) {
                key = IDE.SharedImages.IMG_OBJ_PROJECT;
            } else {
                key = ISharedImages.IMG_OBJ_FOLDER;
            }
            return FileSyncPlugin.getDefault().getWorkbench().getSharedImages().getImage(key);
        } else if (element instanceof PathListElementAttribute) {
            String key = ((PathListElementAttribute) element).getKey();
            if (key.equals(PathListElement.VARIABLES)) {
                Object value = ((PathListElementAttribute) element).getValue();
                Object defValue = ((PathListElementAttribute) element).getDefaultValue();
                if(value == null){
                    return FileSyncPlugin.getImage("icons/no_variables.gif");
                }
                if(value.equals(defValue) || defValue == null){
                    return FileSyncPlugin.getImage("icons/variables.gif");
                }
                return FileSyncPlugin.getImage("icons/extra_variables.gif");
            } else if (key.equals(PathListElement.DESTINATION)) {
                return FileSyncPlugin.getImage("icons/output_folder.gif");
            } else if (key.equals(PathListElement.EXCLUSION)) {
                return FileSyncPlugin.getImage("icons/remove_from_path.gif");
            } else if (key.equals(PathListElement.INCLUSION)) {
                return FileSyncPlugin.getImage("icons/add_to_path.gif");
            }
        }
        return null;
    }

}
