/*******************************************************************************
 * Copyright (c) 2010 Volker Wandmaker.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * Contributor(s):
 * 	Volker Wandmaker - initial API and implementation
 *******************************************************************************/
package de.loskutov.fs.utils;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.security.SecureRandom;
import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;

import de.loskutov.fs.command.RemoteFileImpl;

/**
 * without references to any rse=packages
 */
public class RseSimpleUtils {

    static final SecureRandom random = new SecureRandom();

    private RseSimpleUtils() {/* just static methods */
    }

    /**
     * @param file
     * @return true if it is a "Remote System Explorer"-File
     */
    public static boolean isRseFile(File file) {
        return file instanceof RemoteFileImpl;
    }

    /**
     * copied and modified from java.io.File.generateFile
     *
     * @param prefix
     * @param suffix
     * @return
     */
    public static String getRandomName(String prefix, String suffix) {
        long n = random.nextLong();
        if (n == Long.MIN_VALUE) {
            n = 0; // corner case
        } else {
            n = Math.abs(n);
        }

        return prefix + Long.toString(n) + (suffix == null ? ".tmp" : suffix);
    }
    public static void write(OutputStream out, Collection<?> collection, boolean closeStreamWhenDone) {
        write(out, collection, null, closeStreamWhenDone );
    }
    public static void write(OutputStream out, Collection<?> collection, String lineDelimiter, boolean closeStreamWhenDone) {
        PrintWriter writer = new PrintWriter(out);

        for (Iterator<?> iterator = collection.iterator(); iterator.hasNext();) {
            Object object = iterator.next();
            if (object == null) {
                writer.print("Null");
            } else {
                writer.print(object.toString());
            }
            if(lineDelimiter==null){
                writer.println();
            }else{
                writer.print(lineDelimiter);
            }

        }
        if (closeStreamWhenDone) {
            writer.close();
        }
    }

    public static String[] toEnvArray(Map<String,String> env) {
        String[] ret = new String[env.size()];
        int i = 0;
        for (Iterator<Entry<String,String>> iterator = env.entrySet().iterator(); iterator.hasNext();) {
            Entry<String,String> entry = iterator.next();
            String key = entry.getKey();
            String value = entry.getValue();
            ret[i++] = key + "=" + value;
        }
        return ret;
    }

    public static class RedirectInputStream implements Runnable {

        private final InputStream inputStream;
        private final OutputStream outputStream;
        private final boolean closeOutWhenDone;

        public RedirectInputStream(InputStream inputStream, OutputStream outputStream,
                boolean closeOutWhenDone) {
            super();
            this.inputStream = inputStream;
            this.outputStream = outputStream;
            this.closeOutWhenDone = closeOutWhenDone;
        }

        public void run() {
            try {
                byte[] buffer = new byte[2048];
                int numBytesRead = -1;
                while (((numBytesRead = inputStream.read(buffer)) != -1)) {
                    outputStream.write(buffer, 0, numBytesRead);
                }
            } catch (IOException e) {
                throw new IllegalArgumentException(e);
            } finally {
                if (closeOutWhenDone) {
                    try {
                        outputStream.close();
                    } catch (IOException e) {
                        throw new IllegalArgumentException(e);
                    }
                }
            }

        }

        public void flush() throws IOException {
            outputStream.flush();
        }

    }

    public static boolean transferTo(InputStream in, OutputStream out, boolean closeWhenDone)
    throws IOException {
        int packetSize = 1024 * 32;
        byte[] t = new byte[packetSize];
        int numBytesRead = 0;
        while ((numBytesRead = in.read(t)) != -1) {
            out.write(t, 0, numBytesRead);
        }
        out.flush();
        if (closeWhenDone) {
            out.close();
        }
        return true;
    }

}
