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
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import org.eclipse.core.runtime.Assert;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.URIUtil;

import de.loskutov.fs.FileSyncPlugin;

/**
 * http://forums.java.net/jive/thread.jspa?messageID=215672
 * http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=5086147
 * http://wiki.eclipse.org/Eclipse/UNC_Paths
 *
 * @author Volker
 */
public class FsUriPath extends Path {

    private static final int NOT_INITIALIZED = -1;
    public static final String FILE_SCHEME_TOKEN = "file";
    public static final String RSE_SCHEME_TOKEN = "rse";
    public static final Set<String> ALLOWED_SCHEMES;
    public static String DEFAULT_ENCODE_NAME = "UTF-8";

    static {
        HashSet<String> tmp = new HashSet<String>();
        tmp.add(FILE_SCHEME_TOKEN);
        tmp.add("rse");
        ALLOWED_SCHEMES = Collections.unmodifiableSet(tmp);
    }

    private final URI uri;
    private int hashCode = NOT_INITIALIZED;

    private FsUriPath(URI uri) {
        super(decode(uri.getPath()));
        this.uri = check(uri);
    }

    /**
     * @param uriToCheck
     * @return
     */
    private static URI check(URI uriToCheck) {
        Assert.isTrue(uriToCheck.getUserInfo() == null,
        "userinfo should be set with the remote-systems-view.");
        Assert.isTrue(uriToCheck.getPort() == -1,
        "port should be set with the remote-systems-view.");
        Assert.isTrue(uriToCheck.getQuery() == null, "query-part not implemented yet.");
        Assert.isTrue(uriToCheck.getFragment() == null, "fragment-part not implemented yet.");
        return uriToCheck;
    }

    public URI getUri() {
        return uri;
    }

    @Override
    public Object clone() {
        Path clone = (Path) super.clone();

        return clone;
    }

    @Override
    public IPath addFileExtension(String extension) {
        IPath path = super.addFileExtension(extension);
        return create(uri, path);
    }

    @Override
    public IPath addTrailingSeparator() {
        IPath path = super.addTrailingSeparator();
        return create(uri, path);
    }

    @Override
    public IPath append(IPath tail) {
        IPath path = super.append(tail);
        return create(uri, path);
    }

    @Override
    public IPath append(String tail) {
        IPath path = super.append(tail);
        return create(uri, path);
    }

    @Override
    public IPath makeAbsolute() {
        IPath path = super.makeAbsolute();
        return create(uri, path);
    }

    @Override
    public IPath makeRelative() {
        IPath path = super.makeRelative();
        return create(uri, path);
    }

    @Override
    public IPath makeRelativeTo(IPath base) {
        throw new UnsupportedOperationException();
    }

    @Override
    public IPath makeUNC(boolean toUNC) {
        IPath path = super.makeUNC(toUNC);
        return create(uri, path);
    }

    @Override
    public IPath removeFileExtension() {
        IPath path = super.removeFileExtension();
        return create(uri, path);
    }

    @Override
    public IPath removeFirstSegments(int count) {
        IPath path = super.removeFirstSegments(count);
        return create(uri, path);
    }

    @Override
    public IPath removeLastSegments(int count) {
        IPath path = super.removeLastSegments(count);
        return create(uri, path);
    }

    @Override
    public IPath removeTrailingSeparator() {
        IPath path = super.removeTrailingSeparator();
        return create(uri, path);
    }

    @Override
    public IPath setDevice(String value) {
        IPath path = super.setDevice(value);
        return create(uri, path);
    }

    @Override
    public IPath uptoSegment(int count) {
        IPath path = super.uptoSegment(count);
        return create(uri, path);
    }

    @Override
    public File toFile() {
        return FileSyncPlugin.getDefault().getFsPathUtil().toFile(this);
    }

    public static FsUriPath create(IPath path, String pathStr) {
        if (!(path instanceof FsUriPath)) {
            throw new IllegalArgumentException();
        }
        FsUriPath fsUriPath = (FsUriPath) path;
        return create(fsUriPath.getUri(), pathStr, path.isUNC());
    }

    public static FsUriPath create(URI oldUri, String pathStr, boolean unc/* not used atm */) {
        try {
            check(oldUri);
            URI newUri = createUri(oldUri.getScheme(), oldUri.getUserInfo(), oldUri.getHost(),
                    oldUri.getPort(), addWindowsDeviceSlash(pathStr), oldUri.getQuery(), oldUri
                    .getFragment());
            if (unc) {
                Assert.isTrue(newUri.getPath().startsWith("//"));
            }
            return new FsUriPath(newUri);
        } catch (Exception e) {
            throw new FileSyncException(e);
        }
    }

    private static URI createUri(String scheme, String userInfo, String host, int port,
            String path, String query, String fragment) {
        StringBuilder sb = new StringBuilder();
        sb.append(scheme).append("://");
        if (userInfo != null) {
            sb.append(userInfo).append("@");
        }
        if (host != null) {
            sb.append(host);
        }
        if (port != -1) {
            sb.append(":").append(port);
        }
        sb.append(addWindowsDeviceSlash(path));
        if (query != null) {
            throw new UnsupportedOperationException();
        }
        if (fragment != null) {
            throw new UnsupportedOperationException();
        }
        try {
            return check(URIUtil.fromString(decode(sb.toString())));
        } catch (URISyntaxException e) {
            throw new UnsupportedOperationException(e);
        }
    }

    public static String addWindowsDeviceSlash(String pathStr) {
        if (pathStr.matches("^[a-zA-Z]:.*")) {
            return "/" + pathStr;
        }
        return pathStr;
    }

    public static FsUriPath create(URI oldUri, IPath path) {
        return create(oldUri, (path.toPortableString().startsWith("/") ? "" : "/")
                + path.toPortableString(), path.isUNC());

    }

    public static FsUriPath create(String scheme, String hostName, String path) {
        try {
            URI uri = new URI(scheme, hostName, addWindowsDeviceSlash(path), null);
            return new FsUriPath(uri);
        } catch (URISyntaxException e) {
            throw new FileSyncException(e);
        }

    }

    public static Path create(String pathStr) {
        if (!FsPathUtilImpl.isUriLike(pathStr)) {
            return new Path(pathStr);
        }

        try {
            URI uri = URIUtil.fromString(decode(pathStr));
            return new FsUriPath(uri);
        } catch (URISyntaxException e) {
            throw new FileSyncException(e);
        }
    }

    public static boolean isUriIncluded(IPath path) {
        return path instanceof FsUriPath;
    }

    public static void main(String[] args) throws MalformedURLException, URISyntaxException {
        URL url = new URL("file:////hansenet/u/Share/TE/reports/temp/filesync");
        URI uri = URIUtil.toURI(url);
        File file = new File(uri);
        System.out.println(file.exists());

        uri = new URI("file:////hansenet/u/Share/TE/reports/temp/filesync");
        System.out.println(uri.toString());
        file = new File(uri);
        System.out.println(file.exists());
    }

    @Override
    public int hashCode() {
        if (hashCode == NOT_INITIALIZED) {
            final int prime = 31;
            hashCode = super.hashCode();
            hashCode = prime * hashCode + ((uri == null) ? 0 : uri.hashCode());
            hashCode = (hashCode == -1 ? 0 : hashCode);
        }
        return hashCode;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!super.equals(obj)) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        FsUriPath other = (FsUriPath) obj;
        if (uri == null) {
            if (other.uri != null) {
                return false;
            }
        } else if (hashCode != other.hashCode) {
            return false;
        } else if (!uri.equals(other.uri)) {
            return false;
        }
        return true;
    }

    public static String decode(String uriString){
        try {
            return java.net.URLDecoder.decode(uriString, DEFAULT_ENCODE_NAME);
        } catch (UnsupportedEncodingException e) {
            throw new IllegalArgumentException(  e);
        }
    }

    public static boolean isEncoded(String text){
        if( text == null) {
            return false;
        }

        return !decode(text).equals(text);
    }
}
