/**
* Licensed to the Apache Software Foundation (ASF) under one
* or more contributor license agreements.  See the NOTICE file
* distributed with this work for additional information
* regarding copyright ownership.  The ASF licenses this file
* to you under the Apache License, Version 2.0 (the
* "License"); you may not use this file except in compliance
* with the License.  You may obtain a copy of the License at
*
*     http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/

package org.apache.hadoop.yarn.server.nodemanager;

import static org.junit.Assert.fail;

import java.util.Collection;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.yarn.api.records.ContainerId;
import org.apache.hadoop.yarn.factories.RecordFactory;
import org.apache.hadoop.yarn.factory.providers.RecordFactoryProvider;
import org.apache.hadoop.yarn.server.security.ContainerTokenSecretManager;
import org.apache.hadoop.yarn.server.nodemanager.containermanager.ContainerManagerImpl;
import org.apache.hadoop.yarn.server.nodemanager.containermanager.application.Application;
import org.apache.hadoop.yarn.server.nodemanager.containermanager.application.ApplicationEvent;
import org.apache.hadoop.yarn.server.nodemanager.containermanager.application.ApplicationEventType;
import org.apache.hadoop.yarn.server.nodemanager.containermanager.application.ApplicationInitedEvent;
import org.apache.hadoop.yarn.server.nodemanager.containermanager.container.Container;
import org.apache.hadoop.yarn.server.nodemanager.containermanager.container.ContainerEvent;
import org.apache.hadoop.yarn.server.nodemanager.containermanager.container.ContainerEventType;
import org.apache.hadoop.yarn.server.nodemanager.containermanager.container.ContainerExitEvent;
import org.apache.hadoop.yarn.server.nodemanager.containermanager.container.ContainerResourceLocalizedEvent;
import org.apache.hadoop.yarn.server.nodemanager.containermanager.launcher.ContainersLauncher;
import org.apache.hadoop.yarn.server.nodemanager.containermanager.launcher.ContainersLauncherEvent;
import org.apache.hadoop.yarn.server.nodemanager.containermanager.localizer.LocalResourceRequest;
import org.apache.hadoop.yarn.server.nodemanager.containermanager.localizer.ResourceLocalizationService;
import org.apache.hadoop.yarn.server.nodemanager.containermanager.localizer.event.ApplicationLocalizationEvent;
import org.apache.hadoop.yarn.server.nodemanager.containermanager.localizer.event.ContainerLocalizationEvent;
import org.apache.hadoop.yarn.server.nodemanager.containermanager.localizer.event.ContainerLocalizationRequestEvent;
import org.apache.hadoop.yarn.server.nodemanager.containermanager.localizer.event.LocalizationEvent;
import org.apache.hadoop.yarn.server.nodemanager.containermanager.logaggregation.LogAggregationService;
import org.apache.hadoop.yarn.server.nodemanager.containermanager.logaggregation.event.LogAggregatorEvent;
import org.apache.hadoop.yarn.server.nodemanager.metrics.NodeManagerMetrics;

public class DummyContainerManager extends ContainerManagerImpl {

  private static final Log LOG = LogFactory
      .getLog(DummyContainerManager.class);

  private static final RecordFactory recordFactory = RecordFactoryProvider.getRecordFactory(null);
  
  public DummyContainerManager(Context context, ContainerExecutor exec,
      DeletionService deletionContext, NodeStatusUpdater nodeStatusUpdater,
      NodeManagerMetrics metrics, ContainerTokenSecretManager containerTokenSecretManager) {
    super(context, exec, deletionContext, nodeStatusUpdater, metrics, containerTokenSecretManager);
  }

  @Override
  protected ResourceLocalizationService createResourceLocalizationService(ContainerExecutor exec,
      DeletionService deletionContext) {
    return new ResourceLocalizationService(super.dispatcher, exec, deletionContext) {
      @Override
      public void handle(LocalizationEvent event) {
        switch (event.getType()) {
        case INIT_APPLICATION_RESOURCES:
          Application app =
              ((ApplicationLocalizationEvent) event).getApplication();
          // Simulate event from ApplicationLocalization.
          dispatcher.getEventHandler().handle(new ApplicationInitedEvent(
                app.getAppId()));
          break;
        case INIT_CONTAINER_RESOURCES:
          ContainerLocalizationRequestEvent rsrcReqs =
            (ContainerLocalizationRequestEvent) event;
          // simulate localization of all requested resources
            for (Collection<LocalResourceRequest> rc : rsrcReqs
                .getRequestedResources().values()) {
              for (LocalResourceRequest req : rc) {
                LOG.info("DEBUG: " + req + ":"
                    + rsrcReqs.getContainer().getContainerID());
                dispatcher.getEventHandler().handle(
                    new ContainerResourceLocalizedEvent(rsrcReqs.getContainer()
                        .getContainerID(), req, new Path("file:///local"
                        + req.getPath().toUri().getPath())));
              }
            }
          break;
        case CLEANUP_CONTAINER_RESOURCES:
          Container container =
              ((ContainerLocalizationEvent) event).getContainer();
          // TODO: delete the container dir
          this.dispatcher.getEventHandler().handle(
              new ContainerEvent(container.getContainerID(),
                  ContainerEventType.CONTAINER_RESOURCES_CLEANEDUP));
          break;
        case DESTROY_APPLICATION_RESOURCES:
          Application application =
            ((ApplicationLocalizationEvent) event).getApplication();

          // decrement reference counts of all resources associated with this
          // app
          this.dispatcher.getEventHandler().handle(
              new ApplicationEvent(application.getAppId(),
                  ApplicationEventType.APPLICATION_RESOURCES_CLEANEDUP));
          break;
        default:
          fail("Unexpected event: " + event.getType());
        }
      }
    };
  }

  @Override
  protected ContainersLauncher createContainersLauncher(Context context,
      ContainerExecutor exec) {
    return new ContainersLauncher(context, super.dispatcher, exec) {
      @Override
      public void handle(ContainersLauncherEvent event) {
        Container container = event.getContainer();
        ContainerId containerId = container.getContainerID();
        switch (event.getType()) {
        case LAUNCH_CONTAINER:
          dispatcher.getEventHandler().handle(
              new ContainerEvent(containerId,
                  ContainerEventType.CONTAINER_LAUNCHED));
          break;
        case CLEANUP_CONTAINER:
          dispatcher.getEventHandler().handle(
              new ContainerExitEvent(containerId,
                  ContainerEventType.CONTAINER_KILLED_ON_REQUEST, 0));
          break;
        }
      }
    };
  }

  @Override
  protected LogAggregationService createLogAggregationService(
      DeletionService deletionService) {
    return new LogAggregationService(deletionService) {
      @Override
      public void handle(LogAggregatorEvent event) {
        switch (event.getType()) {
        case APPLICATION_STARTED:
          break;
        case CONTAINER_FINISHED:
          break;
        case APPLICATION_FINISHED:
          break;
        default:
          // Ignore
        }
      }
    };
  }
}
