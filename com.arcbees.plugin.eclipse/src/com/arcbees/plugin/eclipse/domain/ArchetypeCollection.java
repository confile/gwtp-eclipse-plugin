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

import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class ArchetypeCollection {

    private String nextPageToken;
    private int total;
    private String kind;
    private String etag;
    private List<Archetype> archetypes;

    public ArchetypeCollection() {
    }

    public String getNextPageToken() {
        return nextPageToken;
    }

    public void setNextPageToken(String nextPageToken) {
        this.nextPageToken = nextPageToken;
    }

    public int getTotal() {
        return total;
    }

    public void setTotal(int total) {
        this.total = total;
    }

    public String getKind() {
        return kind;
    }

    public void setKind(String kind) {
        this.kind = kind;
    }

    public String getEtag() {
        return etag;
    }

    public void setEtag(String etag) {
        this.etag = etag;
    }

    public List<Archetype> getArchetypes() {
        // TODO tmp sort until I get it setup on server
        if (archetypes != null) {
            Collections.sort(archetypes, new Comparator<Archetype>() {
                @Override
                public int compare(Archetype o1, Archetype o2) {
                    List<Category> listLeft = o1.getCategories();
                    List<Category> listRight = o2.getCategories();
                    if (listLeft == null || listRight == null || listLeft.size() == 0 || listRight.size() == 0) {
                        return 0;
                    }
                    
                    Category item1Left = listLeft.get(0);
                    Category item1Right = listRight.get(0);
                    
                    if (item1Left.getName() == null || item1Right == null) {
                        return 0;
                    }
                    
                    // reverse sort gwtp first
                    return item1Left.getName().compareTo(item1Right.getName()) * -1;
                }
            });
        }
        
        return archetypes;
    }

    public void setArchetypes(List<Archetype> archetypes) {
        this.archetypes = archetypes;
    }

}
