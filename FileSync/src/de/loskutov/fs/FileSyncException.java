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
package de.loskutov.fs;

public class FileSyncException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public FileSyncException(String message, Throwable cause) {
        super(message, cause);
    }

    public FileSyncException(String message) {
        super(message);
    }

    public FileSyncException(Throwable cause) {
        super(cause);
    }

}
