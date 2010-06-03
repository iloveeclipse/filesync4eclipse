package test;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Iterator;
import java.util.Properties;
import java.util.Random;
import java.util.Set;

import junit.framework.TestCase;
import de.loskutov.fs.command.CopyDelegate;
import de.loskutov.fs.command.CopyDelegate;
import de.loskutov.fs.command.FS;

public class TestFS extends TestCase {

    private File tempDir;

    private File tempFile1;

    private File tempFile2;

    private File tempFile3;

    static final char[] CHARS = "0123456789abcdefghijklmnopqrstuvwxyz".toCharArray();
    static final String PLUGUN_ID = "FileSync_test";
    @Override
    protected void setUp() throws Exception {
        super.setUp();
        FS.enableLogging = false;
        tempDir = new File(System.getProperty("java.io.tmpdir") + File.separator
                + "fstestDir");
        tempFile1 = new File(tempDir.getAbsolutePath() + File.separator + "fstestFile1.txt");
        tempFile2 = new File(tempDir.getAbsolutePath() + File.separator + "fstestFile2.txt");
        tempFile3 = new File(tempDir.getAbsolutePath() + File.separator + "fstestFile3.txt");
    }

    @Override
    protected void tearDown() throws Exception {
        if (tempFile1.exists()) {
            tempFile1.delete();
        }
        if (tempFile2.exists()) {
            tempFile2.delete();
        }
        if (tempFile3.exists()) {
            tempFile3.delete();
        }
        if (tempDir.exists()) {
            tempDir.delete();
        }
        FS.enableLogging = true;
        super.tearDown();
    }

    public void testCreate() {
        File file = tempDir;
        boolean result = FS.create(file, false);
        assertTrue(result);
        assertTrue(file.isDirectory());
        result = FS.create(file, false);
        assertTrue(result);

        result = FS.create(file, true);
        assertFalse(result);
        assertFalse(file.isFile());

        file = tempFile1;
        result = FS.create(file, true);
        assertTrue(result);
        assertTrue(file.isFile());
        result = FS.create(file, true);
        assertTrue(result);

        result = FS.create(file, false);
        assertFalse(result);
        assertFalse(file.isDirectory());
    }

    public void testDelete() {
        File dir = tempDir;
        FS.create(dir, false);
        assertTrue(dir.isDirectory());
        boolean result = FS.delete(dir, true);
        assertTrue(result);
        assertFalse(dir.exists());

        FS.create(dir, false);
        assertTrue(dir.isDirectory());
        result = FS.delete(dir, false);
        assertTrue(result);
        assertFalse(dir.exists());

        FS.create(dir, false);
        File file = tempFile1;
        FS.create(file, true);
        assertTrue(file.exists());
        result = FS.delete(dir, false);
        assertFalse(result);
        assertTrue(dir.exists());
        assertTrue(file.exists());

        result = FS.delete(dir, true);
        assertTrue(result);
        assertFalse(dir.exists());
        assertFalse(file.exists());

        FS.create(file, true);
        assertTrue(file.exists());
        result = FS.delete(file, false);
        assertTrue(result);
        result = FS.delete(file, true);
        assertTrue(result);
        result = FS.delete(file, false);
        assertTrue(result);
    }

    public void testIsSame() throws Exception {
        byte[] randomBytes = createRandomBytes(500);

        File file1 = tempFile1;
        FS.create(file1, true);
        assertTrue(file1.isFile());

        FileOutputStream fos1 = new FileOutputStream(file1, false);
        fos1.write(randomBytes);
        fos1.close();

        // to get different file stamp
        synchronized (this) {
            wait(500);
        }

        File file2 = tempFile2;
        FS.create(file2, true);
        assertTrue(file2.isFile());

        FileOutputStream fos2 = new FileOutputStream(file2, false);
        fos2.write(randomBytes);
        fos2.close();

        assertTrue(file1.length() > 0);
        assertEquals(file1.length(), file2.length());
        long lastModified1 = file1.lastModified();
        long lastModified2 = file2.lastModified();
        assertFalse(lastModified1 == lastModified2);

        // timestamp is different
        boolean result = isSame(file1, file2, true, true);
        assertFalse(result);
        // timestamp / content is same
        file2.setLastModified(lastModified1);
        result = isSame(file1, file2, true, true);
        assertTrue(result);

        // change one byte => different timestamp and content
        randomBytes[0] = (byte) (randomBytes[0] + 1);
        FS.delete(file2, false);
        FS.create(file2, true);
        assertTrue(file2.isFile());
        assertTrue(file2.canWrite());

        fos2 = new FileOutputStream(file2, false);
        fos2.write(randomBytes);
        fos2.close();

        assertEquals(file1.length(), file2.length());
        // timestamp + content are different
        result = isSame(file1, file2, true, true);
        assertFalse(result);
        // timestamp ok, content is different
        file2.setLastModified(lastModified1);
        result = isSame(file1, file2, true, true);
        assertFalse(result);

        randomBytes[0] = (byte) (randomBytes[0] - 1);
        FS.delete(file2, false);
        FS.create(file2, true);
        assertTrue(file2.isFile());
        assertTrue(file2.canWrite());
        fos2 = new FileOutputStream(file2, false);
        fos2.write(randomBytes);
        fos2.close();

        assertEquals(file1.length(), file2.length());
        // timestamp is different, content is same
        result = isSame(file1, file2, true, true);
        assertFalse(result);
        // timestamp ok, content ok
        file2.setLastModified(lastModified1);
        result = isSame(file1, file2, true, true);
        assertTrue(result);
    }

    public void testCopy() throws Exception {
        byte[] randomBytes = createRandomBytes(500);

        File file1 = tempFile1;
        FS.create(file1, true);
        assertTrue(file1.isFile());

        FileOutputStream fos1 = new FileOutputStream(file1, false);
        fos1.write(randomBytes);
        fos1.close();

        File file2 = tempFile2;
        FS.delete(file2, true);
        assertFalse(file2.exists());
        FS.create(file2, true);
        boolean result = FS.copy(file1, file2, false);
        assertTrue(result);
        assertTrue(file2.isFile());
        assertTrue(isSame(file1, file2, true, true));

        synchronized (this) {
            wait(50);
        }
        result = FS.copy(file1, file2, true);
        assertTrue(result);
        assertTrue(file2.isFile());
        assertFalse(isSame(file1, file2, true, true));

        result = FS.copy(file1, file2, false);
        assertTrue(result);
        assertTrue(file2.isFile());
        assertTrue(isSame(file1, file2, true, true));

        result = FS.copy(file1, file2, false);
        assertTrue(result);
        assertTrue(file2.isFile());
        assertTrue(isSame(file1, file2, true, true));

        randomBytes = createRandomBytes(500);
        FileOutputStream fos2 = new FileOutputStream(file2, false);
        fos2.write(randomBytes);
        fos2.close();

        result = FS.copy(file2, file1, false);
        assertTrue(result);
        assertTrue(file2.isFile());
        assertTrue(isSame(file1, file2, true, true));
    }

    public void testCopyDelegate1() throws Exception {
        CopyDelegate cd = new CopyDelegate();
        cd.setUseCurrentDateForDestinationFiles(false);
        copyDelegateTest(cd);
    }

    public void testCopyDelegate2()  throws Exception {
        CopyDelegate cd = new CopyDelegate();
        cd.setUseCurrentDateForDestinationFiles(false);
        copyDelegateTest(cd);
    }

    private void copyDelegateTest(CopyDelegate cd) throws Exception {
        Properties props = new Properties();
//        Bundle bundle = Platform.getBundle(PLUGUN_ID);
        URL resource = getClass().getResource("/variables.properties");
        InputStream stream = resource.openStream();
        props.load(stream);
        stream.close();
        cd.setEncoding("ISO-8859-1");
        cd.setPropertiesMap(props);
        byte[][] randomBytesArray = createRandomBytes(props, 5);

        byte [] randomBytes = randomBytesArray[0];
        byte [] randomBytesOrig = randomBytesArray[1];

        File file1 = tempFile1;
        FS.create(file1, true);
        assertTrue(file1.isFile());

        FileOutputStream fos1 = new FileOutputStream(file1, false);
        fos1.write(randomBytes);
        fos1.close();

        File file3 = tempFile3;
        FS.create(file3, true);
        assertTrue(file3.isFile());

        fos1 = new FileOutputStream(file3, false);
        fos1.write(randomBytesOrig);
        fos1.close();

        file3.setLastModified(file1.lastModified());

        assertEquals(file3.lastModified(), file1.lastModified());

        File file2 = tempFile2;
        FS.delete(file2, true);
        assertFalse(file2.exists());
        FS.create(file2, true);
        boolean result = cd.copy(file1, file2);
        assertTrue(result);
        assertTrue(file2.isFile());
        assertTrue(isSame(file3, file2, true, true));

        cd.setUseCurrentDateForDestinationFiles(true);
        synchronized (this) {
            wait(50);
        }
        result = cd.copy(file1, file2);
        assertTrue(result);
        assertTrue(file2.isFile());
        assertFalse(isSame(file3, file2, true, true));
        cd.setUseCurrentDateForDestinationFiles(false);

        result = cd.copy(file1, file2);
        assertTrue(result);
        assertTrue(file2.isFile());
        assertTrue(isSame(file3, file2, true, true));

        result = cd.copy(file1, file2);
        assertTrue(result);
        assertTrue(file2.isFile());
        assertTrue(isSame(file3, file2, true, true));

        randomBytesArray = createRandomBytes(props, 5);

        randomBytes = randomBytesArray[0];
        randomBytesOrig = randomBytesArray[1];

        FileOutputStream fos2 = new FileOutputStream(file2, false);
        fos2.write(randomBytes);
        fos2.close();

        file3 = tempFile3;
        FS.create(file3, true);
        assertTrue(file3.isFile());

        fos1 = new FileOutputStream(file3, false);
        fos1.write(randomBytesOrig);
        fos1.close();
        file3.setLastModified(file2.lastModified());
        assertEquals(file3.lastModified(), file2.lastModified());

        result = cd.copy(file2, file1);
        assertTrue(result);
        assertTrue(file2.isFile());
        assertTrue(isSame(file3, file1, true, true));
    }

    static ByteArrayInputStream createRandomContent(long fileSize) {
        byte[] bytes = createRandomBytes(fileSize);
        return new ByteArrayInputStream(bytes);
    }

    static ByteArrayInputStream[] createRandomContent(Properties props) {
        byte[][] bytes = createRandomBytes(props, 5);
        return new ByteArrayInputStream[] { new ByteArrayInputStream(bytes[0]),
                new ByteArrayInputStream(bytes[1]) };
    }

    static byte[][] createRandomBytes(Properties props, int repeats) {
        StringBuilder sb1 = new StringBuilder(props.size() * 20);
        StringBuilder sb2 = new StringBuilder(sb1.capacity());

        Random r = new Random();
        Set keys = props.keySet();
        int innerRuns = repeats;
        for (int k = 0; k < repeats; k++) {
            for (Iterator iter = keys.iterator(); iter.hasNext();) {
                String key = (String) iter.next();
                String property = props.getProperty(key);
                StringBuilder randomString = getRandomString(10, r);
                for (int i = 0; i < innerRuns; i++) {
                    if(i != 0){
                        sb1.append(randomString);
                        sb2.append(randomString);
                    }
                    sb1.append("${").append(key).append("}");
                    if(property != null) {
                        sb2.append(property);
                    }
                    if(i == 0){
                        sb1.append(randomString);
                        sb2.append(randomString);
                    }
                }
                sb1.append("\n");
                sb2.append("\n");
            }
        }

        return new byte [][]{sb1.toString().getBytes(),sb2.toString().getBytes()};
    }

    static StringBuilder getRandomString(int size, Random r){
        StringBuilder sb = new StringBuilder(size);
        for (int i = 0; i < size; i++) {
            sb.append(CHARS[r.nextInt(CHARS.length)]);
        }
        return sb;
    }

    static byte[] createRandomBytes(long fileSize) {
        StringBuilder sb = new StringBuilder((int) fileSize);
        Random r = new Random();
        for (int i = 0; i < fileSize; i++) {
            if (i == 80 || (i > 81 && i % 81 == 0)) {
                sb.append('\n');
            } else {
                sb.append(CHARS[r.nextInt(CHARS.length)]);
            }
        }
        return sb.toString().getBytes();
    }

    /**
     * prevent from overhead on identical files - this works fine
     * <b>only</b> if source and destination are on the same partition (=> the
     * same filesystem). If both files are on different partitions, then
     * 1) the file size could differ because of different cluster size
     * 2) the file time could differ because of different timestamp
     * formats on different file systems (e.g. NTFS and FAT)
     * @param testContent true to test the content of files, false to test only file size/date
     */
    static boolean isSame(File source, File destination, boolean testContent, boolean testLastModified) {
        if (source == null || destination == null || !source.exists()
                || !destination.exists() || source.isDirectory()
                || destination.isDirectory()) {
            return false;
        }
        if (testLastModified && destination.lastModified() != source.lastModified()) {
            return false;
        }
        if (destination.length() == source.length()) {
            if (!testContent) {
                return true;
            }
            FileInputStream fis1 = null;
            FileInputStream fis2 = null;
            try {
                fis1 = new FileInputStream(source);
                fis2 = new FileInputStream(destination);

                byte[] b1 = new byte[(int) source.length()];
                fis1.read(b1, 0, b1.length);
                byte[] b2 = new byte[(int) destination.length()];
                fis2.read(b2, 0, b2.length);

                for (int i = 0; i < b1.length; i++) {
                    if (b1[i] != b2[i]) {
                        return false;
                    }
                }

                // DOES NOT WORK, it throws later FileNotFoundException if one
                // uses the code below to write to the same file later
                // FileOutputStream outputStream = new FileOutputStream(dest, false);
                // even if stream is definitely closed after usage (see finally block)
                //                FileChannel channel1 = fis1.getChannel();
                //                FileChannel channel2 = fis2.getChannel();
                //                MappedByteBuffer buffer1 = channel1.map(
                //                        FileChannel.MapMode.READ_ONLY, 0, channel1.size());
                //                MappedByteBuffer buffer2 = channel2.map(
                //                        FileChannel.MapMode.READ_ONLY, 0, channel2.size());
                //                int result = buffer1.compareTo(buffer2);
                //
                //                return result == 0;
            } catch (Exception e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            } finally {
                if (fis1 != null) {
                    try {
                        fis1.close();
                    } catch (IOException e) {
                        // TODO Auto-generated catch block
                        e.printStackTrace();
                    }
                }
                if (fis2 != null) {
                    try {
                        fis2.close();
                    } catch (IOException e) {
                        // TODO Auto-generated catch block
                        e.printStackTrace();
                    }
                }

            }
            return true;
        }
        return false;
    }

}
