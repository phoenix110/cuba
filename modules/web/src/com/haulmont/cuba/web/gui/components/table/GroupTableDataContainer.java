/*
 * Copyright (c) 2008-2018 Haulmont.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.haulmont.cuba.web.gui.components.table;

import com.haulmont.cuba.gui.components.data.BindingState;
import com.haulmont.cuba.gui.components.data.GroupTableItems;
import com.haulmont.cuba.gui.data.GroupInfo;
import com.haulmont.cuba.web.widgets.data.GroupTableContainer;

import java.util.*;

public class GroupTableDataContainer<I> extends SortableDataContainer<I> implements GroupTableContainer {

    protected Set<GroupInfo> expandedGroups = new HashSet<>();

    protected List<Object> cachedItemIds;
    protected Object first;
    protected Object last;

    public GroupTableDataContainer(GroupTableItems<I> tableSource,
                                   TableItemsEventsDelegate<I> dataEventsDelegate) {
        super(tableSource, dataEventsDelegate);
    }

    protected GroupTableItems<I> getGroupTableSource() {
        return (GroupTableItems<I>) tableItems;
    }

    @Override
    public void groupBy(Object[] properties) {
        doGroup(properties);
    }

    protected Set<GroupInfo> saveState() {
        if (expandedGroups.isEmpty()) {
            return Collections.emptySet();
        }

        //save expanding state
        return new HashSet<>(expandedGroups);
    }

    protected void restoreState(Set<GroupInfo> expandState) {
        collapseAll();
        //restore groups expanding
        if (hasGroups()) {
            for (GroupInfo groupInfo : expandState) {
                expand(groupInfo);
            }
        }
    }

    protected void doGroup(Object[] properties) {
        Set<GroupInfo> expandState = saveState();
        getGroupTableSource().groupBy(properties);
        restoreState(expandState);
        resetGroupCachedItems();
    }

    protected void doRefreshGroup() {
        if (hasGroups()) {
            Collection<?> groupProperties = getGroupProperties();
            Object[] groupProps = new Object[groupProperties.size()];

            int i = 0;
            for (Object groupProperty : groupProperties) {
                groupProps[i] = groupProperty;
                i++;
            }

            doGroup(groupProps);
        }
    }

    @Override
    public boolean isGroup(Object id) {
        return id instanceof GroupInfo && getGroupTableSource().containsGroup((GroupInfo) id);
    }

    @Override
    public Collection<?> rootGroups() {
        return getGroupTableSource().rootGroups();
    }

    @Override
    public boolean hasChildren(Object id) {
        return isGroup(id) && getGroupTableSource().hasChildren((GroupInfo) id);
    }

    @Override
    public Collection<?> getChildren(Object id) {
        if (isGroup(id)) {
            return getGroupTableSource().getChildren((GroupInfo) id);
        }
        return Collections.emptyList();
    }

    @Override
    public Object getGroupProperty(Object id) {
        if (isGroup(id)) {
            return getGroupTableSource().getGroupProperty((GroupInfo) id);
        }
        return null;
    }

    @Override
    public Object getGroupPropertyValue(Object id) {
        if (isGroup(id)) {
            return getGroupTableSource().getGroupPropertyValue((GroupInfo) id);
        }
        return null;
    }

    @Override
    public Collection<?> getGroupItemIds(Object id) {
        if (isGroup(id)) {
            return getGroupTableSource().getGroupItemIds((GroupInfo) id);
        }
        return Collections.emptyList();
    }

    @Override
    public int getGroupItemsCount(Object id) {
        if (isGroup(id)) {
            return getGroupTableSource().getGroupItemsCount((GroupInfo) id);
        }
        return 0;
    }

    @Override
    public boolean hasGroups() {
        return getGroupTableSource().hasGroups();
    }

    @Override
    public Collection<?> getGroupProperties() {
        if (hasGroups()) {
            return getGroupTableSource().getGroupProperties();
        }
        return Collections.emptyList();
    }

    @Override
    public void expandAll() {
        if (hasGroups()) {
            expandedGroups.clear();

            expand(rootGroups());
            resetGroupCachedItems();
        }
    }

    protected void expand(Collection groupIds) {
        for (Object groupId : groupIds) {
            expandedGroups.add((GroupInfo) groupId);
            if (hasChildren(groupId)) {
                expand(getChildren(groupId));
            }
        }
    }

    @Override
    public void expand(Object id) {
        if (isGroup(id)) {
            expandedGroups.add((GroupInfo) id);
            resetGroupCachedItems();
        }
    }

    @Override
    public void collapseAll() {
        if (hasGroups()) {
            expandedGroups.clear();
            resetGroupCachedItems();
        }
    }

    @Override
    public void collapse(Object id) {
        if (isGroup(id)) {
            //noinspection RedundantCast
            expandedGroups.remove((GroupInfo) id);
            resetGroupCachedItems();
        }
    }

    @Override
    public boolean isExpanded(Object id) {
        //noinspection RedundantCast
        return isGroup(id) && expandedGroups.contains((GroupInfo) id);
    }

    @Override
    public Collection<?> getItemIds() {
        if (tableItems.getState() == BindingState.INACTIVE) {
            return Collections.emptyList();
        }

        return getCachedItemIds();
    }

    protected List getCachedItemIds() {
        if (cachedItemIds == null) {
            if (getGroupTableSource().hasGroups()) {
                List<Object> result = new ArrayList<>();

                List<GroupInfo> roots = getGroupTableSource().rootGroups();
                if (!roots.isEmpty()) {
                    for (GroupInfo root : roots) {
                        result.add(root);
                        collectItemIds(root, result);
                    }
                }
                cachedItemIds = result;
            } else {
                Collection<?> itemIds = getGroupTableSource().getItemIds();
                cachedItemIds = new ArrayList<>(itemIds);
            }

            if (!cachedItemIds.isEmpty()) {
                first = cachedItemIds.get(0);
                last = cachedItemIds.get(cachedItemIds.size() - 1);
            }
        }
        return cachedItemIds;
    }

    protected void collectItemIds(GroupInfo groupId, List<Object> itemIds) {
        if (expandedGroups.contains(groupId)) {
            GroupTableItems<I> groupTableSource = getGroupTableSource();

            if (groupTableSource.hasChildren(groupId)) {
                List<GroupInfo> children = groupTableSource.getChildren(groupId);
                for (GroupInfo child : children) {
                    itemIds.add(child);
                    collectItemIds(child, itemIds);
                }
            } else {
                itemIds.addAll(groupTableSource.getGroupItemIds(groupId));
            }
        }
    }

    @Override
    protected void resetCachedItems() {
        super.resetCachedItems();

        resetGroupCachedItems();
    }

    protected void resetGroupCachedItems() {
        cachedItemIds = null;
        first = null;
        last = null;
    }

    @Override
    public int size() {
        if (hasGroups()) {
            return getItemIds().size();
        }
        return super.size();
    }

    @Override
    public Object firstItemId() {
        if (hasGroups()) {
            return first;
        }
        return super.firstItemId();
    }

    @Override
    public Object lastItemId() {
        if (hasGroups()) {
            return last;
        }
        return super.lastItemId();
    }

    @Override
    public Object nextItemId(Object itemId) {
        if (hasGroups()) {
            if (itemId == null) {
                return null;
            }
            if (isLastId(itemId)) {
                return null;
            }
            List cachedItemIds = getCachedItemIds();

            int index = cachedItemIds.indexOf(itemId);
            return cachedItemIds.get(index + 1);
        }
        return super.nextItemId(itemId);
    }

    @Override
    public Object prevItemId(Object itemId) {
        if (hasGroups()) {
            if (itemId == null) {
                return null;
            }

            if (isFirstId(itemId)) {
                return null;
            }
            List cachedItemIds = getCachedItemIds();

            int index = cachedItemIds.indexOf(itemId);
            return cachedItemIds.get(index - 1);
        }
        return super.prevItemId(itemId);
    }

    @Override
    public boolean isFirstId(Object itemId) {
        if (hasGroups()) {
            return itemId != null && itemId.equals(first);
        }
        return super.isFirstId(itemId);
    }

    @Override
    public boolean isLastId(Object itemId) {
        if (hasGroups()) {
            return itemId != null && itemId.equals(last);
        }
        return super.isLastId(itemId);
    }

    @Override
    protected void beforeFireItemSetChanged() {
        super.beforeFireItemSetChanged();

        doRefreshGroup();
    }

    @Override
    protected void beforeFireStateChanged() {
        super.beforeFireStateChanged();

        doRefreshGroup();
    }
}