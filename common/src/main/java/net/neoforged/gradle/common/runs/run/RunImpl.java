package net.neoforged.gradle.common.runs.run;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import net.minecraftforge.gdi.ConfigurableDSLElement;
import net.neoforged.gradle.common.util.ProjectUtils;
import net.neoforged.gradle.common.util.constants.RunsConstants;
import net.neoforged.gradle.dsl.common.runs.run.Run;
import net.neoforged.gradle.dsl.common.runs.type.RunType;
import org.gradle.api.Action;
import org.gradle.api.NamedDomainObjectContainer;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.TaskProvider;
import org.jetbrains.annotations.NotNull;

import javax.inject.Inject;
import java.util.*;

public abstract class RunImpl implements ConfigurableDSLElement<Run>, Run {

    private final Project project;
    private final String name;

    private ListProperty<String> jvmArguments;
    private MapProperty<String, String> environmentVariables;
    private ListProperty<String> programArguments;
    private MapProperty<String, String> systemProperties;

    private final Set<TaskProvider<? extends Task>> dependencies = Sets.newHashSet();

    @Inject
    public RunImpl(final Project project, final String name) {
        this.project = project;
        this.name = name;

        this.jvmArguments = this.project.getObjects().listProperty(String.class);
        this.environmentVariables = this.project.getObjects().mapProperty(String.class, String.class);
        this.programArguments = this.project.getObjects().listProperty(String.class);
        this.systemProperties = this.project.getObjects().mapProperty(String.class, String.class);

        getIsSingleInstance().convention(true);
        getIsClient().convention(false);
        getIsServer().convention(false);
        getIsDataGenerator().convention(false);
        getIsGameTest().convention(false);
        getShouldBuildAllProjects().convention(false);
        getDependencies().convention(project.getObjects().newInstance(DependencyHandlerImpl.class, project));

        getConfigureAutomatically().convention(true);
        getConfigureFromTypeWithName().convention(getConfigureAutomatically());
        getConfigureFromDependencies().convention(getConfigureAutomatically());
        
        getWorkingDirectory().convention(project.getLayout().getProjectDirectory().dir("runs").dir(getName()));
    }

    @Override
    public Project getProject() {
        return project;
    }
    @Override
    public final String getName() {
        return name;
    }

    @Override
    public MapProperty<String, String> getEnvironmentVariables() {
        return environmentVariables;
    }

    public void overrideEnvironmentVariables(MapProperty<String, String> environmentVariables) {
        this.environmentVariables = environmentVariables;
    }

    @Override
    public abstract Property<String> getMainClass();

    @Override
    public abstract Property<Boolean> getShouldBuildAllProjects();

    @Override
    public ListProperty<String> getProgramArguments() {
        return programArguments;
    }

    public void overrideProgramArguments(ListProperty<String> programArguments) {
        this.programArguments = programArguments;
    }

    @Override
    public ListProperty<String> getJvmArguments() {
        return jvmArguments;
    }

    public void overrideJvmArguments(final ListProperty<String> args) {
        this.jvmArguments = args;
    }

    @Override
    public MapProperty<String, String> getSystemProperties() {
        return systemProperties;
    }

    public void overrideSystemProperties(MapProperty<String, String> systemProperties) {
        this.systemProperties = systemProperties;
    }

    @Internal
    public Set<TaskProvider<? extends Task>> getTaskDependencies() {
        return ImmutableSet.copyOf(this.dependencies);
    }

    @Override
    public final void configure() {
        configure(getName());
    }

    @Override
    public final void configure(final @NotNull String name) {
        getConfigureFromTypeWithName().set(false); // Don't re-configure
        ProjectUtils.afterEvaluate(getProject(), () -> {
            project.getExtensions().configure(RunsConstants.Extensions.RUN_TYPES, (Action<NamedDomainObjectContainer<RunType>>) types -> {
                if (types.getNames().contains(name)) {
                    configureInternally(types.getByName(name));
                }
            });
        });
    }

    @Override
    public final void configure(final @NotNull RunType runType) {
        getConfigureFromTypeWithName().set(false); // Don't re-configure
        ProjectUtils.afterEvaluate(getProject(), () -> {
            configureInternally(runType);
        });
    }

    @Override
    public void configure(@NotNull Provider<RunType> typeProvider) {
        configure(typeProvider.get());
    }

    @SafeVarargs
    @Override
    public final void dependsOn(TaskProvider<? extends Task>... tasks) {
        this.dependencies.addAll(Arrays.asList(tasks));
    }

    public void configureInternally(final @NotNull RunType spec) {
        getEnvironmentVariables().putAll(spec.getEnvironmentVariables());
        getMainClass().convention(spec.getMainClass());
        getProgramArguments().addAll(spec.getArguments());
        getJvmArguments().addAll(spec.getJvmArguments());
        getIsSingleInstance().convention(spec.getIsSingleInstance());
        getSystemProperties().putAll(spec.getSystemProperties());
        getIsClient().convention(spec.getIsClient());
        getIsServer().convention(spec.getIsServer());
        getIsDataGenerator().convention(spec.getIsDataGenerator());
        getIsGameTest().convention(spec.getIsGameTest());
        getClasspath().from(spec.getClasspath());

        if (spec.getRunAdapter().isPresent()) {
            spec.getRunAdapter().get().adapt(this);
        }
    }

    @NotNull
    public List<String> realiseJvmArguments() {
        final List<String> args = new ArrayList<>(getJvmArguments().get());

        getSystemProperties().get().forEach((key, value) -> {
            args.add(String.format("-D%s=%s", key, value));
        });

        return args;
    }
}
