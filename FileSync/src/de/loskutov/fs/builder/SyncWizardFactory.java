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

public class SyncWizardFactory {

    private static final String DELAYEDSYNCWIZARD_CLASS_NAME = "de.loskutov.fs.rse.BulkSyncWizard";

    private static SyncWizardFactory instance;

    private boolean errorOnLoadOfDelayedSyncWizard;

    private volatile Boolean rseAvailable;

    private SyncWizardFactory() {
        /* singleton with access via #getInstance() */
    }

    public boolean isRseAvailable() {
        if (rseAvailable == null) {
            rseAvailable = Boolean.valueOf(createSyncWizard().getClass() != SyncWizard.class);
        }
        return rseAvailable.booleanValue();
    }

    public synchronized static SyncWizardFactory getInstance() {
        if (instance == null) {
            instance = new SyncWizardFactory();
        }
        return instance;
    }

    public SyncWizard createSyncWizard() {
        if (!errorOnLoadOfDelayedSyncWizard) {
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

}
