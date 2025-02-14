/*
 *  Copyright 2022 Red Hat
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.jboss.hal.client.accesscontrol;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import javax.inject.Inject;
import javax.inject.Provider;

import org.jboss.hal.client.accesscontrol.AccessControlTasks.AddAssignment;
import org.jboss.hal.client.accesscontrol.AccessControlTasks.AddRoleMapping;
import org.jboss.hal.client.accesscontrol.AccessControlTasks.CheckRoleMapping;
import org.jboss.hal.config.Role;
import org.jboss.hal.config.User;
import org.jboss.hal.config.UserChangedEvent;
import org.jboss.hal.core.finder.ColumnAction;
import org.jboss.hal.core.finder.ColumnActionHandler;
import org.jboss.hal.core.finder.Finder;
import org.jboss.hal.core.finder.FinderColumn;
import org.jboss.hal.core.finder.FinderPath;
import org.jboss.hal.core.finder.FinderSegment;
import org.jboss.hal.core.finder.ItemAction;
import org.jboss.hal.core.finder.ItemActionFactory;
import org.jboss.hal.core.finder.ItemDisplay;
import org.jboss.hal.dmr.Operation;
import org.jboss.hal.dmr.ResourceAddress;
import org.jboss.hal.dmr.dispatch.Dispatcher;
import org.jboss.hal.flow.FlowContext;
import org.jboss.hal.flow.Progress;
import org.jboss.hal.flow.Task;
import org.jboss.hal.resources.Ids;
import org.jboss.hal.resources.Resources;
import org.jboss.hal.spi.AsyncColumn;
import org.jboss.hal.spi.Footer;
import org.jboss.hal.spi.Message;
import org.jboss.hal.spi.MessageEvent;

import com.google.common.collect.Sets;
import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.web.bindery.event.shared.EventBus;

import elemental2.dom.HTMLElement;
import elemental2.promise.Promise;

import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static java.util.Comparator.comparing;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;
import static org.jboss.elemento.Elements.span;
import static org.jboss.hal.client.accesscontrol.AddressTemplates.EXCLUDE_TEMPLATE;
import static org.jboss.hal.client.accesscontrol.AddressTemplates.INCLUDE_TEMPLATE;
import static org.jboss.hal.dmr.ModelDescriptionConstants.REMOVE;
import static org.jboss.hal.flow.Flow.sequential;
import static org.jboss.hal.resources.CSS.fontAwesome;

/** Shows the members (principals) of the selected role (the reverse of the {@link AssignmentColumn}. */
@AsyncColumn(Ids.MEMBERSHIP)
public class MembershipColumn extends FinderColumn<Assignment> {

    private final Dispatcher dispatcher;
    private final EventBus eventBus;
    private final Provider<Progress> progress;
    private final User currentUser;
    private final AccessControl accessControl;
    private final Resources resources;

    @Inject
    public MembershipColumn(Finder finder,
            ItemActionFactory itemActionFactory,
            Dispatcher dispatcher,
            EventBus eventBus,
            @Footer Provider<Progress> progress,
            User currentUser,
            AccessControl accessControl,
            AccessControlTokens tokens,
            Resources resources) {

        super(new Builder<Assignment>(finder, Ids.MEMBERSHIP, resources.constants().membership())
                .withFilter()
                .filterDescription(resources.messages().membershipColumnFilterDescription())
                .onPreview(item -> new MembershipPreview(tokens, item.getPrincipal(), resources)));

        this.dispatcher = dispatcher;
        this.eventBus = eventBus;
        this.progress = progress;
        this.currentUser = currentUser;
        this.accessControl = accessControl;
        this.resources = resources;

        setItemsProvider((context -> new Promise<>((resolve, reject) -> {
            List<Assignment> assignments = new ArrayList<>();
            Role role = findRole(finder.getContext().getPath());
            if (role != null) {
                accessControl.assignments().byRole(role)
                        .sorted(Assignments.EXCLUDES_FIRST
                                .thenComparing(Assignments.USERS_FIRST)
                                .thenComparing(Assignments.BY_PRINCIPAL_NAME))
                        .forEach(assignments::add);
            }

            // Add the missing principals to the include / exclude column action drop-downs
            resetColumnActions();
            Set<Principal> assignedPrincipals = assignments.stream().map(Assignment::getPrincipal).collect(toSet());
            Set<Principal> missingPrincipals = Sets.newHashSet(accessControl.principals());
            missingPrincipals.removeAll(assignedPrincipals);

            List<ColumnAction<Assignment>> includeActions = missingPrincipals.stream()
                    .sorted(comparing(Principal::getType).thenComparing(Principal::getName))
                    .map(principal -> new ColumnAction.Builder<Assignment>(includeId(principal))
                            .title(typeAndName(principal))
                            .handler(columnActionHandler(principal, true))
                            .build())
                    .collect(toList());
            if (!includeActions.isEmpty()) {
                addColumnActions(Ids.MEMBERSHIP_INCLUDE, fontAwesome("plus"), resources.constants().includeUserGroup(),
                        includeActions);
            }

            List<ColumnAction<Assignment>> excludeActions = missingPrincipals.stream()
                    .sorted(comparing(Principal::getType).thenComparing(Principal::getName))
                    .map(principal -> new ColumnAction.Builder<Assignment>(excludeId(principal))
                            .title(typeAndName(principal))
                            .handler(columnActionHandler(principal, false))
                            .build())
                    .collect(toList());
            if (!excludeActions.isEmpty()) {
                addColumnActions(Ids.MEMBERSHIP_EXCLUDE, fontAwesome("minus"), resources.constants().excludeUserGroup(),
                        excludeActions);
            }

            resolve.onInvoke(assignments);
        })));

        setItemRenderer(item -> new ItemDisplay<Assignment>() {
            @Override
            public String getId() {
                return item.getPrincipal().getId();
            }

            @Override
            public String getTitle() {
                return item.getPrincipal().getName();
            }

            @Override
            public HTMLElement element() {
                if (item.getPrincipal().getRealm() != null) {
                    return ItemDisplay
                            .withSubtitle(item.getPrincipal().getName(), item.getPrincipal().getRealm());
                }
                return null;
            }

            @Override
            public String getTooltip() {
                return item.isInclude() ? resources.constants().includes() : resources.constants().excludes();
            }

            @Override
            public HTMLElement getIcon() {
                HTMLElement icon = span().element();
                if (item.isInclude()) {
                    icon.className = fontAwesome("plus");
                } else {
                    icon.className = fontAwesome("minus");
                }
                return icon;
            }

            @Override
            public String getFilterData() {
                List<String> data = new ArrayList<>(PrincipalColumn.filterData(item.getPrincipal()));
                data.add(item.isInclude() ? "includes" : "excludes"); // NON-NLS
                return String.join(" ", data);
            }

            @Override
            public List<ItemAction<Assignment>> actions() {
                return singletonList(itemActionFactory.remove(resources.constants().membership(),
                        item.getPrincipal().getName(), item.isInclude() ? INCLUDE_TEMPLATE : EXCLUDE_TEMPLATE,
                        itm -> {
                            ResourceAddress address = AddressTemplates.assignment(itm);
                            Operation operation = new Operation.Builder(address, REMOVE).build();
                            dispatcher.execute(operation, result -> {
                                MessageEvent.fire(eventBus, Message.success(resources.messages()
                                        .removeResourceSuccess(resources.constants().membership(),
                                                itm.getPrincipal().getName())));
                                accessControl.reload(() -> {
                                    refresh(RefreshMode.CLEAR_SELECTION);
                                    if (isCurrentUser(itm.getPrincipal())) {
                                        eventBus.fireEvent(new UserChangedEvent());
                                    }
                                });
                            });
                        }));
            }
        });
    }

    private boolean isCurrentUser(Principal principal) {
        return principal != null &&
                principal.getType() == Principal.Type.USER &&
                principal.getName().equals(currentUser.getName());
    }

    private Role findRole(FinderPath path) {
        FinderSegment<?> segment = path.findColumn(Ids.ROLE);
        if (segment != null) {
            return accessControl.roles().get(segment.getItemId());
        }
        return null;
    }

    private String includeId(Principal principal) {
        return Ids.build(Ids.MEMBERSHIP_INCLUDE, principal.getType().name().toLowerCase(), principal.getName());
    }

    private String excludeId(Principal principal) {
        return Ids.build(Ids.MEMBERSHIP_EXCLUDE, principal.getType().name().toLowerCase(), principal.getName());
    }

    private String typeAndName(Principal principal) {
        if (principal.getType() == Principal.Type.USER) {
            return resources.constants().user() + " " + principal.getName();
        } else {
            return resources.constants().group() + " " + principal.getName();
        }
    }

    private ColumnActionHandler<Assignment> columnActionHandler(Principal principal, boolean include) {
        return column -> {
            Role role = findRole(getFinder().getContext().getPath());
            if (role != null) {
                List<Task<FlowContext>> tasks = asList(new CheckRoleMapping(dispatcher, role),
                        new AddRoleMapping(dispatcher, role, status -> status == 404),
                        new AddAssignment(dispatcher, role, principal, include));
                sequential(new FlowContext(progress.get()), tasks)
                        .then(__ -> {
                            String type = principal.getType() == Principal.Type.USER
                                    ? resources.constants().user()
                                    : resources.constants().group();
                            SafeHtml message = include
                                    ? resources.messages().assignmentIncludeSuccess(type, principal.getName())
                                    : resources.messages().assignmentExcludeSuccess(type, principal.getName());
                            MessageEvent.fire(eventBus, Message.success(message));
                            accessControl.reload(() -> {
                                refresh(RefreshMode.RESTORE_SELECTION);
                                if (isCurrentUser(principal)) {
                                    eventBus.fireEvent(new UserChangedEvent());
                                }
                            });
                            return null;
                        });
            }
        };
    }
}
