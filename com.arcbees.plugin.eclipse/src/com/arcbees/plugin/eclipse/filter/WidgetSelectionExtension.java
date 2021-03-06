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

package com.arcbees.plugin.eclipse.filter;

import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.ITypeHierarchy;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.ui.dialogs.ITypeInfoFilterExtension;
import org.eclipse.jdt.ui.dialogs.ITypeInfoRequestor;
import org.eclipse.jdt.ui.dialogs.TypeSelectionExtension;

import com.arcbees.plugin.eclipse.domain.PresenterConfigModel;

public class WidgetSelectionExtension extends TypeSelectionExtension {
    private PresenterConfigModel presenterConfigModel;

    public WidgetSelectionExtension(PresenterConfigModel presenterConfigModel) {
        this.presenterConfigModel = presenterConfigModel;
    }

    @Override
    public ITypeInfoFilterExtension getFilterExtension() {
        ITypeInfoFilterExtension extension = new ITypeInfoFilterExtension() {
            @Override
            public boolean select(ITypeInfoRequestor requestor) {
                try {
                    return canSelect(requestor);
                } catch (JavaModelException e) {
                    return false;
                }
            }
        };
        return extension;
    }

    private boolean canSelect(ITypeInfoRequestor requestor) throws JavaModelException {
        IType type = presenterConfigModel.getJavaProject().findType(
                requestor.getPackageName() + "." + requestor.getTypeName());
        if (type == null || !type.exists()) {
            return false;
        }

        ITypeHierarchy hierarchy = type.newSupertypeHierarchy(new NullProgressMonitor());
        IType[] types = hierarchy.getAllTypes();
        for (IType t : types) {
            // TODO test if this works
            if (t.getFullyQualifiedName('.').equals("com.google.gwt.user.client.ui.Widget")) {
                return true;
            }
        }

        return false;
    }
}
