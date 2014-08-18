package test;

import junit.framework.Test;
import junit.framework.TestSuite;

import org.eclipse.ui.IViewPart;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.PlatformUI;

public class AllTests {

	public static Test suite() {
		IWorkbenchPage activePage = PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage();
		activePage.closeAllEditors(false);
		IViewPart view = activePage.findView("org.eclipse.ui.internal.introview");
		if (view != null) {
			activePage.hideView(view);
		}
		TestSuite suite = new TestSuite("Tests for FileSync");
		//$JUnit-BEGIN$
		suite.addTestSuite(TestBuilder.class);
		suite.addTestSuite(TestFS.class);
		//$JUnit-END$
		return suite;
	}

}
