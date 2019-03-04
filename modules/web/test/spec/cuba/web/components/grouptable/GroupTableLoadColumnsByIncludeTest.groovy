/*
 * Copyright (c) 2008-2019 Haulmont.
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

package spec.cuba.web.components.grouptable

import com.haulmont.cuba.gui.components.GroupTable
import com.haulmont.cuba.gui.config.WindowConfig
import com.haulmont.cuba.gui.screen.OpenMode
import com.haulmont.cuba.gui.sys.UiControllersConfiguration
import com.haulmont.cuba.security.app.UserManagementService
import com.haulmont.cuba.web.testsupport.TestServiceProxy
import org.springframework.core.type.classreading.MetadataReaderFactory
import spec.cuba.web.UiScreenSpec
import spec.cuba.web.components.grouptable.screens.GroupTableLoadColumnsByIncludeScreen

@SuppressWarnings(["GroovyAccessibility", "GroovyAssignabilityCheck"])
class GroupTableLoadColumnsByIncludeTest extends UiScreenSpec {

    void setup() {
        TestServiceProxy.mock(UserManagementService, Mock(UserManagementService) {
            getSubstitutedUsers(_) >> Collections.emptyList()
        })

        def configuration = new UiControllersConfiguration()
        configuration.applicationContext = cont.getApplicationContext()
        configuration.metadataReaderFactory = cont.getBean(MetadataReaderFactory)
        configuration.basePackages = ['spec.cuba.web.components.grouptable.screens']

        def windowConfig = cont.getBean(WindowConfig)
        windowConfig.configurations = [configuration]
        windowConfig.initialized = false
    }

    def cleanup() {
        TestServiceProxy.clear()

        def windowConfig = cont.getBean(WindowConfig)
        windowConfig.configurations = []
        windowConfig.initialized = false
    }

    def "includeBy view"() {
        def screens = vaadinUi.screens

        def mainWindow = screens.create("mainWindow", OpenMode.ROOT)
        screens.show(mainWindow)

        def groupTableScreen = screens.create(GroupTableLoadColumnsByIncludeScreen)
        groupTableScreen.show()

        when:
        def groupTable = groupTableScreen.getWindow().getComponentNN("usersTableView") as GroupTable
        def columnList = groupTable.getColumns()

        then:
        columnList.size() == 17
    }

    def "includeBy local"() {
        def screens = vaadinUi.screens

        def mainWindow = screens.create("mainWindow", OpenMode.ROOT)
        screens.show(mainWindow)

        def groupTableScreen = screens.create(GroupTableLoadColumnsByIncludeScreen)
        groupTableScreen.show()

        when:
        def groupTable = groupTableScreen.getWindow().getComponentNN("usersTableLocal") as GroupTable
        def columnList = groupTable.getColumns()

        then:
        columnList.size() == 16
    }

    def "entity with embedded property"() {
        def screens = vaadinUi.screens

        def mainWindow = screens.create("mainWindow", OpenMode.ROOT)
        screens.show(mainWindow)

        def groupTableScreen = screens.create(GroupTableLoadColumnsByIncludeScreen)
        groupTableScreen.show()

        when:
        def groupTable = groupTableScreen.getWindow().getComponentNN("customersEmbTable") as GroupTable
        def columnList = groupTable.getColumns()

        then:
        columnList.size() == 6

        groupTable.getColumn("address.city") != null
        groupTable.getColumn("address.zip") != null
        groupTable.getColumn("address") != null
    }

    def "grouping and overriding columns"() {
        def screens = vaadinUi.screens

        def mainWindow = screens.create("mainWindow", OpenMode.ROOT)
        screens.show(mainWindow)

        def groupTableScreen = screens.create(GroupTableLoadColumnsByIncludeScreen)
        groupTableScreen.show()

        when:
        def groupTable = groupTableScreen.getWindow().getComponentNN("customersTableGrouping") as GroupTable
        def columnList = groupTable.getColumns()

        then:
        columnList.size() == 4

        groupTable.getColumn("address").isGroupAllowed()
        !groupTable.getColumn("name").isSortable()
    }

    def "group table with non persistent entity"() {
        def screens = vaadinUi.screens

        def mainWindow = screens.create("mainWindow", OpenMode.ROOT)
        screens.show(mainWindow)

        def groupTableScreen = screens.create(GroupTableLoadColumnsByIncludeScreen)
        groupTableScreen.show()

        when:
        def groupTable = groupTableScreen.getWindow().getComponentNN("goodsInfoTable") as GroupTable
        def columnList = groupTable.getColumns()

        then:
        columnList.size() == 4

        groupTable.getColumn("isFragile") == null
    }

    def "exclude property"() {
        def screens = vaadinUi.screens

        def mainWindow = screens.create("mainWindow", OpenMode.ROOT)
        screens.show(mainWindow)

        def groupTableScreen = screens.create(GroupTableLoadColumnsByIncludeScreen)
        groupTableScreen.show()

        when:
        def groupTable = groupTableScreen.getWindow().getComponentNN("customersTableExcluding") as GroupTable
        def columnList = groupTable.getColumns()

        then:
        columnList.size() == 2

        groupTable.getColumn("address") == null
        groupTable.getColumn("name") == null
    }
}
