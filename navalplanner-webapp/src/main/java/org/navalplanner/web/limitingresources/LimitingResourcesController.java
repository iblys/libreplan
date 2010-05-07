/*
 * This file is part of NavalPlan
 *
 * Copyright (C) 2009 Fundación para o Fomento da Calidade Industrial e
 *                    Desenvolvemento Tecnolóxico de Galicia
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.navalplanner.web.limitingresources;

import static org.navalplanner.web.I18nHelper._;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import org.apache.commons.lang.Validate;
import org.jfree.util.Log;
import org.navalplanner.business.orders.entities.Order;
import org.navalplanner.business.planner.entities.GenericResourceAllocation;
import org.navalplanner.business.planner.entities.LimitingResourceQueueElement;
import org.navalplanner.business.planner.entities.Task;
import org.navalplanner.web.common.Util;
import org.navalplanner.web.limitingresources.LimitingResourcesPanel.IToolbarCommand;
import org.navalplanner.web.planner.order.BankHolidaysMarker;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import org.zkoss.ganttz.resourceload.IFilterChangedListener;
import org.zkoss.ganttz.timetracker.TimeTracker;
import org.zkoss.ganttz.timetracker.zoom.SeveralModificators;
import org.zkoss.zk.ui.event.Event;
import org.zkoss.zk.ui.event.EventListener;
import org.zkoss.zk.ui.event.Events;
import org.zkoss.zk.ui.util.Composer;
import org.zkoss.zul.Button;
import org.zkoss.zul.Checkbox;
import org.zkoss.zul.Grid;
import org.zkoss.zul.Label;
import org.zkoss.zul.Messagebox;
import org.zkoss.zul.Row;
import org.zkoss.zul.RowRenderer;

/**
 * Controller for limiting resources view
 * @author Lorenzo Tilve Álvaro <ltilve@igalia.com>
 */
@Component
@Scope(BeanDefinition.SCOPE_PROTOTYPE)
public class LimitingResourcesController implements Composer {

    @Autowired
    private ILimitingResourceQueueModel limitingResourceQueueModel;

    private List<IToolbarCommand> commands = new ArrayList<IToolbarCommand>();

    private Order filterBy;

    private org.zkoss.zk.ui.Component parent;

    private LimitingResourcesPanel limitingResourcesPanel;

    private TimeTracker timeTracker;

    private Grid gridUnassignedLimitingResourceQueueElements;

    private final LimitingResourceQueueElementsRenderer limitingResourceQueueElementsRenderer =
        new LimitingResourceQueueElementsRenderer();

    private transient IFilterChangedListener filterChangedListener;

    public LimitingResourcesController() {
    }

    public void add(IToolbarCommand... commands) {
        Validate.noNullElements(commands);
        this.commands.addAll(Arrays.asList(commands));
    }

    @Override
    public void doAfterCompose(org.zkoss.zk.ui.Component comp) throws Exception {
        this.parent = comp;
        reload();
    }

    public void reload() {
        // by default show the task by resources
        boolean filterByResources = true;
        reload(filterByResources);
    }

    private void reload(boolean filterByResources) {
        try {
            if (filterBy == null) {
                limitingResourceQueueModel.initGlobalView(filterByResources);
            } else {
                limitingResourceQueueModel.initGlobalView(filterBy,
                        filterByResources);
            }
            timeTracker = buildTimeTracker();
            limitingResourcesPanel = buildLimitingResourcesPanel();
            addListeners();

            this.parent.getChildren().clear();
            this.parent.appendChild(limitingResourcesPanel);
            limitingResourcesPanel.afterCompose();
            gridUnassignedLimitingResourceQueueElements = (Grid) limitingResourcesPanel
                    .getFellowIfAny("gridUnassignedLimitingResourceQueueElements");
            addCommands(limitingResourcesPanel);
        } catch (IllegalArgumentException e) {
            try {
                e.printStackTrace();
                Messagebox.show(_("Limiting resources error") + e, _("Error"),
                        Messagebox.OK, Messagebox.ERROR);
            } catch (InterruptedException o) {
                throw new RuntimeException(e);
            }
        }
    }

    private void addListeners() {
        filterChangedListener = new IFilterChangedListener() {

            @Override
            public void filterChanged(boolean filter) {
                onApplyFilter(filter);
            }
        };
        // this.limitingResourcesPanel.addFilterListener(filterChangedListener);
    }

    public void onApplyFilter(boolean filterByResources) {
        limitingResourcesPanel.clearComponents();
        reload(filterByResources);
    }

    private void addCommands(LimitingResourcesPanel limitingResourcesPanel) {
        limitingResourcesPanel.add(commands.toArray(new IToolbarCommand[0]));
    }

    private TimeTracker buildTimeTracker() {
        return timeTracker = new TimeTracker(limitingResourceQueueModel
                .getViewInterval(), limitingResourceQueueModel
                .calculateInitialZoomLevel(), SeveralModificators.create(),
                SeveralModificators.create(new BankHolidaysMarker()), parent);
    }

    private void updateLimitingResourceQueues() {
        limitingResourcesPanel
                .resetLimitingResourceQueues(limitingResourceQueueModel
                        .getLimitingResourceQueues());
        limitingResourcesPanel.reloadLimitingResourcesList();
    }

    private LimitingResourcesPanel buildLimitingResourcesPanel() {
        LimitingResourcesPanel result = new LimitingResourcesPanel(
                limitingResourceQueueModel.getLimitingResourceQueues(),
                timeTracker);
        result.setVariable("limitingResourcesController", this, true);
        return result;
    }

    /**
     * Returns unassigned {@link LimitingResourceQueueElement}
     *
     * It's necessary to convert elements to a DTO that encapsulates properties
     * such as task name or order name, since the only way of sorting by these
     * fields is by having properties getTaskName or getOrderName on the
     * elements returned
     *
     * @return
     */
    public List<LimitingResourceQueueElementDTO> getUnassignedLimitingResourceQueueElements() {
        List<LimitingResourceQueueElementDTO> result = new ArrayList<LimitingResourceQueueElementDTO>();
        for (LimitingResourceQueueElement each : limitingResourceQueueModel
                .getUnassignedLimitingResourceQueueElements()) {
            result.add(toUnassignedLimitingResourceQueueElementDTO(each));
        }
        return result;
    }

    private LimitingResourceQueueElementDTO toUnassignedLimitingResourceQueueElementDTO(
            LimitingResourceQueueElement element) {
        final Task task = element.getResourceAllocation().getTask();
        final Order order = limitingResourceQueueModel.getOrderByTask(task);
        return new LimitingResourceQueueElementDTO(element, order
                .getName(), task.getName(), element
                .getEarlierStartDateBecauseOfGantt());
    }

    /**
     * DTO for list of unassigned {@link LimitingResourceQueueElement}
     *
     * @author Diego Pino Garcia <dpino@igalia.com>
     *
     */
    public class LimitingResourceQueueElementDTO {

        private final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("dd/MM/yyyy");

        private LimitingResourceQueueElement original;

        private String orderName;

        private String taskName;

        private String date;

        public LimitingResourceQueueElementDTO(
                LimitingResourceQueueElement element, String orderName,
                String taskName, Date date) {
            this.original = element;
            this.orderName = orderName;
            this.taskName = taskName;
            this.date = DATE_FORMAT.format(date);
        }

        public LimitingResourceQueueElement getOriginal() {
            return original;
        }

        public String getOrderName() {
            return orderName;
        }

        public String getTaskName() {
            return taskName;
        }

        public String getDate() {
            return date;
        }

    }

    public void filterBy(Order order) {
        this.filterBy = order;
    }

    public void saveQueues() {
        limitingResourceQueueModel.confirm();
        notifyUserThatSavingIsDone();
    }

    private void notifyUserThatSavingIsDone() {
        try {
            Messagebox.show(_("Scheduling saved"), _("Information"), Messagebox.OK,
                    Messagebox.INFORMATION);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    public LimitingResourceQueueElementsRenderer getLimitingResourceQueueElementsRenderer() {
        return limitingResourceQueueElementsRenderer;
    }

    private class LimitingResourceQueueElementsRenderer implements RowRenderer {

        @Override
        public void render(Row row, Object data) throws Exception {
            LimitingResourceQueueElementDTO element = (LimitingResourceQueueElementDTO) data;

            row.appendChild(label(element.getTaskName()));
            row.appendChild(label(element.getOrderName()));
            row.appendChild(label(element.getDate()));
            row.appendChild(assignButton(element));
            row.appendChild(automaticQueueing(element));
        }

        private Button assignButton(
                final LimitingResourceQueueElementDTO element) {
            Button result = new Button();
            result.setLabel(_("Assign"));
            result.setTooltiptext(_("Assign to queue"));
            result.addEventListener(Events.ON_CLICK, new EventListener() {

                @Override
                public void onEvent(Event event) throws Exception {
                    assignLimitingResourceQueueElement(element);
                }
            });
            return result;
        }

        private void assignLimitingResourceQueueElement(
                LimitingResourceQueueElementDTO dto) {

            LimitingResourceQueueElement element = dto.getOriginal();
            if (element.getResourceAllocation() instanceof GenericResourceAllocation) {
                // TODO: Generic resources allocation
                Log.error("Allocation of generic resources is not supported yet");
                return;
            }
            limitingResourceQueueModel
                    .assignLimitingResourceQueueElement(element);
            Util.reloadBindings(gridUnassignedLimitingResourceQueueElements);
            updateLimitingResourceQueues();
        }

        private Checkbox automaticQueueing(
                final LimitingResourceQueueElementDTO element) {
            Checkbox result = new Checkbox();
            result.setTooltiptext(_("Select for automatic queuing"));
            return result;
        }

        private Label label(String value) {
            return new Label(value);
        }

    }

}
