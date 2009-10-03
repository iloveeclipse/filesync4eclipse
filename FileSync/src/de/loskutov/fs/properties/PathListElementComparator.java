/*******************************************************************************
 * Copyright (c) 2005 Andrei Loskutov.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the BSD License
 * which accompanies this distribution, and is available at
 * http://www.opensource.org/licenses/bsd-license.php
 * Contributor:  Andrei Loskutov - initial API and implementation
 *******************************************************************************/
package de.loskutov.fs.properties;

import java.io.Serializable;
import java.util.Comparator;

/**
 * @author Andrei
 */
public class PathListElementComparator implements Comparator, Serializable {

    /**
     * default
     */
    private static final long serialVersionUID = -6143935945692635274L;

    /* (non-Javadoc)
     * @see java.util.Comparator#compare(java.lang.Object, java.lang.Object)
     */
    public int compare(Object o1, Object o2) {
        if(!(o1 instanceof PathListElement) || !(o2 instanceof PathListElement)){
            return 0;
        }
        PathListElement path1 = (PathListElement) o1;
        PathListElement path2 = (PathListElement) o2;
        if(path1.getPath() != null && path2.getPath() != null){
            return path1.getPath().toString().compareTo(path2.getPath().toString());
        }
        return path1.toString().compareTo(path2.toString());
    }

}
