/*******************************************************************************
 * Copyright (c) 2009 Andrei Loskutov.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * Contributor:  Andrei Loskutov - initial API and implementation
 *******************************************************************************/
package de.loskutov.fs.preferences;

import org.eclipse.core.runtime.preferences.AbstractPreferenceInitializer;
import org.eclipse.jface.preference.IPreferenceStore;

import de.loskutov.fs.FileSyncPlugin;

/**
 * @author Andrei
 */
public class PreferencesInitializer extends AbstractPreferenceInitializer {

    @Override
    public void initializeDefaultPreferences() {
        IPreferenceStore store = FileSyncPlugin.getDefault().getPreferenceStore();
        store.setDefault(FileSyncConstants.KEY_ASK_USER, true);
    }

}
