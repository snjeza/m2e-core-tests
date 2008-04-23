/*******************************************************************************
 * Copyright (c) 2008 Sonatype, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package org.maven.ide.eclipse.tests;

import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Set;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.project.MavenProject;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IProjectDescription;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.Path;
import org.maven.ide.eclipse.MavenPlugin;
import org.maven.ide.eclipse.internal.project.MavenProjectManagerImpl;
import org.maven.ide.eclipse.internal.project.MavenUpdateRequest;
import org.maven.ide.eclipse.project.IMavenProjectChangedListener;
import org.maven.ide.eclipse.project.IMavenProjectVisitor;
import org.maven.ide.eclipse.project.MavenProjectChangedEvent;
import org.maven.ide.eclipse.project.MavenProjectFacade;


public class MavenProjectManagerTest extends AsbtractMavenProjectTestCase {
  
  MavenPlugin plugin = MavenPlugin.getDefault();

  MavenProjectManagerImpl manager;
  
  ArrayList events;
  
  IMavenProjectChangedListener listener = new IMavenProjectChangedListener() {
    public void mavenProjectChanged(MavenProjectChangedEvent[] event, IProgressMonitor monitor) {
      events.addAll(Arrays.asList(event));
    }
  };
  
  protected void setUp() throws Exception {
    super.setUp();

    manager = new MavenProjectManagerImpl(plugin.getConsole(), plugin.getIndexManager(), //
        plugin.getMavenEmbedderManager(), runtimeManager);
    
    events = new ArrayList();
    manager.addMavenProjectChangedListener(listener);
  }

  protected void tearDown() throws Exception {
    manager.removeMavenProjectChangedListener(listener);
    listener = null;
    events = null;
    manager = null;

    super.tearDown();
  }

  private IProject createExisting(String name) throws Exception {
    String test = name.substring(0, name.indexOf('-'));
    File dir = new File("resources/" + test, name);
    
    return createExisting(name, dir.getAbsolutePath());
  }
  
  private MavenProjectChangedEvent[] getEvents() {
    return (MavenProjectChangedEvent[]) events.toArray(new MavenProjectChangedEvent[events.size()]);
  }

  private void add(MavenProjectManagerImpl manager, IProject[] projects) throws Exception {
    MavenUpdateRequest updateRequest = new MavenUpdateRequest(false /*offline*/, false /* updateSnapshots*/);
    for (int i = 0; i < projects.length; i++) {
      updateRequest.addPomFile(projects[i].getFile(MavenPlugin.POM_FILE_NAME));
    }
    
    manager.refresh(Collections.singleton(updateRequest), monitor);
//    manager.notifyListeners(monitor);
  }

  private void remove(MavenProjectManagerImpl manager, IProject project) throws Exception {
    MavenUpdateRequest updateRequest = new MavenUpdateRequest(true /*offline*/, false /* updateSnapshots*/);

    updateRequest.addPomFiles(manager.remove(project.getFile(MavenPlugin.POM_FILE_NAME)));
    manager.refresh(Collections.singleton(updateRequest), monitor);
//    manager.notifyListeners(monitor);
  }

  public void test000_simple() throws Exception {
    IProject p1 = createExisting("t000-p1");
    waitForJobsToComplete();
    
    add(manager, new IProject[] {p1});

    MavenProjectFacade f1 = manager.create(p1, monitor);
    assertEquals(p1.getFullPath(), f1.getFullPath());

    assertEquals("t000", f1.getMavenProject().getGroupId());
    assertEquals("t000-p1", f1.getMavenProject().getArtifactId());
    assertEquals("0.0.1-SNAPSHOT", f1.getMavenProject().getVersion());
  }

  public void test000_pom_simple() throws Exception {
    IProject p1 = createExisting("t000-p1");
    waitForJobsToComplete();

    add(manager, new IProject[] {p1});

    IFile pom = p1.getFile(MavenPlugin.POM_FILE_NAME);

    MavenProjectFacade f1 = manager.create(pom, false, null);
    assertEquals(p1.getFullPath(), f1.getFullPath());

    assertEquals("t000", f1.getMavenProject().getGroupId());
    assertEquals("t000-p1", f1.getMavenProject().getArtifactId());
    assertEquals("0.0.1-SNAPSHOT", f1.getMavenProject().getVersion());
  }

  public void test000_removeClosed() throws Exception {
    IProject p1 = createExisting("t000-p1");
    waitForJobsToComplete();

    add(manager, new IProject[] {p1});

    MavenProjectFacade f1 = manager.create(p1, monitor);

    assertEquals(1, events.size());
    MavenProjectChangedEvent event = (MavenProjectChangedEvent) events.get(0);
    assertEquals(MavenProjectChangedEvent.KIND_ADDED, event.getKind());
    assertNull(event.getOldMavenProject());
    assertSame(f1, event.getMavenProject());

    assertEquals(p1.getFullPath(), f1.getFullPath());

    p1.close(monitor);

    events.clear();
    remove(manager, p1);

    assertNull(manager.create(p1, monitor));

    assertEquals(1, events.size());
    event = (MavenProjectChangedEvent) events.get(0);
    assertEquals(MavenProjectChangedEvent.KIND_REMOVED, event.getKind());
    assertSame(f1, event.getOldMavenProject());
    assertNull(event.getMavenProject());
  }

  public void test000_removeDeleted() throws Exception {
    IProject p1 = createExisting("t000-p1");
    waitForJobsToComplete();

    add(manager, new IProject[] {p1});

    MavenProjectFacade f1 = manager.create(p1, monitor);
    assertEquals(p1.getFullPath(), f1.getFullPath());

    p1.delete(false, true, monitor);

    remove(manager, p1);

    assertNull(manager.create(p1, monitor));
  }

  public void test000_deletePom() throws Exception {
    IProject p1 = createExisting("t000-p1");
    waitForJobsToComplete();

    add(manager, new IProject[] {p1});

    assertNotNull(manager.create(p1, monitor));

    p1.getFile(MavenPlugin.POM_FILE_NAME).delete(true, monitor);
    waitForJobsToComplete();

    add(manager, new IProject[] {p1});

    assertNull(manager.create(p1, monitor));
  }

  public void test000_noChangeReload() throws Exception {
    IProject p1 = createExisting("t000-p1");
    waitForJobsToComplete();

    add(manager, new IProject[] {p1});
    
    MavenProjectFacade oldFacade = manager.create(p1, monitor);

    assertEquals(1, events.size());
    MavenProjectChangedEvent event = (MavenProjectChangedEvent) events.get(0);
    assertEquals(p1.getFile(MavenPlugin.POM_FILE_NAME), event.getSource());
    assertEquals(MavenProjectChangedEvent.KIND_ADDED, event.getKind());

    events.clear();

    add(manager, new IProject[] {p1});

    MavenProjectFacade newFacade = manager.create(p1, monitor);
    assertNotSame(oldFacade.getMavenProject(), newFacade.getMavenProject());

    assertEquals(1, events.size());
    event = (MavenProjectChangedEvent) events.get(0);
    assertEquals(p1.getFile(MavenPlugin.POM_FILE_NAME), event.getSource());
    assertEquals(MavenProjectChangedEvent.KIND_CHANGED, event.getKind());
    assertEquals(MavenProjectChangedEvent.FLAG_NONE, event.getFlags());
    assertNotNull(event.getOldMavenProject());
    assertNotNull(event.getMavenProject());
  }

  public void test001_missingParent() throws Exception {
    IProject p2 = createExisting("t001-p2");
    IProject p3 = createExisting("t001-p3");
    waitForJobsToComplete();

    add(manager, new IProject[] {p2});
    assertNull(manager.create(p2, monitor));

    IMarker[] markers = p2.findMarkers(null, true, IResource.DEPTH_INFINITE);
    assertEquals(toString(markers), 1, markers.length);

    add(manager, new IProject[] {p3});
    assertNotNull(manager.create(p2, monitor));

    remove(manager, p3);
    assertNull(manager.create(p2, monitor));
  }

  public void test001_dependencyOnModuleWithInheritedGroupAndVersion() throws Exception {
    IProject p1 = createExisting("t001-p1");
    IProject p2 = createExisting("t001-p2");
    IProject p3 = createExisting("t001-p3");
    waitForJobsToComplete();

    add(manager, new IProject[] {p1, p2, p3});

    MavenProjectFacade f1 = manager.create(p1, monitor);
    Set artifacts = f1.getMavenProject().getArtifacts();
    assertEquals(1, artifacts.size());
    Artifact a1 = (Artifact) artifacts.iterator().next();
    assertEquals(true, a1.isResolved());
    assertEquals(p2.getFile(MavenPlugin.POM_FILE_NAME).getLocation().toFile(), a1.getFile());

    MavenProjectFacade f2 = manager.create(p2, monitor);
    assertNotNull(f2);

    MavenProjectFacade f3 = manager.create(p3, monitor);
    assertNotNull(f3);
  }

  public void _test003_projectsWithDuplicateGroupArtifactVersion() throws Exception {
    fail("Implement me");
  }

  public void test005_dependencyAvailableFromLocalRepoAndWorkspace() throws Exception {
    IProject p1 = createExisting("t005-p1");
    IProject p2 = createExisting("t005-p2");
    waitForJobsToComplete();

    add(manager, new IProject[] {p1, p2});

    MavenProjectFacade f1 = manager.create(p1, monitor);
    Set artifacts = f1.getMavenProject().getArtifacts();
    assertEquals(1, artifacts.size());
    Artifact a1 = (Artifact) artifacts.iterator().next();
    assertEquals(true, a1.isResolved());
    assertEquals(p2.getFile(MavenPlugin.POM_FILE_NAME).getLocation().toFile(), a1.getFile());

    p2.delete(false, true, monitor);

    remove(manager, p2);

    f1 = manager.create(p1, monitor);
    artifacts = f1.getMavenProject().getArtifacts();
    assertEquals(1, artifacts.size());
    a1 = (Artifact) artifacts.iterator().next();
    assertEquals(true, a1.isResolved());
    assertTrue(a1.getFile().getAbsolutePath().startsWith(repo.getAbsolutePath()));
  }

  public void test005_snapshotAvailableFromLocalRepoAndWorkspace() throws Exception {
    IProject p1 = createExisting("t005-p3");
    waitForJobsToComplete();
    add(manager, new IProject[] {p1});

    MavenProjectFacade f1 = manager.create(p1, monitor);
    Set artifacts = f1.getMavenProject().getArtifacts();
    assertEquals(1, artifacts.size());
    Artifact a1 = (Artifact) artifacts.iterator().next();
    assertEquals(true, a1.isResolved());
    assertTrue(a1.getFile().getAbsolutePath().startsWith(repo.getAbsolutePath()));

    IProject p2 = createExisting("t005-p4");
    waitForJobsToComplete();
    add(manager, new IProject[] {p2});

    f1 = manager.create(p1, monitor);
    artifacts = f1.getMavenProject().getArtifacts();
    assertEquals(1, artifacts.size());
    a1 = (Artifact) artifacts.iterator().next();
    assertEquals(true, a1.isResolved());
    assertEquals(p2.getFile(MavenPlugin.POM_FILE_NAME).getLocation().toFile(), a1.getFile());
  }

  public void test006_parentAvailableFromLocalRepoAndWorkspace() throws Exception {
    IProject p1 = createExisting("t006-p1");
    IProject p2 = createExisting("t006-p2");
    waitForJobsToComplete();

    add(manager, new IProject[] {p1, p2});

    MavenProjectFacade f1 = manager.create(p1, monitor);
    assertEquals(p2.getFile(MavenPlugin.POM_FILE_NAME).getLocation().toFile(), f1.getMavenProject().getParent().getFile());

    p2.delete(false, true, monitor);

    remove(manager, p2);

    f1 = manager.create(p1, monitor);
    assertTrue(f1.getMavenProject().getParent().getFile().getAbsolutePath().startsWith(repo.getAbsolutePath()));
  }

  public void test007_staleDependencies() throws Exception {
    IProject p1 = createExisting("t007-p1");
    IProject p2 = createExisting("t007-p2");
    waitForJobsToComplete();

    // p1 depends on p2
    add(manager, new IProject[] {p1, p2});

    MavenProjectFacade f1 = manager.create(p1, monitor);
    assertEquals(1, f1.getMavenProject().getArtifacts().size());

    InputStream contents = p1.getFile("pom_updated.xml").getContents();
    p1.getFile("pom.xml").setContents(contents, IResource.FORCE, monitor);
    contents.close();

    // update p1 to remove dependency on p2
    add(manager, new IProject[] {p1});

    f1 = manager.create(p1, monitor);
    assertEquals(0, f1.getMavenProject().getArtifacts().size());

    events.clear();

    // remove p2
    remove(manager, p2);

    assertEquals(1, events.size());
    MavenProjectChangedEvent event = (MavenProjectChangedEvent) events.get(0);
    assertEquals(p2.getFile(MavenPlugin.POM_FILE_NAME), event.getSource());
    assertEquals(MavenProjectChangedEvent.KIND_REMOVED, event.getKind());
  }

  public void test008_staleMissingParent() throws Exception {
    IProject p1 = createExisting("t008-p1");
    IProject p2 = createExisting("t008-p2");
    IProject p3 = createExisting("t008-p3");
    waitForJobsToComplete();

    // p1 does not have parent
    add(manager, new IProject[] {p1, p3});

    MavenProjectFacade f1 = manager.create(p1, monitor);
    assertNull(f1); // XXX should I return non-null facade that does not have MavenProject?

    // update p1 to have p3 parent
    InputStream contents = p1.getFile("pom_updated.xml").getContents();
    p1.getFile("pom.xml").setContents(contents, IResource.FORCE, monitor);
    contents.close();
    add(manager, new IProject[] {p1});

    f1 = manager.create(p1, monitor);
    assertEquals("t008-p3", f1.getMavenProject().getParent().getArtifactId());

    events.clear();
    add(manager, new IProject[] {p2});

    assertEquals(1, events.size());
    MavenProjectChangedEvent event = (MavenProjectChangedEvent) events.get(0);
    assertEquals(p2.getFile(MavenPlugin.POM_FILE_NAME), event.getSource());
    assertEquals(MavenProjectChangedEvent.KIND_ADDED, event.getKind());
  }

  public void test009_noworkspaceResolution() throws Exception {
    IProject p1 = createExisting("t009-p1");
    IProject p2 = createExisting("t009-p2");
    IProject p3 = createExisting("t009-p3");
    waitForJobsToComplete();

    add(manager, new IProject[] {p1, p2, p3});

    MavenProject f1 = manager.create(p1, monitor).getMavenProject();
    MavenProject f2 = manager.create(p2, monitor).getMavenProject();

    assertEquals(2, f1.getArtifacts().size());
    Artifact[] a1 = (Artifact[]) f1.getArtifacts().toArray(new Artifact[f1.getArtifacts().size()]);
    assertTrue(a1[0].getFile().getAbsolutePath().startsWith(workspace.getRoot().getLocation().toFile().getAbsolutePath()));
    assertEquals(p2.getFile("pom.xml").getLocation().toFile(), a1[1].getFile());

    assertEquals(1, f2.getArtifacts().size());
    Artifact a2 = (Artifact) f2.getArtifacts().iterator().next();
    assertTrue(a2.getFile().getAbsolutePath().startsWith(repo.getAbsolutePath()));

    this.events.clear();
    remove(manager, p3);

    f1 = manager.create(p1, monitor).getMavenProject();
    f2 = manager.create(p2, monitor).getMavenProject();
    assertNull(manager.create(p3, monitor));

    assertEquals(2, f1.getArtifacts().size());
    a1 = (Artifact[]) f1.getArtifacts().toArray(new Artifact[f1.getArtifacts().size()]);
    assertTrue(a1[0].getFile().getAbsolutePath().startsWith(repo.getAbsolutePath()));
    assertEquals(p2.getFile("pom.xml").getLocation().toFile(), a1[1].getFile());

    assertEquals(1, f2.getArtifacts().size());
    a2 = (Artifact) f1.getArtifacts().iterator().next();
    assertTrue(a2.getFile().getAbsolutePath().startsWith(repo.getAbsolutePath()));

    MavenProjectChangedEvent[] events = getEvents();
    assertEquals(2, events.length);
    assertEquals(p3.getFile(MavenPlugin.POM_FILE_NAME), events[0].getSource());
    assertEquals(p1.getFile(MavenPlugin.POM_FILE_NAME), events[1].getSource());

    this.events.clear();
    remove(manager, p2);

    f1 = manager.create(p1, monitor).getMavenProject();
    assertNull(manager.create(p2, monitor));
    assertNull(manager.create(p3, monitor));

    a1 = (Artifact[]) f1.getArtifacts().toArray(new Artifact[f1.getArtifacts().size()]);
    assertTrue(a1[0].getFile().getAbsolutePath().startsWith(repo.getAbsolutePath()));
    assertEquals(false, a1[1].isResolved());

    events = getEvents();
    assertEquals(2, events.length);
    assertEquals(p2.getFile(MavenPlugin.POM_FILE_NAME), events[0].getSource());
    assertEquals(p1.getFile(MavenPlugin.POM_FILE_NAME), events[1].getSource());
  }

  public void test010_projectWithNestedModules() throws Exception {
    IProject p1 = createExisting("t010-p1");
    IProject p2 = createExisting("t010-p2");
    IProject p3 = createExisting("t010-p3");
    createExisting("t010-p4");
    waitForJobsToComplete();

    add(manager, new IProject[] {p1, p2, p3});

    MavenProject f1 = manager.create(p1, monitor).getMavenProject();
//    MavenProject f2 = manager.create(p2).getMavenProject();

    Artifact[] a1 = (Artifact[]) f1.getArtifacts().toArray(new Artifact[f1.getArtifacts().size()]);
    assertEquals(2, a1.length);
    assertEquals(p2.getFile(new Path("t010-p2-m1/pom.xml")).getLocation().toFile(), a1[0].getFile());
    assertEquals(p3.getFile("pom.xml").getLocation().toFile(), a1[1].getFile());

//    Artifact[] a2 = (Artifact[]) f2.getArtifacts().toArray(new Artifact[f2.getArtifacts().size()]);
//    assertEquals(1, a2.length);
//    assertEquals(p3.getFile("t010-p3/pom.xml").getLocation().toFile(), a2[0].getFile());
  }

  public void ___test40projects() throws Exception {
    File[] dirs = new File("S:/tmp/40projects").listFiles();
    IProject[] projects = new IProject[dirs.length];
    for (int d = 0; d < dirs.length; d++) {
      String name = dirs[d].getName();
      File dir = new File(workspace.getRoot().getLocation().toFile(), name);
      copyDir(dirs[d], dir);

      IProject cptest = workspace.getRoot().getProject(name);
      if (!cptest.exists()) {
        IProjectDescription projectDescription = workspace.newProjectDescription(cptest.getName());
        projectDescription.setLocation(null); 
        cptest.create(projectDescription, monitor);
        cptest.open(IResource.NONE, monitor);
      } else {
        cptest.refreshLocal(IResource.DEPTH_INFINITE, monitor);
      }
      projects[dirs.length - d - 1] = cptest;
    }

    add(manager, projects);
  }

  public void test011_interModuleDependencies() throws Exception {
    IProject p1 = createExisting("t011-p1");
    waitForJobsToComplete();

    File m1 = p1.getFile("t011-p1-m1/pom.xml").getLocation().toFile().getAbsoluteFile();
    File m2 = p1.getFile("t011-p1-m2/pom.xml").getLocation().toFile().getAbsoluteFile();

    add(manager, new IProject[] {p1});

    MavenProjectFacade f1 = manager.create(p1, monitor);
    MavenProject[] mavenProjects = getAllMavenProjects(f1);
    assertEquals(3, mavenProjects.length);

    assertEquals(m1, mavenProjects[1].getFile().getAbsoluteFile());
//    XXX look why mavenProjects[1].getArtifact().getFile() == null
//    assertEquals(m1, mavenProjects[1].getArtifact().getFile().getAbsoluteFile());

    Artifact[] a1 = (Artifact[]) mavenProjects[1].getArtifacts().toArray(new Artifact[mavenProjects[1].getArtifacts().size()]);
    assertEquals(1, a1.length);
    assertEquals(m2, a1[0].getFile().getAbsoluteFile());

    assertEquals(m2, mavenProjects[2].getFile().getAbsoluteFile());
//    assertEquals(m2, mavenProjects[2].getArtifact().getFile().getAbsoluteFile());

    assertEquals(0, f1.getTestArtifacts().size());

    IMarker[] markers = p1.findMarkers(null, true, IResource.DEPTH_INFINITE);
    assertEquals(toString(markers), 0, markers.length);
  }

  private MavenProject[] getAllMavenProjects(MavenProjectFacade facade) throws CoreException {
    final ArrayList projects = new ArrayList();
    facade.accept(new IMavenProjectVisitor() {
      public boolean visit(MavenProjectFacade mavenProject) {
        projects.add(mavenProject.getMavenProject());
        return true;
      }
      public void visit(MavenProjectFacade mavenProject, Artifact artifact) {
      }
    }, IMavenProjectVisitor.NESTED_MODULES);
    return (MavenProject[]) projects.toArray(new MavenProject[projects.size()]);
  }

  public void test012_downloadExternalDependency() throws Exception {
    IProject p1 = createExisting("t012-p1");
    waitForJobsToComplete();

    File jar = new File(repo + "/log4j/log4j/1.2.13", "log4j-1.2.13.jar").getAbsoluteFile();
//    assertTrue(jar.exists());

    jar.delete();

    add(manager, new IProject[] {p1});

    MavenProjectFacade f1 = manager.create(p1, monitor);
    MavenProject[] mavenProjects = getAllMavenProjects(f1);
    assertEquals(1, mavenProjects.length);

    Artifact[] a1 = (Artifact[]) mavenProjects[0].getArtifacts().toArray(new Artifact[mavenProjects[0].getArtifacts().size()]);
    assertEquals(1, a1.length);
    assertEquals(jar, a1[0].getFile());
    assertTrue(jar.exists());
  }

  public void test013_cantParsePomMarker() throws Exception {
    IProject p1 = createExisting("t013-p1");
    waitForJobsToComplete();

    add(manager, new IProject[] {p1});
    assertNull(manager.create(p1, monitor));
    IMarker[] markers = p1.findMarkers(null, true, IResource.DEPTH_INFINITE);
    assertEquals(toString(markers), 1, markers.length);

    copyContent(p1, "pom_good.xml", "pom.xml");
    add(manager, new IProject[] {p1});
    markers = p1.findMarkers(null, true, IResource.DEPTH_INFINITE);
    assertEquals(toString(markers), 0, markers.length);
    assertNotNull(manager.create(p1, monitor));
  }

  public void test013_missingDependencyMarker() throws Exception {
    IProject p2 = createExisting("t013-p2");
    waitForJobsToComplete();

    add(manager, new IProject[] {p2});
    IMarker[] markers = p2.findMarkers(null, true, IResource.DEPTH_INFINITE);
    assertEquals(toString(markers), 3, markers.length); // two come from java builder
  }

  public void __test014_resolveDependencies() throws Exception {
    IProject p1 = createExisting("t014-p1");
    waitForJobsToComplete();

    copyContent(p1, "pom.xml", "pom_original.xml");

    add(manager, new IProject[] {p1});

    MavenProjectFacade f1 = manager.create(p1, monitor);
    MavenProject[] mavenProjects = getAllMavenProjects(f1);
    assertEquals(1, mavenProjects.length);

    Artifact[] a1 = (Artifact[]) mavenProjects[0].getArtifacts().toArray(new Artifact[mavenProjects[0].getArtifacts().size()]);
    assertEquals("junit-4.0.jar", a1[0].getFile().getName());
    assertEquals(true, a1[0].isResolved());

    copyContent(p1, "pom_changed.xml", "pom.xml");
    add(manager, new IProject[] {p1});
    f1 = manager.create(p1, monitor);
    mavenProjects = getAllMavenProjects(f1);
    a1 = (Artifact[]) mavenProjects[0].getArtifacts().toArray(new Artifact[mavenProjects[0].getArtifacts().size()]);
    assertEquals(1, a1.length);
    assertEquals("junit-4.0.jar", a1[0].getFile().getName());

    copyContent(p1, "pom_original.xml", "pom.xml");
    add(manager, new IProject[] {p1});
    f1 = manager.create(p1, monitor);
    mavenProjects = getAllMavenProjects(f1);
    a1 = (Artifact[]) mavenProjects[0].getArtifacts().toArray(new Artifact[mavenProjects[0].getArtifacts().size()]);
    assertEquals("junit-4.0.jar", a1[0].getFile().getName());
  }

  public void test015_refreshOffline() throws Exception {
    IProject p1 = createExisting("t015-p1");
    waitForJobsToComplete();

    File file = new File(repo, "junit/junit/3.8.1/junit-3.8.1.jar");
    assertTrue(file.delete());

    MavenUpdateRequest updateRequest = new MavenUpdateRequest(true /*offline*/, false /* updateSources */);
    updateRequest.addPomFile(p1);
    manager.refresh(Collections.singleton(updateRequest), monitor);
//    manager.notifyListeners(monitor);

    assertEquals(false, file.exists());
    MavenProjectFacade f1 = manager.create(p1, monitor);
    MavenProject[] mavenProjects = getAllMavenProjects(f1);
    Artifact[] a1 = (Artifact[]) mavenProjects[0].getArtifacts().toArray(new Artifact[mavenProjects[0].getArtifacts().size()]);
    assertEquals(false, a1[0].isResolved());

    add(manager, new IProject[] {p1});

    assertEquals(true, file.exists());
    f1 = manager.create(p1, monitor);
    mavenProjects = getAllMavenProjects(f1);
    a1 = (Artifact[]) mavenProjects[0].getArtifacts().toArray(new Artifact[mavenProjects[0].getArtifacts().size()]);
    assertEquals(true, a1[0].isResolved());
  }

  public void test016_removeModule() throws Exception {
    IProject p1 = createExisting("t016-p1");
    waitForJobsToComplete();

    IFile pom = p1.getFile("t016-p1-m1/pom.xml");

    add(manager, new IProject[] {p1});

    assertNotNull(manager.create(pom, false, null));

    copyContent(p1, "pom_changed.xml", "pom.xml");

    add(manager, new IProject[] {p1});

    assertNull(manager.create(pom, false, null));
  }

  public void test017_nestedModuleRefresh() throws Exception {
    IProject p1 = createExisting("t017-p1");
    IProject p4 = createExisting("t017-p4");
    waitForJobsToComplete();

    add(manager, new IProject[] {p1, p4});

    IFile pom = p1.getFile("t017-p1-m1/pom.xml");

    MavenProject mavenProject = manager.create(pom, false, null).getMavenProject();
    Artifact a = (Artifact) mavenProject.getArtifacts().iterator().next();
    assertTrue(a.isResolved());
    assertEquals(p4.getFile(MavenPlugin.POM_FILE_NAME).getLocation().toFile().getAbsoluteFile(), 
        a.getFile().getAbsoluteFile());

    copyContent(p1, "pom_changed.xml", "pom.xml");

    add(manager, new IProject[] {p1});

    mavenProject = manager.create(pom, false, null).getMavenProject();
    a = (Artifact) mavenProject.getArtifacts().iterator().next();
    assertFalse(a.isResolved());
  }

  public void test017_moduleRefresh() throws Exception {
    IProject p2 = createExisting("t017-p2");
    IProject p3 = createExisting("t017-p3");
    IProject p4 = createExisting("t017-p4");
    waitForJobsToComplete();

    add(manager, new IProject[] {p2, p3, p4});

    IFile pom = p3.getFile("pom.xml");

    MavenProject mavenProject = manager.create(pom, false, null).getMavenProject();
    Artifact a = (Artifact) mavenProject.getArtifacts().iterator().next();
    assertTrue(a.isResolved());
    assertEquals(p4.getFile(MavenPlugin.POM_FILE_NAME).getLocation().toFile().getAbsoluteFile(), 
        a.getFile().getAbsoluteFile());

    copyContent(p2, "pom_changed.xml", "pom.xml");

    add(manager, new IProject[] {p2});

    mavenProject = manager.create(pom, false, null).getMavenProject();
    a = (Artifact) mavenProject.getArtifacts().iterator().next();
    assertFalse(a.isResolved());
  }
}
