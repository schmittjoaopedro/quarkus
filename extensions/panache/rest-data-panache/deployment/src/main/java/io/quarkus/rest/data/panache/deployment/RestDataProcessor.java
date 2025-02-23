package io.quarkus.rest.data.panache.deployment;

import java.util.Collections;
import java.util.List;

import io.quarkus.arc.deployment.GeneratedBeanBuildItem;
import io.quarkus.arc.deployment.GeneratedBeanGizmoAdaptor;
import io.quarkus.deployment.Capabilities;
import io.quarkus.deployment.Capability;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.CombinedIndexBuildItem;
import io.quarkus.deployment.builditem.nativeimage.RuntimeInitializedClassBuildItem;
import io.quarkus.gizmo.ClassOutput;
import io.quarkus.rest.data.panache.deployment.properties.ResourceProperties;
import io.quarkus.rest.data.panache.deployment.properties.ResourcePropertiesBuildItem;
import io.quarkus.rest.data.panache.deployment.properties.ResourcePropertiesProvider;
import io.quarkus.rest.data.panache.runtime.sort.SortQueryParamFilter;
import io.quarkus.rest.data.panache.runtime.sort.SortQueryParamValidator;
import io.quarkus.resteasy.common.spi.ResteasyJaxrsProviderBuildItem;
import io.quarkus.resteasy.reactive.spi.ContainerRequestFilterBuildItem;
import io.quarkus.resteasy.reactive.spi.GeneratedJaxRsResourceBuildItem;
import io.quarkus.resteasy.reactive.spi.GeneratedJaxRsResourceGizmoAdaptor;

public class RestDataProcessor {

    @BuildStep
    void supportingBuildItems(Capabilities capabilities,
            BuildProducer<RuntimeInitializedClassBuildItem> runtimeInitializedClassBuildItemBuildProducer,
            BuildProducer<ResteasyJaxrsProviderBuildItem> resteasyJaxrsProviderBuildItemBuildProducer,
            BuildProducer<ContainerRequestFilterBuildItem> containerRequestFilterBuildItemBuildProducer) {
        boolean isResteasyClassicAvailable = capabilities.isPresent(Capability.RESTEASY);
        boolean isResteasyReactiveAvailable = capabilities.isPresent(Capability.RESTEASY_REACTIVE);

        if (!isResteasyClassicAvailable && !isResteasyReactiveAvailable) {
            throw new IllegalStateException(
                    "REST Data Panache can only work if 'quarkus-resteasy' or 'quarkus-resteasy-reactive' is present");
        }

        if (isResteasyClassicAvailable) {
            runtimeInitializedClassBuildItemBuildProducer
                    .produce(new RuntimeInitializedClassBuildItem("org.jboss.resteasy.links.impl.EL"));
            resteasyJaxrsProviderBuildItemBuildProducer
                    .produce(new ResteasyJaxrsProviderBuildItem(SortQueryParamFilter.class.getName()));
        } else {
            containerRequestFilterBuildItemBuildProducer
                    .produce(new ContainerRequestFilterBuildItem.Builder(SortQueryParamFilter.class.getName())
                            .setNameBindingNames(Collections.singleton(SortQueryParamValidator.class.getName())).build());
        }
    }

    @BuildStep
    void implementResources(CombinedIndexBuildItem index, List<RestDataResourceBuildItem> resourceBuildItems,
            List<ResourcePropertiesBuildItem> resourcePropertiesBuildItems, Capabilities capabilities,
            BuildProducer<GeneratedBeanBuildItem> resteasyClassicImplementationsProducer,
            BuildProducer<GeneratedJaxRsResourceBuildItem> resteasyReactiveImplementationsProducer) {

        boolean isReactivePanache = capabilities.isPresent(Capability.HIBERNATE_REACTIVE);
        boolean isResteasyClassic = capabilities.isPresent(Capability.RESTEASY);

        if (isReactivePanache && isResteasyClassic) {
            throw new IllegalStateException(
                    "Reactive REST Data Panache does not work with 'quarkus-resteasy'. Only 'quarkus-resteasy-reactive' extensions are supported");
        }

        ClassOutput classOutput = isResteasyClassic ? new GeneratedBeanGizmoAdaptor(resteasyClassicImplementationsProducer)
                : new GeneratedJaxRsResourceGizmoAdaptor(resteasyReactiveImplementationsProducer);
        JaxRsResourceImplementor jaxRsResourceImplementor = new JaxRsResourceImplementor(hasValidatorCapability(capabilities),
                isResteasyClassic, isReactivePanache);
        ResourcePropertiesProvider resourcePropertiesProvider = new ResourcePropertiesProvider(index.getIndex());

        for (RestDataResourceBuildItem resourceBuildItem : resourceBuildItems) {
            ResourceMetadata resourceMetadata = resourceBuildItem.getResourceMetadata();
            ResourceProperties resourceProperties = getResourceProperties(resourcePropertiesProvider,
                    resourceMetadata, resourcePropertiesBuildItems);
            if (resourceProperties.isHal()) {
                if (isResteasyClassic && !hasAnyJsonCapabilityForResteasyClassic(capabilities)) {
                    throw new IllegalStateException("Cannot generate HAL endpoints without "
                            + "either 'quarkus-resteasy-jsonb' or 'quarkus-resteasy-jackson'");
                } else if (!isResteasyClassic && !hasAnyJsonCapabilityForResteasyReactive(capabilities)) {
                    throw new IllegalStateException("Cannot generate HAL endpoints without "
                            + "either 'quarkus-resteasy-reactive-jsonb' or 'quarkus-resteasy-reactive-jackson'");
                }

            }
            if (resourceProperties.isExposed()) {
                jaxRsResourceImplementor.implement(classOutput, resourceMetadata, resourceProperties, capabilities);
            }
        }
    }

    private ResourceProperties getResourceProperties(ResourcePropertiesProvider resourcePropertiesProvider,
            ResourceMetadata resourceMetadata, List<ResourcePropertiesBuildItem> resourcePropertiesBuildItems) {
        for (ResourcePropertiesBuildItem resourcePropertiesBuildItem : resourcePropertiesBuildItems) {
            if (resourcePropertiesBuildItem.getResourceType().equals(resourceMetadata.getResourceClass())
                    || resourcePropertiesBuildItem.getResourceType().equals(resourceMetadata.getResourceInterface())) {
                return resourcePropertiesBuildItem.getResourcePropertiesInfo();
            }
        }
        return resourcePropertiesProvider.getForInterface(resourceMetadata.getResourceInterface());
    }

    private boolean hasValidatorCapability(Capabilities capabilities) {
        return capabilities.isPresent(Capability.HIBERNATE_VALIDATOR);
    }

    private boolean hasAnyJsonCapabilityForResteasyClassic(Capabilities capabilities) {
        return capabilities.isPresent(Capability.RESTEASY_JSON_JSONB)
                || capabilities.isPresent(Capability.RESTEASY_JSON_JACKSON);
    }

    private boolean hasAnyJsonCapabilityForResteasyReactive(Capabilities capabilities) {
        return capabilities.isPresent(Capability.RESTEASY_REACTIVE_JSON_JSONB)
                || capabilities.isPresent(Capability.RESTEASY_REACTIVE_JSON_JACKSON);
    }
}
