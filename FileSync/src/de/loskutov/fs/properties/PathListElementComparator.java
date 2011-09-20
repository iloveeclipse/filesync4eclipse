/*******************************************************************************
 * Copyright (c) 2011 Andrey Loskutov.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * Contributor:  Andrey Loskutov - initial API and implementation
 *******************************************************************************/
package de.loskutov.fs.properties;

import java.io.Serializable;
import java.util.Comparator;

/**
 * @author Andrei
 */
public class PathListElementComparator implements Comparator<PathListElement>, Serializable {

    /**
     * default
     */
    private static final long serialVersionUID = -6143935945692635274L;

    public int compare(PathListElement path1, PathListElement path2) {
        if((path1 == null) || (path2 == null)){
            return 0;
        }
        if(path1.getPath() != null && path2.getPath() != null){
            return path1.getPath().toString().compareTo(path2.getPath().toString());
        }
        return path1.toString().compareTo(path2.toString());
    }

}
