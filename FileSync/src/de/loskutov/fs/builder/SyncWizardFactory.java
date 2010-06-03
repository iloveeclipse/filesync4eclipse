/*******************************************************************************
 * Copyright (c) 2010 Volker Wandmaker.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * Contributor(s):
 * 	Volker Wandmaker - initial API and implementation
 *******************************************************************************/
package de.loskutov.fs.builder;

import org.eclipse.core.runtime.IStatus;

import de.loskutov.fs.FileSyncPlugin;
import de.loskutov.fs.command.FsPathUtil;
import de.loskutov.fs.command.FsPathUtilImpl;

public class SyncWizardFactory {

    public static final String DELAYEDSYNCWIZARD_CLASS_NAME = "de.loskutov.fs.builder.DelayedSyncWizard";
    public static final String FS_PATH_UTIL_CLASS_NAME = "de.loskutov.fs.command.FsUriPathUtil";

    private static SyncWizardFactory instance;

    private boolean errorOnLoadOfDelayedSyncWizard = false;

    private boolean errorOnLoadOfFsPathUtil = false;

    private SyncWizardFactory() {
        /* singleton with access via #getInstance() */
    }

    public static SyncWizardFactory getInstance() {
        if (instance == null) {
            instance = new SyncWizardFactory();
        }
        return instance;
    }

    public SyncWizard createSyncWizard() {
        if (!errorOnLoadOfDelayedSyncWizard && !errorOnLoadOfFsPathUtil) {
            boolean error = false;
            try {
                Class<?> wizardClass = Class.forName(DELAYEDSYNCWIZARD_CLASS_NAME, true, getClass()
                        .getClassLoader());
                SyncWizard w = (SyncWizard) wizardClass.newInstance();
                return w;
            } catch (NoClassDefFoundError e) {
                FileSyncPlugin.log("Wizard not found: ", e, IStatus.WARNING);
                error = true;
            } catch (ClassNotFoundException e) {
                FileSyncPlugin.log("Wizard not found: ", e, IStatus.WARNING);
                error = true;
            } catch (Throwable e) {
                FileSyncPlugin.log("Wizard: ", e, IStatus.WARNING);
                error = true;
            }
            if (error == true) {
                FileSyncPlugin.log("Wizard '" + DELAYEDSYNCWIZARD_CLASS_NAME
                        + "' not loaded. Load '" + SyncWizard.class.getCanonicalName(), null,
                        IStatus.INFO);
            }
            errorOnLoadOfDelayedSyncWizard = error;
        }
        return new SyncWizard();
    }

    @SuppressWarnings("unchecked")
    public FsPathUtil createFsPathUtil() {
        if (!errorOnLoadOfDelayedSyncWizard && !errorOnLoadOfFsPathUtil) {
            boolean error = false;
            try {
                Class<FsPathUtil> wizardClass = (Class<FsPathUtil>) Class.forName(
                        FS_PATH_UTIL_CLASS_NAME, true, getClass().getClassLoader());
                FsPathUtil w = wizardClass.newInstance();
                return w;
            } catch (NoClassDefFoundError e) {
                FileSyncPlugin.log("FsPathUtil not found: ", e, IStatus.WARNING);
                error = true;
            } catch (ClassNotFoundException e) {
                FileSyncPlugin.log("FsPathUtil not found: ", e, IStatus.WARNING);
                error = true;
            } catch (Throwable e) {
                FileSyncPlugin.log("FsPathUtil: ", e, IStatus.WARNING);
                error = true;
            }
            if (error == true) {
                FileSyncPlugin.log("FsPathUtil '" + FS_PATH_UTIL_CLASS_NAME
                        + "' not loaded. Load '" + FsPathUtil.class.getCanonicalName(), null,
                        IStatus.INFO);
            }
            errorOnLoadOfFsPathUtil = error;
        }
        return new FsPathUtilImpl();
    }

}
