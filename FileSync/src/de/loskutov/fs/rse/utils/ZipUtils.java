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
package de.loskutov.fs.rse.utils;

import java.io.File;
import java.io.FileInputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.zip.ZipOutputStream;

import de.loskutov.fs.command.CopyDelegate;

public class ZipUtils {

    public static final int BUFFER_SIZE = 10240;

    /**
     * The list of files is iterated in descending ordered. If a {@link FileRecord#getTargetName()}
     * already exists the new one is ignored.
     *
     * @param outputStream
     * @param tobeJared
     * @param useCurrentDateForDestinationFiles
     * @return
     */
    public static ZipOutputStream zipStream(OutputStream outputStream, FileRecord[] tobeJared,
            boolean useCurrentDateForDestinationFiles) {
        try {
            byte buffer[] = new byte[BUFFER_SIZE];
            // Open archive file

            ZipOutputStream out = new ZipOutputStream(outputStream);
            Map<String, FileRecord> seen = new HashMap<String, FileRecord>(tobeJared.length);
            for (int i = tobeJared.length - 1; i >= 0; i--) {
                if (tobeJared[i] == null || !tobeJared[i].getSource().exists()
                        || tobeJared[i].getSource().isDirectory()) {
                    continue; // Just in case...
                }
                FileRecord fr = seen.get(tobeJared[i].getTargetName());
                if (fr != null) {
                    continue;
                }
                seen.put(tobeJared[i].getTargetName(), tobeJared[i]);
                // Add archive entry
                JarEntry jarAdd = new JarEntry(tobeJared[i].getTargetName());
                if (!useCurrentDateForDestinationFiles) {
                    jarAdd.setTime(tobeJared[i].getSource().lastModified());
                }
                out.putNextEntry(jarAdd);

                // Write file to archive
                FileInputStream in = new FileInputStream(tobeJared[i].getSource());
                if (tobeJared[i].getCopyDelegate() != null) {
                    tobeJared[i].getCopyDelegate().copyStreams(in, out,
                            CopyDelegate.KEEP_BOTH_OPEN_OPTION);
                } else {
                    while (true) {
                        int nRead = in.read(buffer, 0, buffer.length);
                        if (nRead <= 0) {
                            break;
                        }
                        out.write(buffer, 0, nRead);
                    }
                }
                in.close();
            }

            out.close();
            return out;
        } catch (Exception ex) {
            throw new IllegalArgumentException(ex);
        }
    }

    public static class FileRecord implements Comparable<FileRecord> {

        private final File source;
        private final String targetName;
        private CopyDelegate copyDelegate;

        public String getTargetName() {
            return targetName;
        }

        public FileRecord(File source, String targetName) {
            super();
            this.source = source;
            this.targetName = targetName;
        }

        public CopyDelegate getCopyDelegate() {
            return copyDelegate;
        }

        public FileRecord setCopyDelegate(CopyDelegate copyDelegate) {
            this.copyDelegate = copyDelegate;
            return this;
        }

        public File getSource() {
            return source;
        }

        @Override
        public String toString() {
            return source.toString() + " to " + targetName;
        }

        public int compareTo(FileRecord other) {
            return source.compareTo( other.source);
        }

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result + ((source == null) ? 0 : source.hashCode());
            return result;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            FileRecord other = (FileRecord) obj;
            if (source == null) {
                if (other.source != null) {
                    return false;
                }
            } else if (!source.equals(other.source)) {
                return false;
            }
            return true;
        }

    }

}
