/*******************************************************************************
 * Copyright (c) 2010 Volker Wandmaker.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * Contributor(s):
 *  Volker Wandmaker - initial API and implementation
 *  Andrei Loskutov - refactoring
 *******************************************************************************/
package de.loskutov.fs.utils;

import java.io.IOException;
import java.io.OutputStream;

/**
 * Has a readable toString()-Method
 *
 * @author Volker
 */
public class NamedOutputStream extends OutputStream {

    private final OutputStream outputStream;
    private final String toString;

    public NamedOutputStream(OutputStream outputStream, String toString) {
        this.outputStream = outputStream;
        this.toString = toString;
    }

    @Override
    public String toString() {
        return toString;
    }

    @Override
    public void close() throws IOException {
        outputStream.close();
    }

    @Override
    public boolean equals(Object obj) {
        return outputStream.equals(obj);
    }

    @Override
    public void flush() throws IOException {
        outputStream.flush();
    }

    @Override
    public int hashCode() {
        return outputStream.hashCode();
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
        outputStream.write(b, off, len);
    }

    @Override
    public void write(byte[] b) throws IOException {
        outputStream.write(b);
    }

    @Override
    public void write(int b) throws IOException {
        outputStream.write(b);
    }

}
