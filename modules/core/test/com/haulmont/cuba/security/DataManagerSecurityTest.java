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
 *
 */

package com.haulmont.cuba.security;

import com.haulmont.cuba.core.EntityManager;
import com.haulmont.cuba.core.Transaction;
import com.haulmont.cuba.core.app.DataService;
import com.haulmont.cuba.core.entity.Server;
import com.haulmont.cuba.core.global.*;
import com.haulmont.cuba.security.auth.AuthenticationManager;
import com.haulmont.cuba.security.auth.Credentials;
import com.haulmont.cuba.security.auth.LoginPasswordCredentials;
import com.haulmont.cuba.security.entity.*;
import com.haulmont.cuba.security.global.UserSession;
import com.haulmont.cuba.testsupport.TestContainer;
import com.haulmont.cuba.testsupport.TestUserSessionSource;
import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;

import java.util.List;
import java.util.Locale;

import static org.junit.Assert.*;

public class DataManagerSecurityTest {

    @ClassRule
    public static TestContainer cont = TestContainer.Common.INSTANCE;

    private static final String USER_NAME = "testUser";
    private static final String USER_PASSW = "testUser";
    private static final String PERM_TARGET = "sys$Server:" + EntityOp.READ.getId();

    private PasswordEncryption passwordEncryption;
    private Role role;
    private Permission permission;
    private Group group;
    private User user;
    private UserRole userRole;
    private Server server;

    @Before
    public void setUp() throws Exception {
        passwordEncryption = AppBeans.get(PasswordEncryption.class);

        try (Transaction tx = cont.persistence().createTransaction()) {
            EntityManager em = cont.persistence().getEntityManager();

            server = new Server();
            server.setName("someServer");
            server.setRunning(false);
            em.persist(server);

            role = new Role();
            role.setName("testRole1");
            em.persist(role);

            permission = new Permission();
            permission.setRole(role);
            permission.setType(PermissionType.ENTITY_OP);
            permission.setTarget(PERM_TARGET);
            permission.setValue(0);
            em.persist(permission);

            group = new Group();
            group.setName("testGroup");
            em.persist(group);

            user = new User();
            user.setName(USER_NAME);
            user.setLogin(USER_NAME);

            String pwd = passwordEncryption.getPasswordHash(user.getId(), USER_PASSW);
            user.setPassword(pwd);

            user.setGroup(group);
            em.persist(user);

            userRole = new UserRole();
            userRole.setUser(user);
            userRole.setRole(role);
            em.persist(userRole);

            tx.commit();
        }
    }

    @After
    public void tearDown() throws Exception {
        cont.deleteRecord(userRole, user, group, permission, role, server);
    }

    @Test
    public void test() {
        AuthenticationManager lw = AppBeans.get(AuthenticationManager.NAME);
        Credentials credentials = new LoginPasswordCredentials(USER_NAME, USER_PASSW, Locale.getDefault());
        UserSession userSession = lw.login(credentials).getSession();
        assertNotNull(userSession);

        UserSessionSource uss = AppBeans.get(UserSessionSource.class);
        UserSession savedUserSession = uss.getUserSession();
        ((TestUserSessionSource) uss).setUserSession(userSession);
        try {
            DataManager dm = AppBeans.get(DataManager.NAME);
            LoadContext<Server> loadContext = LoadContext.create(Server.class)
                    .setQuery(new LoadContext.Query("select s from sys$Server s"));
            List<Server> list = dm.loadList(loadContext);
            assertFalse("Permission took effect when calling DataManager inside middleware", list.isEmpty());

            DataService ds = AppBeans.get(DataService.NAME);
            loadContext = LoadContext.create(Server.class)
                    .setQuery(new LoadContext.Query("select s from sys$Server s"));
            list = ds.loadList(loadContext);
            assertTrue("Permission did not take effect when calling DataService", list.isEmpty());

        } finally {
            ((TestUserSessionSource) uss).setUserSession(savedUserSession);
        }
    }
}
