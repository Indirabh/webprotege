package edu.stanford.bmir.protege.web.client.ui.portlet;

import com.google.common.base.Optional;
import com.google.gwt.core.client.GWT;
import com.google.gwt.core.client.JavaScriptObject;
import com.google.gwt.place.shared.PlaceChangeEvent;
import com.google.gwt.user.client.Timer;
import com.google.web.bindery.event.shared.Event;
import com.google.web.bindery.event.shared.HandlerRegistration;
import com.gwtext.client.core.EventObject;
import com.gwtext.client.core.ExtElement;
import com.gwtext.client.core.Function;
import com.gwtext.client.core.Position;
import com.gwtext.client.widgets.*;
import com.gwtext.client.widgets.event.ButtonListenerAdapter;
import com.gwtext.client.widgets.event.ComponentListener;
import com.gwtext.client.widgets.event.ResizableListenerAdapter;
import com.gwtext.client.widgets.form.Checkbox;
import com.gwtext.client.widgets.layout.AnchorLayoutData;
import com.gwtext.client.widgets.layout.FitLayout;
import com.gwtext.client.widgets.portal.Portlet;
import edu.stanford.bmir.protege.web.client.Application;
import edu.stanford.bmir.protege.web.client.events.UserLoggedInEvent;
import edu.stanford.bmir.protege.web.client.events.UserLoggedInHandler;
import edu.stanford.bmir.protege.web.client.events.UserLoggedOutEvent;
import edu.stanford.bmir.protege.web.client.events.UserLoggedOutHandler;
import edu.stanford.bmir.protege.web.client.project.Project;
import edu.stanford.bmir.protege.web.client.rpc.data.EntityData;
import edu.stanford.bmir.protege.web.client.rpc.data.PropertyEntityData;
import edu.stanford.bmir.protege.web.client.rpc.data.PropertyType;
import edu.stanford.bmir.protege.web.client.rpc.data.ValueType;
import edu.stanford.bmir.protege.web.client.rpc.data.layout.PortletConfiguration;
import edu.stanford.bmir.protege.web.client.ui.selection.SelectionEvent;
import edu.stanford.bmir.protege.web.client.ui.tab.AbstractTab;
import edu.stanford.bmir.protege.web.client.ui.util.AbstractValidatableTab;
import edu.stanford.bmir.protege.web.client.ui.util.ValidatableTab;
import edu.stanford.bmir.protege.web.shared.DataFactory;
import edu.stanford.bmir.protege.web.shared.entity.*;
import edu.stanford.bmir.protege.web.shared.event.EventBusManager;
import edu.stanford.bmir.protege.web.shared.event.HasEventHandlerManagement;
import edu.stanford.bmir.protege.web.shared.event.PermissionsChangedEvent;
import edu.stanford.bmir.protege.web.shared.event.PermissionsChangedHandler;
import edu.stanford.bmir.protege.web.shared.project.ProjectId;
import edu.stanford.bmir.protege.web.shared.selection.EntityDataSelectionChangedEvent;
import edu.stanford.bmir.protege.web.shared.selection.EntityDataSelectionChangedHandler;
import edu.stanford.bmir.protege.web.shared.selection.SelectionModel;
import edu.stanford.bmir.protege.web.shared.user.UserId;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Abstract class that should be extended by all portlets that should be
 * available in the user interface of a tab. This class takes care of the common
 * initializations for portlet (e.g. resizing, dragging, etc.) and the life
 * cycle of a portlet.
 * @author Tania Tudorache <tudorache@stanford.edu>
 * @deprecated Use {@link AbstractOWLEntityPortlet}.
 */
@Deprecated
public abstract class AbstractEntityPortlet extends Portlet implements EntityPortlet, HasEventHandlerManagement {

    private Project project;

    private AbstractTab tab;

    private PortletConfiguration portletConfiguration;

    private final SelectionModel selectionModel;

    private List<HandlerRegistration> handlerRegistrations = new ArrayList<>();

    public AbstractEntityPortlet(SelectionModel selectionModel, Project project) {
        this(selectionModel, project, true);
    }

    public AbstractEntityPortlet(SelectionModel selectionModel, Project project, boolean initialize) {
        super();
        this.project = project;
        this.selectionModel = selectionModel;

        setTitle(""); // very important
        setLayout(new FitLayout());

        ResizableConfig config = new ResizableConfig();
        config.setHandles(Resizable.SOUTH_EAST);

        final Resizable resizable = new Resizable(this, config);
        resizable.addListener(new ResizableListenerAdapter() {
            @Override
            public void onResize(Resizable self, int width, int height) {
                doOnResize(width, height);
            }
        });

        if (initialize) {
            setTools(getTools());
            initialize();
        }

        updateIcon(isControllingPortlet());

        addApplicationEventHandler(UserLoggedInEvent.TYPE, new UserLoggedInHandler() {
            @Override
            public void handleUserLoggedIn(UserLoggedInEvent event) {
                onLogin(event.getUserId());
            }
        });

        addApplicationEventHandler(UserLoggedOutEvent.TYPE, new UserLoggedOutHandler() {
            @Override
            public void handleUserLoggedOut(UserLoggedOutEvent event) {
                onLogout(event.getUserId());
            }
        });

        addProjectEventHandler(PermissionsChangedEvent.TYPE, new PermissionsChangedHandler() {
            @Override
            public void handlePersmissionsChanged(PermissionsChangedEvent event) {
                onPermissionsChanged();
            }
        });

        addApplicationEventHandler(PlaceChangeEvent.TYPE, new PlaceChangeEvent.Handler() {
            @Override
            public void onPlaceChange(PlaceChangeEvent event) {
                GWT.log("DETECTED PLACE CHANGE: COMMITTING CHANGES!");
                commitChanges();
            }
        });

        HandlerRegistration handlerRegistration = selectionModel.addSelectionChangedHandler(new EntityDataSelectionChangedHandler() {
            @Override
            public void handleSelectionChanged(EntityDataSelectionChangedEvent event) {
                boolean tabIsVisible = false;
                if(tab != null) {
                    tabIsVisible = tab.isVisible();
                    if (tabIsVisible) {
                        GWT.log("[" + tab.getLabel()+ ", " + AbstractEntityPortlet.this.getClass().getSimpleName() + "] Selection changed in selection model and tab is visible.  Updating portlet selection.");
                    }
                }
                if (tabIsVisible) {
                    handleBeforeSetEntity(event.getPreviousSelection());
                    handleAfterSetEntity(event.getLastSelection());
                }
            }
        });
        handlerRegistrations.add(handlerRegistration);
    }

    public SelectionModel getSelectionModel() {
        return selectionModel;
    }

    public void handleActivated() {
        handleAfterSetEntity(selectionModel.getSelection());
    }


    public ProjectId getProjectId() {
        return project.getProjectId();
    }

    public UserId getUserId() {
        return Application.get().getUserId();
    }

    public boolean hasWritePermission() {
        return getProject().hasWritePermission(Application.get().getUserId());
    }

    protected void doOnResize(int width, int height) {
        setWidth(width);
        setHeight(height);
        doLayout();
    }

    protected void handleBeforeSetEntity(Optional<OWLEntityData> existingEntity) {

    }

    protected void handleAfterSetEntity(Optional<OWLEntityData> entityData) {

    }

    public Project getProject() {
        return project;
    }

    /*
     * (non-Javadoc)
     *
     * @see edu.stanford.bmir.protege.web.client.ui.EntityPortlet#intialize()
     */
    public abstract void initialize();

    /*
     * Tools methods
     */
    protected Tool[] getTools() {
        // create some shared tools
        Tool refresh = new Tool(Tool.REFRESH, new Function() {
            public void execute() {
                onRefresh();
            }
        });
        Tool gear = new Tool(Tool.GEAR, new Function() {
            public void execute() {
                onConfigure();
            }
        });

        Tool close = new Tool(Tool.CLOSE, new Function() {
            public void execute() {
                onClose();
            }
        });

        List<Tool> toolList = new ArrayList<Tool>();
        if (hasRefreshButton()) {
            toolList.add(refresh);
        }
        toolList.add(gear);
        toolList.add(close);
        return toolList.toArray(new Tool[toolList.size()]);
    }

    protected boolean hasRefreshButton() {
        return true;
    }

    protected void onRefresh() {
    }

    public void commitChanges() {
    }

    protected void onConfigure() {
        final Window window = new Window();
        window.setSize(550, 250);
        window.setTitle("Configure");
        window.setLayout(new FitLayout());
        window.setClosable(true);
        window.setPaddings(7);
        window.setCloseAction(Window.CLOSE);
        final TabPanel configPanel = getConfigurationPanel();
        window.add(configPanel);
        window.setButtonAlign(Position.CENTER);

        window.addButton(new Button("OK", new ButtonListenerAdapter() {
            @Override
            public void onClick(Button button, EventObject e) {
                if (isValidConfiguration(configPanel)) {
                    saveConfiguration(configPanel);
                    window.close();
                }
                else {
                    MessageBox.alert("Invalid configuration", "The configuration is not valid. Please fix it, and try again");
                }
            }
        }));

        window.addButton(new Button("Cancel", new ButtonListenerAdapter() {
            @Override
            public void onClick(Button button, EventObject e) {
                window.close();
            }
        }));

        window.show();
    }

    protected boolean isValidConfiguration(TabPanel configPanel) {
        Component[] tabs = configPanel.getComponents();
        for (int i = 0; i < tabs.length; i++) {
            Component comp = tabs[i];
            if (comp instanceof ValidatableTab) {
                ValidatableTab tab = (ValidatableTab) comp;
                if (!tab.isValid()) {
                    return false;
                }
            }
        }
        return true;
    }

    protected void saveConfiguration(TabPanel configPanel) {
        Component[] tabs = configPanel.getComponents();
        for (int i = 0; i < tabs.length; i++) {
            Component comp = tabs[i];
            if (comp instanceof ValidatableTab) {
                ValidatableTab tab = (ValidatableTab) comp;
                tab.onSave();
            }
        }
    }

    protected TabPanel getConfigurationPanel() {
        TabPanel tabPanel = new TabPanel();
        tabPanel.add(createGeneralConfigPanel());
        return tabPanel;
    }

    protected Panel createGeneralConfigPanel() {
        final Checkbox isControllingPortletCheckbox = new Checkbox("Set as controlling portlet (the controlling portlet sets the selection for the entire tab)");
        isControllingPortletCheckbox.setChecked(isControllingPortlet());

        Panel generalPanel = new AbstractValidatableTab() {
            @Override
            public void onSave() {
                if (isControllingPortletCheckbox.getValue()) { //true - is checked
                    setAsControllingPortlet();
                }
                else {
                    if (isControllingPortlet()) {
                        AbstractTab tab = getTab();
                        if (tab == null) {
                            return;
                        }
                        tab.setControllingPortlet(null);
                    }
                }
            }

            @Override
            public boolean isValid() {
                return true;
            }
        };

        generalPanel.setTitle("General");
        generalPanel.setPaddings(10);

        generalPanel.add(isControllingPortletCheckbox, new AnchorLayoutData("100%"));
        return generalPanel;
    }


    public boolean isControllingPortlet() {
        AbstractTab tab = getTab();
        if (tab == null) {
            return false;
        }
        return this.equals(tab.getControllingPortlet());
    }

    public void setAsControllingPortlet() {
        AbstractTab tab = getTab();
        if (tab == null) {
            return;
        }
        EntityPortlet oldCtrlPortlet = tab.getControllingPortlet();
        if (!(this.equals(oldCtrlPortlet))) {
            tab.setControllingPortlet(this);
        }
    }

    public void updateIcon(boolean isControlling) {
        setIconCls(isControlling ? "ctrl_portlet" : null);
        if (isRendered() && !isControlling) {
            ExtElement header = this.getHeader();
            if (header != null) {
                header.removeClass("x-panel-icon");
            }
        }
    }

    protected void onClose() {
        commitChanges();
        this.hide();
        this.destroy();
    }

    protected void onLogin(UserId userId) {
    }

    protected void onLogout(UserId userId) {
    }

    public void onPermissionsChanged() {
    }

    /**
     * Does not do anything to the UI, only stores the new configuration in this
     * portlet
     * @param portletConfiguration
     */
    public void setPortletConfiguration(PortletConfiguration portletConfiguration) {
        this.portletConfiguration = portletConfiguration;
    }

    public PortletConfiguration getPortletConfiguration() {
        return portletConfiguration;
    }

    public AbstractTab getTab() {
        return tab;
    }

    public void setTab(AbstractTab tab) {
        this.tab = tab;
    }


    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////



    /**
     * Adds an event handler to the main event bus.  When the portlet is destroyed the handler will automatically be
     * removed.
     * @param type The type of event to listen for.  Not {@code null}.
     * @param handler The handler for the event type. Not {@code null}.
     * @param <T> The event type
     * @throws NullPointerException if any parameters are {@code null}.
     */
    public <T> void addProjectEventHandler(Event.Type<T> type, T handler) {
        final EventBusManager manager = EventBusManager.getManager();
        HandlerRegistration reg = manager.registerHandlerToProject(getProjectId(), checkNotNull(type), checkNotNull(handler));
        handlerRegistrations.add(reg);
    }

    public <T> void addApplicationEventHandler(Event.Type<T> type, T handler) {
        EventBusManager manager = EventBusManager.getManager();
        HandlerRegistration reg = manager.registerHandler(checkNotNull(type), checkNotNull(handler));
        handlerRegistrations.add(reg);
    }


    public boolean isEventForThisProject(Event<?> event) {
        Object source = event.getSource();
        return source instanceof ProjectId && source.equals(getProjectId());
    }

    @Override
    public void destroy() {
        removeHandlers();
        super.destroy();
    }

    private void removeHandlers() {
        GWT.log("Removing handlers for portlet " + this);
        for (HandlerRegistration reg : handlerRegistrations) {
            reg.removeHandler();
        }

    }

    public Optional<OWLEntityData> getSelectedEntityData() {
        return getSelectionModel().getSelection();
    }


}
