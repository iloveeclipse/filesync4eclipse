/*******************************************************************************
 * Copyright (c) 2010 Volker Wandmaker.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * Contributor(s):
 * 	Volker Wandmaker - initial API and implementation
 *******************************************************************************/
package de.loskutov.fs.command;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.zip.ZipOutputStream;

public class ZipUtils {

    public static final boolean USE_CURRENT_DATE_FOR_DESTINATION_FILES = true;
    public static final boolean DEFAULT_USE_CURRENT_DATE_FOR_DESTINATION_FILES = !USE_CURRENT_DATE_FOR_DESTINATION_FILES;

    public static final int BUFFER_SIZE = 10240;

    public static ZipOutputStream zipStream(OutputStream outputStream, FileRecord[] tobeJared) {
        return zipStream(outputStream, tobeJared, DEFAULT_USE_CURRENT_DATE_FOR_DESTINATION_FILES);
    }

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
                    tobeJared[i].getCopyDelegate().copyInternal(in, out,
                            AbstractCopyDelegate.KEEP_BOTH_OPEN_OPTION);
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

    public static void createZip(File archiveFile, FileRecord[] tobeJared) {
        try {
            zipStream(new FileOutputStream(archiveFile), tobeJared);
        } catch (FileNotFoundException e) {
            throw new IllegalArgumentException(e);
        }
    }

    public static class FileRecord implements Comparable<FileRecord> {

        private final File source;
        private final String targetName;
        private AbstractCopyDelegate copyDelegate;

        public String getTargetName() {
            return targetName;
        }

        public FileRecord(File source) {
            this(source, source.getName());
        }

        public FileRecord(File source, String targetName) {
            super();
            this.source = source;
            this.targetName = targetName;
        }

        public AbstractCopyDelegate getCopyDelegate() {
            return copyDelegate;
        }

        public FileRecord setCopyDelegate(AbstractCopyDelegate copyDelegate) {
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

    public static abstract class AbstractCopyDelegate {

        public static final int KEEP_BOTH_OPEN_OPTION = 0;
        public static final int CLOSE_READER_OPTION = 1;
        public static final int CLOSE_WRITER_OPTION = 2;
        public static final int CLOSE_BOTH_OPTION = CLOSE_READER_OPTION | CLOSE_WRITER_OPTION;
        public static final int DEFAULT_OPTIONS = CLOSE_BOTH_OPTION;

        protected abstract boolean copyInternal(InputStream source, OutputStream destination,
                int options);

    }

    /**
     * Has a readable toString()-Method
     *
     * @author Volker
     */
    public static class LocalFileInputStream extends FileInputStream {

        private final String toString;

        public LocalFileInputStream(File file) throws FileNotFoundException {
            super(file);
            toString = file.toString();
        }

        @Override
        public String toString() {
            return toString;
        }

    }

    /**
     * Has a readable toString()-Method
     *
     * @author Volker
     */
    public static class LocalOutputStream extends OutputStream {

        public static final boolean APPEND = true;
        public static final boolean DEFAULT_APPEND = !APPEND;

        private final OutputStream outputStream;
        private final String toString;

        public LocalOutputStream(OutputStream outputStream, String toString) {
            this.outputStream = outputStream;
            this.toString = toString;

        }

        public LocalOutputStream(File file, boolean append) throws FileNotFoundException {
            this(new FileOutputStream(file, append), file.toString());
        }

        public LocalOutputStream(File file) throws FileNotFoundException {
            this(new FileOutputStream(file, DEFAULT_APPEND), file.toString());
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

}
