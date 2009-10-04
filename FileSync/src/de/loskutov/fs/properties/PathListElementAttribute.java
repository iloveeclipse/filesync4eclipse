/*******************************************************************************
 * Copyright (c) 2009 Andrei Loskutov.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * Contributor:  Andrei Loskutov - initial API and implementation
 *******************************************************************************/
package de.loskutov.fs.properties;


public class PathListElementAttribute {

    private PathListElement parent;
    private String key;
    private Object value;
    private Object defaultValue;

    public PathListElementAttribute(PathListElement parentEl, String myKey, Object myValue, Object defaultValue) {
        key = myKey;
        value = myValue;
        parent = parentEl;
        this.defaultValue = defaultValue;
    }

    public PathListElement getParent() {
        return parent;
    }

    /**
     * Returns the key.
     * @return String
     */
    public String getKey() {
        return key;
    }

    /**
     * Returns the value.
     * @return Object
     */
    public Object getValue() {
        return value;
    }

    /**
     * Returns the value.
     */
    public void setValue(Object myValue) {
        value = myValue;
    }

    public void setDefaultValue(Object defaultValue) {
        this.defaultValue = defaultValue;
    }

    /**
     * @return the defaultValue
     */
    public Object getDefaultValue() {
        if(defaultValue instanceof IValueCallback){
            IValueCallback callback = (IValueCallback) defaultValue;
            return callback.getValue();
        }
        return defaultValue;
    }

}

interface IValueCallback {
    public Object getValue();
}

