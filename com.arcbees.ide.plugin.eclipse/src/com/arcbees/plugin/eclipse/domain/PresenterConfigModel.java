/**
 * Copyright 2013 ArcBees Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package com.arcbees.plugin.eclipse.domain;

import org.eclipse.core.resources.IProject;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;


public class PresenterConfigModel extends ModelObject {
    private IProject project;    
    private String name;

    public PresenterConfigModel() {
    }

    public String getProjectName() {
        return name;
    }

    public void setName(String name) {
        firePropertyChange("name", this.name, this.name = name);
    }

    
    @Override
    public String toString() {
        String s = "{ PresenterConfigModel: ";
        s += "name=" + name + " ";
        s += " }"; 
        return s;
    }

    public void setProject(IProject project) {
        this.project = project;
    }

    public IProject getProject() {
        return project;
    }
    
    public IJavaProject getJavaProject() {
        IJavaProject javaProject = JavaCore.create(project);
        return javaProject;
    }
}
