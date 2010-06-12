/*******************************************************************************
 * Copyright (c) 2010 Volker Wandmaker.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * Contributor(s):
 * 	Volker Wandmaker - initial API and implementation
 *  Andrei Loskutov - refactoring
 *******************************************************************************/
package de.loskutov.fs.builder;

import org.eclipse.core.runtime.IStatus;
import org.osgi.framework.Bundle;
import org.osgi.framework.Version;

import de.loskutov.fs.FileSyncPlugin;

public class SyncWizardFactory {

    private static final String DELAYEDSYNCWIZARD_CLASS_NAME = "de.loskutov.fs.rse.BulkSyncWizard";

    public static final String RSE_SYMBOLIC_NAME = "org.eclipse.rse";
    private static final Version RSE_MIN_VERSION = new Version("3.1.0");
    public static final Version ZERO_VERSION = new Version("0.0.0");

    private static SyncWizardFactory instance;

    private boolean errorOnLoadOfDelayedSyncWizard = false;

    private final Boolean rseAvailable;
    private final Bundle rseBundle;
    private final Version rseMinVersion;

    private SyncWizardFactory(Bundle rseBundle, Version rseMinVersion) {
        this.rseBundle = rseBundle;
        this.rseMinVersion = rseMinVersion;
        this.rseAvailable = rseBundle!=null && rseBundle.getVersion().compareTo(rseMinVersion) >=0;
    }

    public boolean isRseAvailable() {
        if (rseAvailable == null) {
            throw new IllegalArgumentException("not initialized yet.");
        }
        return rseAvailable.booleanValue();
    }


    public synchronized static SyncWizardFactory setInstance(Bundle rseBundle) {
        return setInstance(rseBundle, RSE_MIN_VERSION);

    }
    /**
     * It's possible to reset SyncWizardFactory which is useful to create testcases.
     *
     * @param rseBundle
     * @param rseMinVersion
     * @return the (new) SyncWizardFactory
     */
    public synchronized static SyncWizardFactory setInstance(Bundle rseBundle, Version rseMinVersion) {
        if (instance == null) {
            instance = new SyncWizardFactory(rseBundle, rseMinVersion);
        }
        return instance;
    }


    public synchronized static SyncWizardFactory getInstance() {
        if (instance == null) {
            throw new IllegalArgumentException("use getInstance(boolean rseAvailable) first.");
        }
        return instance;
    }

    public SyncWizard createSyncWizard() {
        if (this.rseAvailable.booleanValue() && !errorOnLoadOfDelayedSyncWizard) {
            try {
                Class<?> wizardClass = Class.forName(DELAYEDSYNCWIZARD_CLASS_NAME, true, getClass()
                        .getClassLoader());
                return (SyncWizard) wizardClass.newInstance();
            } catch (Throwable e) {

                FileSyncPlugin.log("Rse is availabe, but " + DELAYEDSYNCWIZARD_CLASS_NAME + " failed.",
                        e, IStatus.WARNING);

                errorOnLoadOfDelayedSyncWizard = true;
            }
        }
        return new SyncWizard();
    }

    public String getRseRequirement() {
        return RSE_SYMBOLIC_NAME + " >= " + this.rseMinVersion;
    }

    public Bundle getRseBundle() {
        return rseBundle;
    }

    public Version getRseMinVersion() {
        return rseMinVersion;
    }


}
