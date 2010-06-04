/*******************************************************************************
 * Copyright (c) 2009 Andrei Loskutov.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * Contributor:  Andrei Loskutov - initial API and implementation
 *******************************************************************************/
package de.loskutov.fs;

import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.jface.resource.ImageRegistry;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.plugin.AbstractUIPlugin;
import org.osgi.framework.Version;

/**
 * The main plugin class to be used in the desktop.
 */
public class FileSyncPlugin extends AbstractUIPlugin {

    private static FileSyncPlugin plugin;
    /** used for tests to not to open dialogs */
    public static final String KEY_ASK_USER = "askUser";
    public static final String RSE_SYMBOLIC_NAME = "org.eclipse.rse";
    public static final Version RSE_MIN_VERSION = new Version("3.1.0");

    public static final String PLUGIN_ID = "de.loskutov.FileSync";


    /**
     * The constructor.
     */
    public FileSyncPlugin() {
        super();
        if (plugin != null) {
            throw new IllegalStateException("FileSync plugin is singleton!");
        }
        plugin = this;
    }

    /**
     * Returns the shared instance.
     */
    public static FileSyncPlugin getDefault() {
        return plugin;
    }

    public static void error(String message, Throwable error) {
        Shell shell = FileSyncPlugin.getShell();
        if (message == null) {
            message = "";
        }
        if (error != null) {
            message = message + " " + error.getMessage();
        }
        IPreferenceStore store = getDefault().getPreferenceStore();
        if (store.getBoolean(KEY_ASK_USER)) {
            MessageDialog.openError(shell, "FileSync error", message);
        }
        log(message, error, IStatus.ERROR);
    }

    /**
     * @param statusID
     *            one of IStatus. constants like IStatus.ERROR etc
     * @param error
     */
    public static void log(String messageID, Throwable error, int statusID) {
        if (messageID == null) {
            messageID = error.getMessage();
            if (messageID == null) {
                messageID = error.toString();
            }
        }
        Status status = new Status(statusID, PLUGIN_ID, 0, messageID, error);
        getDefault().getLog().log(status);
        if (getDefault().isDebugging()) {
            System.out.println(status);
        }
    }

    public static Shell getShell() {
        return getDefault().getWorkbench().getActiveWorkbenchWindow().getShell();
    }

    /**
     * Returns an image descriptor for the image file at the given plug-in relative path.
     *
     * @param path
     *            the path
     * @return the image descriptor
     */
    public static ImageDescriptor getImageDescriptor(String path) {
        if (path == null) {
            return null;
        }
        ImageRegistry imageRegistry = getDefault().getImageRegistry();
        ImageDescriptor imageDescriptor = imageRegistry.getDescriptor(path);
        if (imageDescriptor == null) {
            imageDescriptor = imageDescriptorFromPlugin(PLUGIN_ID, path);
            imageRegistry.put(path, imageDescriptor);
        }
        return imageDescriptor;
    }

    public static Image getImage(String path) {
        if (path == null) {
            return null;
        }
        // prefetch image, if not jet there
        ImageDescriptor imageDescriptor = getImageDescriptor(path);
        if (imageDescriptor != null) {
            return getDefault().getImageRegistry().get(path);
        }
        // TODO error message
        return null;
    }

    public String getRseRequirement() {
        return RSE_SYMBOLIC_NAME + " >= " + RSE_MIN_VERSION;
    }

}
