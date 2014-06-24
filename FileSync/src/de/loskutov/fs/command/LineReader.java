/*******************************************************************************
 * Copyright (c) 2009 Andrey Loskutov.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * Contributor:  Andrey Loskutov - initial API and implementation
 *******************************************************************************/

package de.loskutov.fs.command;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;

/**
 * A class for reading String lines from stream and preserve line ends.
 * Default implementation of BufferedReader skip line ends information, therefore
 * we want a safe implementation to avoid problems with postprocessing of stream.
 * This implementation uses a buffer for reading characters from underlined stream.
 * Some standard stream operations are not implemented and are not more
 * supported by LineReader. Use only {@link #readLine()} method or another stream
 * implementation ;).
 * @author Andrey
 */
public class LineReader {
    /** char buffer size */
    private static int charBufferSize = 8192;

    /** input buffer */
    private char[] charBuffer;

    /** index of last readed char in last readed line */
    private int lastLineEndIdx = -1;

    /** index of last input char in buffer */
    private int bufferEndIdx = -1;

    /** readed chars count, if negativ, then end of stream  has been reached */
    private int readSize = 0;

    /** true if after '\r' no characters found in buffer and we should check next char for '\n' */
    private boolean checkLF;

    private InputStreamReader inReader;

    /**
     * Create an InputStreamReader that uses the default charset.
     * @param  in   An InputStream
     */
    public LineReader(InputStream in, String encoding)
            throws UnsupportedEncodingException {
        if (encoding != null) {
            inReader = new InputStreamReader(in, encoding);
        }
        charBuffer = new char[charBufferSize];
    }

    /**
     * Create an InputStreamReader that uses the given charset.
     * @param  in
     */
    public LineReader(String in, String encoding) throws UnsupportedEncodingException {
        this(new ByteArrayInputStream(in.getBytes(encoding)), encoding);
    }

    /**
     * Read a line of text.  A line is considered to be terminated by any one
     * of a line feed ('\n'), a carriage return ('\r'), or a carriage return
     * followed immediately by a linefeed.
     *
     * @return <b>String</b> instance, contains next line from input stream,
     * including all line-termination characters, or null if the end of the stream has been reached
     *
     * @exception  IOException  If an I/O error occurs
     */
    public String readLineToString() throws IOException {
        StringBuffer sb = readLine();
        return sb == null ? null : sb.toString();
    }

    /**
     * Read a line of text.  A line is considered to be terminated by any one
     * of a line feed ('\n'), a carriage return ('\r'), or a carriage return
     * followed immediately by a linefeed.
     *
     * @return <b>StringBuffer</b> (not String!) instance, contains next line from input stream,
     * including all line-termination characters, or null if the end of the stream has been reached
     *
     * @exception  IOException  If an I/O error occurs
     */
    public StringBuffer readLine() throws IOException {
        if (readSize < 0) {
            return null;
        }
        StringBuffer line = null;

        // if no more characters are in buffer since last line read
        if (lastLineEndIdx + 1 == bufferEndIdx + 1) {
            lastLineEndIdx = -1;
            bufferEndIdx = -1;
        } else {
            // we have here "unreaded" chars in buffer
            // find next line end
            int newLineEndIdx = firstIndexOfLineEnd(charBuffer, lastLineEndIdx + 1,
                    bufferEndIdx + 1);
            // if found, return part of buffer, contains this line
            if (newLineEndIdx >= 0) {
                line = new StringBuffer(newLineEndIdx - lastLineEndIdx);
                line
                .append(partOfArray(charBuffer, lastLineEndIdx + 1,
                        newLineEndIdx + 1));
                lastLineEndIdx = newLineEndIdx;
                return line;
            }
            // buffer contains unreaded chars, but end of buffer has been reached without
            // next line end char; or after '\r' char no chars found in buffer
            // and we should check next char for '\n'
            // save "unreaded" chars
            char[] lastChars = partOfArray(charBuffer, lastLineEndIdx + 1,
                    bufferEndIdx + 1);
            line = new StringBuffer(lastChars.length);
            line.append(lastChars);

            // buffer is empty now
            bufferEndIdx = -1;
            // last line
            lastLineEndIdx = bufferEndIdx;
        }

        // main "read" loop: ends if stream ends, or we found line end in stream
        do {
            readSize = readFromStream();

            // end of stream
            if (readSize < 0) {
                return line;
            }
            // last char from last read
            int lastCharIdx = bufferEndIdx;
            bufferEndIdx += readSize;

            // test for '\n' in new chars if last char was '\r'
            if (checkLF) {
                lastLineEndIdx = lastCharIdx;
                checkLF = false;
                // it was single '\r', also we can finish search and return line
                if (charBuffer[lastCharIdx + 1] != '\n') {
                    return line;
                }
            }

            // find next line end
            int newLineEndIdx = firstIndexOfLineEnd(charBuffer, lastLineEndIdx + 1,
                    bufferEndIdx + 1);

            // depend on search results: end of line or end of buffer
            int newPartEndIdx = newLineEndIdx < 0 ? bufferEndIdx + 1 : newLineEndIdx + 1;

            // add new part to line
            char[] newPart = partOfArray(charBuffer, lastLineEndIdx + 1, newPartEndIdx);
            if (line == null) {
                line = new StringBuffer(newPart.length);
            }
            line.append(newPart);

            // re-set buffer end index, if buffer is full and line end is not found
            if (newLineEndIdx < 0 && bufferEndIdx + 1 == charBufferSize) { //
                bufferEndIdx = -1;
            }
            // -1 if line end not found!
            lastLineEndIdx = newLineEndIdx;
        } while (lastLineEndIdx < 0);

        return line;
    }

    /**
     * Read characters from underlined stream in internal buffer
     * @return number of readed characters, or -1 if end of stream has been reached
     * @throws IOException if read fails
     */
    private int readFromStream() throws IOException {
        if (inReader != null) {
            // read from underlined stream in buffer
            return inReader.read(charBuffer, bufferEndIdx + 1, charBufferSize
                    - (bufferEndIdx + 1));
        }
        return -1;
    }

    /**
     * Search for first occurence of line end character(s)
     * (also first line end index in given string).
     * A line end is considered to be any one
     * of a line feed ('\n'), a carriage return ('\r'), or a carriage return
     * followed immediately by a linefeed.
     * @param chars
     * @param startIdx start offset, inclusive
     * @param stopIdx stop offset, exclusive
     * @return -1 if no line end chars found, or if the last char (at index stopIdx-1) is '\r'.
     * Otherwise index of last end line character in first end line character sequence.
     */
    protected int firstIndexOfLineEnd(char[] chars, int startIdx, int stopIdx) {
        int result = -1;
        char current;
        for (int i = startIdx; i < stopIdx; i++) {
            // "\r\n" Windows "\n" Unix "\r" Mac
            current = chars[i];
            if (current == '\r') {
                checkLF = i + 1 >= stopIdx;
                if (!checkLF) {
                    if (chars[i + 1] == '\n') {
                        result = i + 1;
                    } else {
                        result = i;
                    }
                    // ok
                    break;
                }
            } else if (current == '\n') {
                result = i;
                // ok
                break;
            }
        }
        return result;
    }

    /**
     * Get part of given array between given indexes
     * @param source char array, cannot be null!
     * @param startIdx start offset, inclusive
     * @param stopIdx stop offset, exclusive
     * @return part of given array between startIdx, inclusive, and stopIdx, exclusive
     */
    protected static char[] partOfArray(char[] source, int startIdx, int stopIdx) {
        int size = (stopIdx - startIdx);
        if (source.length == size) {
            return source;
        }
        char[] newChars = new char[size];
        System.arraycopy(source, startIdx, newChars, 0, size);
        return newChars;
    }

    /**
     * Close underlined stream
     */
    public void close() throws IOException {
        if (inReader != null) {
            inReader.close();
        }
        charBuffer = null;
    }

}
