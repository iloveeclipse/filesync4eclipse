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

import de.loskutov.fs.command.FsPathUtil;
import de.loskutov.fs.command.FsPathUtilImpl;

public class SyncWizardFactory {

    public static final String DELAYEDSYNCWIZARD_CLASS_NAME = "de.loskutov.fs.builder.DelayedSyncWizard";
    public static final String FS_PATH_UTIL_CLASS_NAME = "de.loskutov.fs.command.FsUriPathUtil";

    private static SyncWizardFactory instance;

    private boolean errorOnLoadOfDelayedSyncWizard;

    private boolean errorOnLoadOfFsPathUtil;

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
            try {
                Class<?> wizardClass = Class.forName(DELAYEDSYNCWIZARD_CLASS_NAME, true, getClass()
                        .getClassLoader());
                return (SyncWizard) wizardClass.newInstance();
            } catch (Throwable e) {
                errorOnLoadOfDelayedSyncWizard = true;
            }
        }
        return new SyncWizard();
    }

    @SuppressWarnings("unchecked")
    public FsPathUtil createFsPathUtil() {
        if (!errorOnLoadOfDelayedSyncWizard && !errorOnLoadOfFsPathUtil) {
            try {
                Class<FsPathUtil> wizardClass = (Class<FsPathUtil>) Class.forName(
                        FS_PATH_UTIL_CLASS_NAME, true, getClass().getClassLoader());
                return wizardClass.newInstance();
            } catch (Throwable e) {
                errorOnLoadOfFsPathUtil = true;
            }
        }
        return new FsPathUtilImpl();
    }

}
