/*
 * Copyright (c) 2008-2016 Haulmont.
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

package com.haulmont.cuba.gui.xml.layout.loaders;

import com.google.common.base.Splitter;
import com.haulmont.chile.core.model.MetaClass;
import com.haulmont.chile.core.model.MetaProperty;
import com.haulmont.chile.core.model.MetaPropertyPath;
import com.haulmont.chile.core.model.MetadataObject;
import com.haulmont.cuba.core.app.dynamicattributes.DynamicAttributesUtils;
import com.haulmont.cuba.core.entity.CategoryAttribute;
import com.haulmont.cuba.core.entity.LocaleHelper;
import com.haulmont.cuba.core.global.MetadataTools;
import com.haulmont.cuba.core.global.View;
import com.haulmont.cuba.core.global.ViewProperty;
import com.haulmont.cuba.core.global.ViewRepository;
import com.haulmont.cuba.gui.GuiDevelopmentException;
import com.haulmont.cuba.gui.components.*;
import com.haulmont.cuba.gui.components.DataGrid.Column;
import com.haulmont.cuba.gui.components.data.DataGridItems;
import com.haulmont.cuba.gui.components.data.datagrid.ContainerDataGridItems;
import com.haulmont.cuba.gui.data.CollectionDatasource;
import com.haulmont.cuba.gui.data.Datasource;
import com.haulmont.cuba.gui.dynamicattributes.DynamicAttributesGuiTools;
import com.haulmont.cuba.gui.model.*;
import com.haulmont.cuba.gui.screen.FrameOwner;
import com.haulmont.cuba.gui.screen.UiControllerUtils;
import com.haulmont.cuba.gui.xml.layout.ComponentLoader;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.IterableUtils;
import org.apache.commons.lang3.StringUtils;
import org.dom4j.DocumentFactory;
import org.dom4j.Element;
import org.dom4j.datatype.DatatypeElementFactory;

import javax.annotation.Nullable;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public abstract class AbstractDataGridLoader<T extends DataGrid> extends ActionsHolderLoader<T> {

    protected ComponentLoader buttonsPanelLoader;
    protected Element panelElement;

    @Override
    public void createComponent() {
        resultComponent = createComponentInternal();
        loadId(resultComponent, element);
        createButtonsPanel(resultComponent, element);
    }

    protected abstract T createComponentInternal();

    protected void createButtonsPanel(HasButtonsPanel dataGrid, Element element) {
        panelElement = element.element("buttonsPanel");
        if (panelElement != null) {
            ButtonsPanelLoader loader = (ButtonsPanelLoader) getLoader(panelElement, ButtonsPanel.NAME);
            loader.createComponent();
            ButtonsPanel panel = loader.getResultComponent();

            dataGrid.setButtonsPanel(panel);

            buttonsPanelLoader = loader;
        }
    }

    @Override
    public void loadComponent() {
        assignXmlDescriptor(resultComponent, element);
        assignFrame(resultComponent);

        loadEnable(resultComponent, element);
        loadVisible(resultComponent, element);
        loadSettingsEnabled(resultComponent, element);

        loadAlign(resultComponent, element);
        loadStyleName(resultComponent, element);

        loadHeight(resultComponent, element);
        loadWidth(resultComponent, element);

        loadIcon(resultComponent, element);
        loadCaption(resultComponent, element);
        loadDescription(resultComponent, element);
        loadContextHelp(resultComponent, element);

        loadEditorEnabled(resultComponent, element);
        loadEditorBuffered(resultComponent, element);
        loadEditorSaveCaption(resultComponent, element);
        loadEditorCancelCaption(resultComponent, element);

        loadActions(resultComponent, element);

        loadContextMenuEnabled(resultComponent, element);
        loadColumnsHidingAllowed(resultComponent, element);
        loadColumnResizeMode(resultComponent, element);
        loadSortable(resultComponent, element);
        loadResponsive(resultComponent, element);
        loadCss(resultComponent, element);
        loadReorderingAllowed(resultComponent, element);
        loadHeaderVisible(resultComponent, element);
        loadFooterVisible(resultComponent, element);
        loadTextSelectionEnabled(resultComponent, element);
        loadBodyRowHeight(resultComponent, element);
        loadHeaderRowHeight(resultComponent, element);
        loadFooterRowHeight(resultComponent, element);

        Element columnsElement = element.element("columns");

        loadButtonsPanel(resultComponent);

        loadRowsCount(resultComponent, element); // must be before datasource setting

        MetaClass metaClass;
        CollectionContainer collectionContainer = null;
        DataLoader dataLoader = null;
        Datasource datasource = null;

        String containerId = element.attributeValue("dataContainer");
        if (containerId != null) {
            FrameOwner frameOwner = context.getFrame().getFrameOwner();
            ScreenData screenData = UiControllerUtils.getScreenData(frameOwner);
            InstanceContainer container = screenData.getContainer(containerId);
            if (container instanceof CollectionContainer) {
                collectionContainer = (CollectionContainer) container;
            } else {
                throw new GuiDevelopmentException("Not a CollectionContainer: " + containerId, context.getCurrentFrameId());
            }
            metaClass = collectionContainer.getEntityMetaClass();
            if (collectionContainer instanceof HasLoader) {
                dataLoader = ((HasLoader) collectionContainer).getLoader();
            }

        } else {
            String datasourceId = element.attributeValue("datasource");
            if (StringUtils.isBlank(datasourceId)) {
                throw new GuiDevelopmentException("DataGrid element doesn't have 'datasource' attribute",
                        context.getCurrentFrameId(), "DataGrid ID", element.attributeValue("id"));
            }
            datasource = context.getDsContext().get(datasourceId);
            if (datasource == null) {
                throw new GuiDevelopmentException("Can't find datasource by name: " + datasourceId, context.getCurrentFrameId());
            }
            if (!(datasource instanceof CollectionDatasource)) {
                throw new GuiDevelopmentException("Not a CollectionDatasource: " + datasource, context.getCurrentFrameId());
            }
            metaClass = datasource.getMetaClass();
        }

        List<Column> availableColumns;
        if (columnsElement != null) {
            View view = collectionContainer != null ? collectionContainer.getView() : datasource.getView();
            availableColumns = loadColumns(resultComponent, columnsElement, metaClass, view);
        } else {
            availableColumns = new ArrayList<>();
        }

        if (collectionContainer != null) {
            if (dataLoader instanceof CollectionLoader) {
                addDynamicAttributes(resultComponent, metaClass, null, (CollectionLoader) dataLoader, availableColumns);
            }
            //noinspection unchecked
            resultComponent.setItems(createContainerDataGridSource(collectionContainer));
        } else {
            addDynamicAttributes(resultComponent, metaClass, datasource, null, availableColumns);
            resultComponent.setDatasource((CollectionDatasource) datasource);
        }

        loadSelectionMode(resultComponent, element);
        loadFrozenColumnCount(resultComponent, element);
        loadTabIndex(resultComponent, element);
    }

    @SuppressWarnings("unchecked")
    protected DataGridItems createContainerDataGridSource(CollectionContainer container) {
        return new ContainerDataGridItems(container);
    }

    protected void loadEditorEnabled(DataGrid component, Element element) {
        String editorEnabled = element.attributeValue("editorEnabled");
        if (StringUtils.isNotEmpty(editorEnabled)) {
            component.setEditorEnabled(Boolean.parseBoolean(editorEnabled));
        }
    }

    protected void loadEditorBuffered(DataGrid component, Element element) {
        String editorBuffered = element.attributeValue("editorBuffered");
        if (StringUtils.isNotEmpty(editorBuffered)) {
            component.setEditorBuffered(Boolean.parseBoolean(editorBuffered));
        }
    }

    protected void loadEditorSaveCaption(DataGrid component, Element element) {
        String editorSaveCaption = element.attributeValue("editorSaveCaption");
        if (StringUtils.isNotEmpty(editorSaveCaption)) {
            editorSaveCaption = loadResourceString(editorSaveCaption);
            component.setEditorSaveCaption(editorSaveCaption);
        }
    }

    protected void loadEditorCancelCaption(DataGrid component, Element element) {
        String editorCancelCaption = element.attributeValue("editorCancelCaption");
        if (StringUtils.isNotEmpty(editorCancelCaption)) {
            editorCancelCaption = loadResourceString(editorCancelCaption);
            component.setEditorCancelCaption(editorCancelCaption);
        }
    }

    protected void loadColumnsHidingAllowed(DataGrid component, Element element) {
        String columnsCollapsingAllowed = element.attributeValue("columnsCollapsingAllowed");
        if (StringUtils.isNotEmpty(columnsCollapsingAllowed)) {
            component.setColumnsCollapsingAllowed(Boolean.parseBoolean(columnsCollapsingAllowed));
        }
    }

    protected void loadColumnResizeMode(DataGrid component, Element element) {
        String columnResizeMode = element.attributeValue("columnResizeMode");
        if (StringUtils.isNotEmpty(columnResizeMode)) {
            component.setColumnResizeMode(DataGrid.ColumnResizeMode.valueOf(columnResizeMode));
        }
    }

    protected void loadSortable(DataGrid component, Element element) {
        String sortable = element.attributeValue("sortable");
        if (StringUtils.isNotEmpty(sortable)) {
            component.setSortable(Boolean.parseBoolean(sortable));
        }
    }

    protected void loadReorderingAllowed(DataGrid component, Element element) {
        String reorderingAllowed = element.attributeValue("reorderingAllowed");
        if (StringUtils.isNotEmpty(reorderingAllowed)) {
            component.setColumnReorderingAllowed(Boolean.parseBoolean(reorderingAllowed));
        }
    }

    protected void loadTextSelectionEnabled(DataGrid dataGrid, Element element) {
        String textSelectionEnabled = element.attributeValue("textSelectionEnabled");
        if (StringUtils.isNotEmpty(textSelectionEnabled)) {
            dataGrid.setTextSelectionEnabled(Boolean.parseBoolean(textSelectionEnabled));
        }
    }

    protected void loadBodyRowHeight(DataGrid dataGrid, Element element) {
        Integer bodyRowHeight = loadSizeInPx(element, "bodyRowHeight");
        if (bodyRowHeight != null) {
            dataGrid.setBodyRowHeight(bodyRowHeight);
        }
    }

    protected void loadHeaderRowHeight(DataGrid dataGrid, Element element) {
        Integer headerRowHeight = loadSizeInPx(element, "headerRowHeight");
        if (headerRowHeight != null) {
            dataGrid.setHeaderRowHeight(headerRowHeight);
        }
    }

    protected void loadFooterRowHeight(DataGrid dataGrid, Element element) {
        Integer footerRowHeight = loadSizeInPx(element, "footerRowHeight");
        if (footerRowHeight != null) {
            dataGrid.setFooterRowHeight(footerRowHeight);
        }
    }

    protected void loadHeaderVisible(DataGrid component, Element element) {
        String columnHeaderVisible = element.attributeValue("headerVisible");
        if (StringUtils.isNotEmpty(columnHeaderVisible)) {
            component.setHeaderVisible(Boolean.parseBoolean(columnHeaderVisible));
        }
    }

    protected void loadFooterVisible(DataGrid component, Element element) {
        String columnFooterVisible = element.attributeValue("footerVisible");
        if (StringUtils.isNotEmpty(columnFooterVisible)) {
            component.setFooterVisible(Boolean.parseBoolean(columnFooterVisible));
        }
    }

    protected void loadContextMenuEnabled(DataGrid dataGrid, Element element) {
        String contextMenuEnabled = element.attributeValue("contextMenuEnabled");
        if (StringUtils.isNotEmpty(contextMenuEnabled)) {
            dataGrid.setContextMenuEnabled(Boolean.parseBoolean(contextMenuEnabled));
        }
    }

    protected void loadButtonsPanel(DataGrid component) {
        if (buttonsPanelLoader != null) {
            buttonsPanelLoader.loadComponent();
            ButtonsPanel panel = (ButtonsPanel) buttonsPanelLoader.getResultComponent();

            String alwaysVisible = panelElement.attributeValue("alwaysVisible");
            if (alwaysVisible != null) {
                panel.setAlwaysVisible(Boolean.parseBoolean(alwaysVisible));
            }
        }
    }

    protected void loadRowsCount(DataGrid component, Element element) {
        Element rowsCountElement = element.element("rowsCount");
        if (rowsCountElement != null) {
            RowsCount rowsCount = factory.create(RowsCount.class);
            rowsCount.setRowsCountTarget(component);
            component.setRowsCount(rowsCount);
        }
    }

    protected List<Column> loadColumnsByInclude(String viewName, DataGrid component, Element columnsElement, MetaClass metaClass, View view) {
        View currentView = view;
        if (viewName != null) {
            ViewRepository viewRepository = beanLocator.get(ViewRepository.NAME);
            currentView = viewRepository.getView(metaClass, viewName);
        }

        Collection<String> appliedProperties = getAppliedProperties(columnsElement, currentView, metaClass);

        List<Column> columns = new ArrayList<>(appliedProperties.size());
        List<Element> columnElements = columnsElement.elements("column");
        Set<Element> overriddenColumns = new HashSet<>();

        DocumentFactory documentFactory = DatatypeElementFactory.getInstance();

        for (String property : appliedProperties) {
            Element column = getOverriddenColumn(columnElements, property);
            if (column == null) {
                column = documentFactory.createElement("column");
                column.add(documentFactory.createAttribute(column, "property", property));
            } else {
                overriddenColumns.add(column);
            }

            columns.add(loadColumn(component, column, metaClass));
        }

        // load remains columns
        List<Element> remainedColumns = columnsElement.elements("column");
        for (Element column : remainedColumns) {
            if (overriddenColumns.contains(column)) {
                continue;
            }

            // check property and add
            String propertyId = column.attributeValue("property");
            if (StringUtils.isNotEmpty(propertyId)) {
                MetaPropertyPath dynamicAttributePath = DynamicAttributesUtils.getMetaPropertyPath(metaClass, propertyId);

                MetaPropertyPath mpp = metaClass.getPropertyPath(propertyId);
                boolean isViewContainsProperty = mpp != null && getMetadataTools().viewContainsProperty(currentView, mpp);

                if (isViewContainsProperty || dynamicAttributePath != null) {
                    columns.add(loadColumn(component, column, metaClass));
                }
            }
        }

        return columns;
    }

    protected List<Column> loadColumns(DataGrid component, Element columnsElement, MetaClass metaClass, View view) {
        String includeByView = columnsElement.attributeValue("includeByView");
        String includeAll = columnsElement.attributeValue("includeAll");

        if (StringUtils.isNotBlank(includeByView) && StringUtils.isNotBlank(includeAll)) {
            throw new GuiDevelopmentException("'includeByView' and 'includeAll' attributes cannot be defined simultaneously",
                    getContext().getFullFrameId());
        }

        if (StringUtils.isNotBlank(includeByView)) {
            return loadColumnsByInclude(includeByView, component, columnsElement, metaClass, view);
        }

        if (StringUtils.isNotBlank(includeAll)) {
            if (Boolean.parseBoolean(includeAll)) {
                return loadColumnsByInclude(null, component, columnsElement, metaClass, view);
            }
        }

        List<Element> columnElements = columnsElement.elements("column");

        List<Column> columns = new ArrayList<>(columnElements.size());
        for (Element columnElement : columnElements) {
            columns.add(loadColumn(component, columnElement, metaClass));
        }
        return columns;
    }

    protected Column loadColumn(DataGrid component, Element element, MetaClass metaClass) {
        String id = element.attributeValue("id");
        String property = element.attributeValue("property");

        if (id == null) {
            if (property != null) {
                id = property;
            } else {
                throw new GuiDevelopmentException("A column must have whether id or property specified",
                        context.getCurrentFrameId(), "DataGrid ID", component.getId());
            }
        }

        Column column;
        if (property != null) {
            MetaPropertyPath metaPropertyPath = getMetadataTools().resolveMetaPropertyPath(metaClass, property);
            column = component.addColumn(id, metaPropertyPath);
        } else {
            column = component.addColumn(id, null);
        }

        String expandRatio = element.attributeValue("expandRatio");
        if (StringUtils.isNotEmpty(expandRatio)) {
            column.setExpandRatio(Integer.parseInt(expandRatio));
        }

        String collapsed = element.attributeValue("collapsed");
        if (StringUtils.isNotEmpty(collapsed)) {
            column.setCollapsed(Boolean.parseBoolean(collapsed));
        }

        String collapsible = element.attributeValue("collapsible");
        if (StringUtils.isNotEmpty(collapsible)) {
            column.setCollapsible(Boolean.parseBoolean(collapsible));
        }

        String collapsingToggleCaption = element.attributeValue("collapsingToggleCaption");
        if (StringUtils.isNotEmpty(collapsingToggleCaption)) {
            collapsingToggleCaption = loadResourceString(collapsingToggleCaption);
            column.setCollapsingToggleCaption(collapsingToggleCaption);
        }

        String sortable = element.attributeValue("sortable");
        if (StringUtils.isNotEmpty(sortable)) {
            column.setSortable(Boolean.parseBoolean(sortable));
        }

        String resizable = element.attributeValue("resizable");
        if (StringUtils.isNotEmpty(resizable)) {
            column.setResizable(Boolean.parseBoolean(resizable));
        }

        String editable = element.attributeValue("editable");
        if (StringUtils.isNotEmpty(editable)) {
            column.setEditable(Boolean.parseBoolean(editable));
        }

        String caption = loadCaption(element);

        if (caption == null) {
            String columnCaption;
            if (column.getPropertyPath() != null) {
                MetaProperty metaProperty = column.getPropertyPath().getMetaProperty();
                String propertyName = metaProperty.getName();

                if (DynamicAttributesUtils.isDynamicAttribute(metaProperty)) {
                    CategoryAttribute categoryAttribute = DynamicAttributesUtils.getCategoryAttribute(metaProperty);
                    columnCaption = LocaleHelper.isLocalizedValueDefined(categoryAttribute.getLocaleNames()) ?
                            categoryAttribute.getLocaleName() :
                            StringUtils.capitalize(categoryAttribute.getName());
                } else {
                    MetaClass propertyMetaClass = getMetadataTools().getPropertyEnclosingMetaClass(column.getPropertyPath());

                    columnCaption = getMessageTools().getPropertyCaption(propertyMetaClass, propertyName);
                }
            } else {
                Class<?> declaringClass = metaClass.getJavaClass();
                String className = declaringClass.getName();
                int i = className.lastIndexOf('.');
                if (i > -1) {
                    className = className.substring(i + 1);
                }
                columnCaption = getMessages().getMessage(declaringClass, className + "." + id);
            }
            column.setCaption(columnCaption);
        } else {
            column.setCaption(caption);
        }

        ((Component.HasXmlDescriptor) column).setXmlDescriptor(element);

        Integer width = loadSizeInPx(element, "width");
        if (width != null) {
            column.setWidth(width);
        }

        Integer minimumWidth = loadSizeInPx(element, "minimumWidth");
        if (minimumWidth != null) {
            column.setMinimumWidth(minimumWidth);
        }

        Integer maximumWidth = loadSizeInPx(element, "maximumWidth");
        if (maximumWidth != null) {
            column.setMaximumWidth(maximumWidth);
        }

        column.setFormatter(loadFormatter(element));

        return column;
    }

    protected String loadCaption(Element element) {
        if (element.attribute("caption") != null) {
            String caption = element.attributeValue("caption");

            return loadResourceString(caption);
        }
        return null;
    }

    @Nullable
    protected Integer loadSizeInPx(Element element, String propertyName) {
        String value = loadThemeString(element.attributeValue(propertyName));
        if (!StringUtils.isBlank(value)) {
            if (StringUtils.endsWith(value, "px")) {
                value = StringUtils.substring(value, 0, value.length() - 2);
            }
            try {
                // Only integer allowed in XML
                return Integer.parseInt(value);
            } catch (NumberFormatException e) {
                throw new GuiDevelopmentException("Property '" + propertyName + "' must contain only numeric value",
                        context.getCurrentFrameId(), propertyName, element.attributeValue(propertyName));
            }
        }
        return null;
    }

    protected MetadataTools getMetadataTools() {
        return beanLocator.get(MetadataTools.NAME);
    }

    protected DynamicAttributesGuiTools getDynamicAttributesGuiTools() {
        return beanLocator.get(DynamicAttributesGuiTools.NAME);
    }

    protected void addDynamicAttributes(DataGrid component, MetaClass metaClass,
                                        Datasource ds, CollectionLoader collectionLoader,
                                        List<Column> availableColumns) {
        if (getMetadataTools().isPersistent(metaClass)) {
            String windowId = getWindowId(context);

            Set<CategoryAttribute> attributesToShow =
                    getDynamicAttributesGuiTools().getAttributesToShowOnTheScreen(metaClass,
                            windowId, component.getId());
            if (CollectionUtils.isNotEmpty(attributesToShow)) {
                if (collectionLoader != null) {
                    collectionLoader.setLoadDynamicAttributes(true);
                } else if (ds != null) {
                    ds.setLoadDynamicAttributes(true);
                }
                for (CategoryAttribute attribute : attributesToShow) {
                    final MetaPropertyPath metaPropertyPath =
                            DynamicAttributesUtils.getMetaPropertyPath(metaClass, attribute);

                    Object columnWithSameId = IterableUtils.find(availableColumns, column -> {
                        MetaPropertyPath propertyPath = column.getPropertyPath();
                        return propertyPath != null && propertyPath.equals(metaPropertyPath);
                    });

                    if (columnWithSameId != null) {
                        continue;
                    }

                    final Column column =
                            component.addColumn(metaPropertyPath.getMetaProperty().getName(), metaPropertyPath);

                    column.setCaption(LocaleHelper.isLocalizedValueDefined(attribute.getLocaleNames()) ?
                            attribute.getLocaleName() :
                            StringUtils.capitalize(attribute.getName()));
                }
            }

            if (ds != null) {
                getDynamicAttributesGuiTools().listenDynamicAttributesChanges(ds);
            }
        }
    }

    protected void loadSelectionMode(DataGrid component, Element element) {
        String selectionMode = element.attributeValue("selectionMode");
        if (StringUtils.isNotEmpty(selectionMode)) {
            component.setSelectionMode(DataGrid.SelectionMode.valueOf(selectionMode));
        }
    }

    protected void loadFrozenColumnCount(DataGrid component, Element element) {
        String frozenColumnCount = element.attributeValue("frozenColumnCount");
        if (StringUtils.isNotEmpty(frozenColumnCount)) {
            component.setFrozenColumnCount(Integer.parseInt(frozenColumnCount));
        }
    }

    protected Collection<String> getAppliedProperties(Element columnsElement, View view, MetaClass metaClass) {
        String exclude = columnsElement.attributeValue("exclude");
        List<String> excludes = StringUtils.isEmpty(exclude) ? Collections.emptyList() :
                Splitter.on(",").omitEmptyStrings().trimResults().splitToList(exclude);

        String includeSystem = columnsElement.attributeValue("includeSystem");
        boolean isIncludeSystem = StringUtils.isNotBlank(includeSystem) && Boolean.parseBoolean(includeSystem);

        MetadataTools metadataTools = getMetadataTools();

        Stream<String> properties;
        if (metadataTools.isPersistent(metaClass)) {
            properties = view.getProperties().stream().map(ViewProperty::getName);
        } else {
            properties = metaClass.getOwnProperties().stream().map(MetadataObject::getName);
        }

        List<String> appliedProperties = properties.filter(s -> {
            MetaProperty metaProperty = metaClass.getProperty(s);
            if (isIncludeSystem || !metadataTools.isSystem(metaProperty)) {
                return !excludes.contains(s);
            } else {
                return false;
            }
        }).collect(Collectors.toList());

        return appliedProperties;
    }

    @Nullable
    protected Element getOverriddenColumn(List<Element> columns, String property) {
        if (CollectionUtils.isEmpty(columns)) {
            return null;
        }

        for (Element element : columns) {
            String propertyAttr = element.attributeValue("property");
            if (StringUtils.isNotEmpty(propertyAttr) && propertyAttr.equals(property)) {
                return element;
            }
        }
        return null;
    }
}