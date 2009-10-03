package test;

import junit.framework.Test;
import junit.framework.TestSuite;

public class AllTests {

    public static Test suite() {
        TestSuite suite = new TestSuite("Tests for FileSync");
        //$JUnit-BEGIN$
        suite.addTestSuite(TestBuilder.class);
        suite.addTestSuite(TestFS.class);
        //$JUnit-END$
        return suite;
    }

}
