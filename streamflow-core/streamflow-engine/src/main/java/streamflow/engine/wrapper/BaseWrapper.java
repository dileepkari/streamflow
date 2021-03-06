package streamflow.engine.wrapper;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Module;
import java.io.Serializable;
import java.util.ArrayList;
import streamflow.engine.framework.FrameworkException;
import streamflow.engine.framework.FrameworkLoader;
import streamflow.engine.framework.FrameworkModule;
import streamflow.engine.resource.ResourceModule;
import streamflow.model.Topology;
import streamflow.model.TopologyComponent;
import streamflow.model.TopologyResourceEntry;
import streamflow.model.config.StreamflowConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class BaseWrapper<T> implements Serializable {
    
    private static final Logger LOG = LoggerFactory.getLogger(BaseWrapper.class);
    
    protected transient T delegate;
    
    protected Class<T> typeClass;

    protected Topology topology;
    
    protected TopologyComponent component;
    
    protected boolean isCluster;
    
    protected StreamflowConfig configuration;
    
    public BaseWrapper() {
    }

    public BaseWrapper(Topology topology, TopologyComponent component, boolean isCluster, 
            StreamflowConfig configuration, Class<T> typeClass) throws FrameworkException {
        this.topology = topology;
        this.component = component;
        this.isCluster = isCluster;
        this.configuration = configuration;
        this.typeClass = typeClass;
    }
    
    protected T getDelegate() throws FrameworkException {
        if (delegate == null) {
            try {
                // Load the delegate class from the framework jar in an isolated class loader
                delegate = FrameworkLoader.getInstance().loadFrameworkComponent(
                        topology.getProjectId(), component.getFramework(), 
                        component.getMainClass(), typeClass);
                
                ClassLoader loader = FrameworkLoader.getInstance()
                    .loadRealm(component.getFramework());
                
                Thread.currentThread().setContextClassLoader(loader);
                
                injectModules();
                
            } catch (Exception ex) {
                throw new FrameworkException("Unable to locate component class: "
                    + component.getMainClass());
            }
        }
        return delegate;
    }

    private void injectModules() throws FrameworkException {
        // Create the new FrameworkModule to inject proxy and property information
        FrameworkModule frameworkModule = new FrameworkModule(
                topology, component, isCluster, configuration);

        // Create the resource module which will inject resource properties
        ResourceModule resourceModule = new ResourceModule(
                topology, component.getResources());

        // Create the Guice injector and use it to inject the modules
        Injector injector = Guice.createInjector(
                (Module) frameworkModule, (Module) resourceModule);

        ArrayList<Module> resourceModules = new ArrayList<Module>();

        for (TopologyResourceEntry resourceEntry : component.getResources()) {
            // Load the framework class instance from the framework
            Class resourceClass = FrameworkLoader.getInstance().loadFrameworkClass(
                    topology.getProjectId(), resourceEntry.getFramework(), 
                    resourceEntry.getResourceClass());

            // Create an instance of each resource module save it for injection
            resourceModules.add((Module) injector.getInstance(resourceClass));
        }

        // Create a child injector with all of the bound resource module implementations
        injector = injector.createChildInjector(resourceModules);

        // Finally inject the delegate object with all of the resource bound settings
        injector.injectMembers(delegate);
    }
}
