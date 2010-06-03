/*******************************************************************************
 * Copyright (c) 2009 Andrei Loskutov.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * Contributor:  Andrei Loskutov - initial API and implementation
 *******************************************************************************/

package de.loskutov.fs.command;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;

/**
 * A class for writing String lines to stream and preserve a information about line ends.
 * @author Andrei
 */
public class LineWriter extends OutputStreamWriter {

	/**
	 * @param out
	 */
	public LineWriter(OutputStream out, String charsetName)
	throws UnsupportedEncodingException {
		super(out, charsetName);
	}

	/**
	 * Write given line to stream. No extra line end characters would be added, nor line changed by
	 * write operation.
	 * @param line <b>StringBuffer</b>, contains line <b>including</b> all line-termination
	 * characters. A line is considered to be terminated by any one
	 * of a line feed ('\n'), a carriage return ('\r'), or a carriage return
	 * followed immediately by a linefeed.
	 */
	public void writeLine(StringBuffer line) throws IOException {
		write(line.toString(), 0, line.length());
	}

	/**
	 * Write given line to stream. No extra line end characters would be added, nor line changed by
	 * write operation.
	 * @param line <b>String</b>, contains line <b>including</b> all line-termination
	 * characters. A line is considered to be terminated by any one
	 * of a line feed ('\n'), a carriage return ('\r'), or a carriage return
	 * followed immediately by a linefeed.
	 */
	public void writeLine(String line) throws IOException {
		write(line.toCharArray(), 0, line.length());
	}

}
