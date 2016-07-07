/*
 * Copyright 2015-2016 Red Hat, Inc, and individual contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jboss.hal.client.configuration.subsystem.logging;

import java.util.List;
import javax.inject.Inject;

import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.annotations.NameToken;
import com.gwtplatform.mvp.client.annotations.ProxyCodeSplit;
import com.gwtplatform.mvp.client.proxy.ProxyPlace;
import com.gwtplatform.mvp.shared.proxy.PlaceRequest;
import org.jboss.hal.client.configuration.PathsTypeahead;
import org.jboss.hal.config.Environment;
import org.jboss.hal.core.finder.Finder;
import org.jboss.hal.core.finder.FinderPath;
import org.jboss.hal.core.finder.FinderPathFactory;
import org.jboss.hal.core.mbui.MbuiPresenter;
import org.jboss.hal.core.mbui.MbuiView;
import org.jboss.hal.core.mvp.HasVerticalNavigation;
import org.jboss.hal.dmr.ModelDescriptionConstants;
import org.jboss.hal.dmr.ModelNode;
import org.jboss.hal.dmr.dispatch.Dispatcher;
import org.jboss.hal.dmr.model.NamedNode;
import org.jboss.hal.dmr.model.Operation;
import org.jboss.hal.dmr.model.ResourceAddress;
import org.jboss.hal.meta.SelectionAwareStatementContext;
import org.jboss.hal.meta.StatementContext;
import org.jboss.hal.meta.token.NameTokens;
import org.jboss.hal.resources.Names;
import org.jboss.hal.spi.Requires;

import static org.jboss.hal.client.configuration.subsystem.logging.AddressTemplates.*;
import static org.jboss.hal.dmr.ModelDescriptionConstants.NAME;
import static org.jboss.hal.dmr.ModelDescriptionConstants.READ_RESOURCE_OPERATION;
import static org.jboss.hal.dmr.ModelDescriptionConstants.RECURSIVE_DEPTH;
import static org.jboss.hal.dmr.ModelNodeHelper.asNamedNodes;
import static org.jboss.hal.dmr.ModelNodeHelper.failSafePropertyList;

/**
 * @author Harald Pehl
 */
public class LoggingProfilePresenter
        extends MbuiPresenter<LoggingProfilePresenter.MyView, LoggingProfilePresenter.MyProxy> {

    // @formatter:off
    @ProxyCodeSplit
    @NameToken(NameTokens.LOGGING_PROFILE)
    @Requires(LOGGING_PROFILE_ADDRESS)
    public interface MyProxy extends ProxyPlace<LoggingProfilePresenter> {}

    public interface MyView extends MbuiView<LoggingProfilePresenter>, HasVerticalNavigation {
        void updateRootLogger(ModelNode modelNode);
        void noRootLogger();
        void updateLogger(List<NamedNode> items);

        void updateAsyncHandler(List<NamedNode> items);
        void updateConsoleHandler(List<NamedNode> items);
        void updateCustomHandler(List<NamedNode> items);
        void updateFileHandler(List<NamedNode> items);
        void updatePeriodicHandler(List<NamedNode> items);
        void updatePeriodicSizeHandler(List<NamedNode> items);
        void updateSizeHandler(List<NamedNode> items);
        void updateSyslogHandler(List<NamedNode> items);

        void updateCustomFormatter(List<NamedNode> items);
        void updatePatternFormatter(List<NamedNode> items);
    }
    // @formatter:on


    private final FinderPathFactory finderPathFactory;
    private final Environment environment;
    private final StatementContext statementContext;
    private final Dispatcher dispatcher;
    private String loggingProfile;

    @Inject
    public LoggingProfilePresenter(final EventBus eventBus,
            final MyView view,
            final MyProxy myProxy,
            final Finder finder,
            final FinderPathFactory finderPathFactory,
            final Environment environment,
            final StatementContext statementContext,
            final Dispatcher dispatcher) {
        super(eventBus, view, myProxy, finder);
        this.finderPathFactory = finderPathFactory;
        this.environment = environment;
        this.statementContext = new SelectionAwareStatementContext(statementContext, () -> loggingProfile);
        this.dispatcher = dispatcher;
    }

    @Override
    protected void onBind() {
        super.onBind();
        getView().setPresenter(this);
    }

    @Override
    public void prepareFromRequest(final PlaceRequest request) {
        super.prepareFromRequest(request);
        loggingProfile = request.getParameter(NAME, null);
    }

    String getLoggingProfile() {
        return loggingProfile;
    }

    @Override
    protected FinderPath finderPath() {
        return finderPathFactory.configurationSubsystemPath(LOGGING_SUBSYSTEM_TEMPLATE.lastValue())
                .append(ModelDescriptionConstants.LOGGING_PROFILE, Logging.profileId(loggingProfile),
                        Names.LOGGING_PROFILE, loggingProfile);
    }

    @Override
    protected void reload() {
        ResourceAddress address = SELECTED_LOGGING_PROFILE_TEMPLATE.resolve(statementContext);
        Operation operation = new Operation.Builder(READ_RESOURCE_OPERATION, address)
                .param(RECURSIVE_DEPTH, 2)
                .build();
        dispatcher.execute(operation, result -> {
            // @formatter:off
            if (result.hasDefined(ROOT_LOGGER_TEMPLATE.lastKey())) {
                getView().updateRootLogger(result.get(ROOT_LOGGER_TEMPLATE.lastKey()).get(ROOT_LOGGER_TEMPLATE.lastValue()));
            } else {
                getView().noRootLogger();
            }
            getView().updateLogger(asNamedNodes(failSafePropertyList(result, LOGGER_TEMPLATE.lastKey())));

            getView().updateAsyncHandler(asNamedNodes(failSafePropertyList(result, ASYNC_HANDLER_TEMPLATE.lastKey())));
            getView().updateConsoleHandler(asNamedNodes(failSafePropertyList(result, CONSOLE_HANDLER_TEMPLATE.lastKey())));
            getView().updateCustomHandler(asNamedNodes(failSafePropertyList(result, CUSTOM_HANDLER_TEMPLATE.lastKey())));
            getView().updateFileHandler(asNamedNodes(failSafePropertyList(result, FILE_HANDLER_TEMPLATE.lastKey())));
            getView().updatePeriodicHandler(asNamedNodes(failSafePropertyList(result, PERIODIC_ROTATING_FILE_HANDLER_TEMPLATE.lastKey())));
            getView().updatePeriodicSizeHandler(asNamedNodes(failSafePropertyList(result, PERIODIC_SIZE_ROTATING_FILE_HANDLER_TEMPLATE.lastKey())));
            getView().updateSizeHandler(asNamedNodes(failSafePropertyList(result, SIZE_ROTATING_FILE_HANDLER_TEMPLATE.lastKey())));
            getView().updateSyslogHandler(asNamedNodes(failSafePropertyList(result, SYSLOG_HANDLER_TEMPLATE.lastKey())));

            getView().updateCustomFormatter(asNamedNodes(failSafePropertyList(result, CUSTOM_FORMATTER_TEMPLATE.lastKey())));
            getView().updatePatternFormatter(asNamedNodes(failSafePropertyList(result, PATTERN_FORMATTER_TEMPLATE.lastKey())));
            // @formatter:on
        });
        PathsTypeahead.updateOperation(environment, dispatcher, statementContext);
    }
}
