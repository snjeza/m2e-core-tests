
package org.eclipse.m2e.editor.xml;

import java.io.IOException;

import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.m2e.core.project.ResolverConfiguration;
import org.eclipse.m2e.tests.common.AbstractMavenProjectTestCase;


public class WarnIfGroupSameThanParentTest extends AbstractMavenProjectTestCase {

  public void testMNGEclipse2552() throws IOException, CoreException, InterruptedException {
    IProject[] projects = importProjects("projects/MNGECLIPSE-2552", new String[] {
        "child2552withDuplicateGroupAndVersion/pom.xml", 
        "child2552withDuplicateGroup/pom.xml",
        "child2552withDuplicateVersion/pom.xml", 
        "parent2552/pom.xml"}, new ResolverConfiguration());
    waitForJobsToComplete();

    {
      //"child2552withDuplicateGroupAndVersion/pom.xml"
      IMarker[] markers = projects[0].findMember("pom.xml").findMarkers("org.eclipse.m2e.core.maven2ProblemHint", true, IResource.DEPTH_INFINITE);
      assertEquals(2, markers.length);
      assertEquals(IMarker.SEVERITY_WARNING, markers[0].getAttribute(IMarker.SEVERITY));
      assertEquals(org.eclipse.m2e.core.internal.Messages.MavenMarkerManager_duplicate_groupid, markers[0].getAttribute("message"));
      assertEquals(org.eclipse.m2e.core.internal.Messages.MavenMarkerManager_duplicate_version, markers[1].getAttribute("message"));
    }

    {
      //"child2552withDuplicateGroup/pom.xml", 
      IMarker[] markers = projects[1].findMember("pom.xml").findMarkers("org.eclipse.m2e.core.maven2ProblemHint", true, IResource.DEPTH_INFINITE);
      assertEquals(1, markers.length);
      assertEquals(IMarker.SEVERITY_WARNING, markers[0].getAttribute(IMarker.SEVERITY));
      assertEquals(org.eclipse.m2e.core.internal.Messages.MavenMarkerManager_duplicate_groupid, markers[0].getAttribute("message"));
    }
    
    {
      //"child2552withDuplicateVersion/pom.xml"
      IMarker[] markers = projects[2].findMember("pom.xml").findMarkers("org.eclipse.m2e.core.maven2ProblemHint", true, IResource.DEPTH_INFINITE);
      assertEquals(1, markers.length);
      assertEquals(IMarker.SEVERITY_WARNING, markers[0].getAttribute(IMarker.SEVERITY));
      assertEquals(org.eclipse.m2e.core.internal.Messages.MavenMarkerManager_duplicate_version, markers[0].getAttribute("message"));
    }
  }
}