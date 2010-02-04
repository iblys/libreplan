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

package org.navalplanner.business.planner.entities;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.commons.lang.Validate;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.hibernate.validator.AssertTrue;
import org.hibernate.validator.Valid;
import org.joda.time.DateTime;
import org.joda.time.Days;
import org.joda.time.LocalDate;
import org.navalplanner.business.orders.entities.AggregatedHoursGroup;
import org.navalplanner.business.orders.entities.HoursGroup;
import org.navalplanner.business.orders.entities.OrderElement;
import org.navalplanner.business.orders.entities.TaskSource;
import org.navalplanner.business.planner.entities.DerivedAllocationGenerator.IWorkerFinder;
import org.navalplanner.business.planner.entities.allocationalgorithms.HoursModification;
import org.navalplanner.business.planner.entities.allocationalgorithms.ResourcesPerDayModification;
import org.navalplanner.business.resources.daos.IResourceDAO;
import org.navalplanner.business.resources.entities.Criterion;
import org.navalplanner.business.resources.entities.Resource;
import org.navalplanner.business.resources.entities.Worker;

/**
 * @author Óscar González Fernández <ogonzalez@igalia.com>
 */
public class Task extends TaskElement {

    private static final Log LOG = LogFactory.getLog(Task.class);

    public static Task createTask(TaskSource taskSource) {
        Task task = new Task();
        OrderElement orderElement = taskSource.getOrderElement();
        orderElement.applyStartConstraintIfNeededTo(task);
        Task result = create(task, taskSource);
        result.initializeEndDate();
        return result;
    }

    @Override
    protected void initializeEndDate() {
        Integer workHours = getWorkHours();
        long endDateTime = getStartDate().getTime()
                + (workHours * 3600l * 1000);
        setEndDate(new Date(endDateTime));
    }

    private CalculatedValue calculatedValue = CalculatedValue.END_DATE;

    private Set<ResourceAllocation<?>> resourceAllocations = new HashSet<ResourceAllocation<?>>();

    private TaskStartConstraint startConstraint = new TaskStartConstraint();

    @Valid
    private SubcontractedTaskData subcontractedTaskData;

    /**
     * Constructor for hibernate. Do not use!
     */
    public Task() {

    }

    @SuppressWarnings("unused")
    @AssertTrue(message = "order element associated to a task must be not null")
    private boolean theOrderElementMustBeNotNull() {
        return getOrderElement() != null;
    }


    public HoursGroup getHoursGroup() {
        return getTaskSource().getHoursGroups().iterator().next();
    }

    public Set<Criterion> getCriterions() {
        return Collections
                .unmodifiableSet(getHoursGroup().getValidCriterions());
    }

    public Integer getHoursSpecifiedAtOrder() {
        return getWorkHours();
    }

    public int getAssignedHours() {
        return new AggregateOfResourceAllocations(resourceAllocations)
                .getTotalHours();
    }

    @Override
    public boolean isLeaf() {
        return true;
    }

    @Override
    public List<TaskElement> getChildren() {
        throw new UnsupportedOperationException();
    }

    public Set<ResourceAllocation<?>> getResourceAllocations() {
        return Collections.unmodifiableSet(filterEmpty(resourceAllocations));
    }

    private Set<ResourceAllocation<?>> filterEmpty(
            Set<ResourceAllocation<?>> allocations) {
        Set<ResourceAllocation<?>> result = new HashSet<ResourceAllocation<?>>();
        for (ResourceAllocation<?> each : allocations) {
            if (each.hasAssignments()) {
                result.add(each);
            } else {
                LOG.warn("there is an empty resource allocation: "
                        + each.toString() + " ,on task: " + this);
            }
        }
        return result;
    }

    public void addResourceAllocation(ResourceAllocation<?> resourceAllocation) {
        if (!resourceAllocation.getTask().equals(this)) {
            throw new IllegalArgumentException(
                    "the resourceAllocation's task must be this task");
        }
        if (resourceAllocation.hasAssignments()) {
            resourceAllocations.add(resourceAllocation);
            resourceAllocation.associateAssignmentsToResource();
        } else {
            LOG.warn("adding a resource allocation without assignments: "
                    + resourceAllocation);
        }
    }

    public void removeResourceAllocation(
            ResourceAllocation<?> resourceAllocation) {
        resourceAllocation.detach();
        resourceAllocations.remove(resourceAllocation);
    }

    public CalculatedValue getCalculatedValue() {
        if (calculatedValue == null) {
            return CalculatedValue.END_DATE;
        }
        return calculatedValue;
    }

    public void setCalculatedValue(CalculatedValue calculatedValue) {
        Validate.notNull(calculatedValue);
        this.calculatedValue = calculatedValue;
    }

    public void setDaysDuration(Integer duration) {
        Validate.notNull(duration);
        Validate.isTrue(duration >= 0);
        DateTime endDate = toDateTime(getStartDate()).plusDays(duration);
        setEndDate(endDate.toDate());
    }

    public Integer getDaysDuration() {
        Days daysBetween = Days.daysBetween(new LocalDate(
                toDateTime(getStartDate())), new LocalDate(
                toDateTime(getEndDate())));
        return daysBetween.getDays();
    }

    private DateTime toDateTime(Date startDate) {
        return new DateTime(startDate.getTime());
    }

    /**
     * Checks if there isn't any {@link Worker} repeated in the {@link Set} of
     * {@link ResourceAllocation} of this {@link Task}.
     * @return <code>true</code> if the {@link Task} is valid, that means there
     *         isn't any {@link Worker} repeated.
     */
    public boolean isValidResourceAllocationWorkers() {
        Set<Long> workers = new HashSet<Long>();

        for (ResourceAllocation<?> resourceAllocation : resourceAllocations) {
            if (resourceAllocation instanceof SpecificResourceAllocation) {
                Resource resource = ((SpecificResourceAllocation) resourceAllocation)
                        .getResource();
                if (resource != null) {
                    if (workers.contains(resource.getId())) {
                        return false;
                    } else {
                        workers.add(resource.getId());
                    }
                }
            }
        }

        return true;
    }

    public Set<GenericResourceAllocation> getGenericResourceAllocations() {
        return new HashSet<GenericResourceAllocation>(ResourceAllocation
                .getOfType(GenericResourceAllocation.class,
                        getResourceAllocations()));
    }

    public Set<SpecificResourceAllocation> getSpecificResourceAllocations() {
        return new HashSet<SpecificResourceAllocation>(ResourceAllocation
                .getOfType(SpecificResourceAllocation.class,
                        getResourceAllocations()));
    }

    public static class ModifiedAllocation {

        public static List<ModifiedAllocation> copy(
                Collection<ResourceAllocation<?>> resourceAllocations) {
            List<ModifiedAllocation> result = new ArrayList<ModifiedAllocation>();
            for (ResourceAllocation<?> resourceAllocation : resourceAllocations) {
                result.add(new ModifiedAllocation(resourceAllocation,
                        resourceAllocation.copy()));
            }
            return result;
        }

        public static List<ResourceAllocation<?>> modified(
                Collection<ModifiedAllocation> collection) {
            List<ResourceAllocation<?>> result = new ArrayList<ResourceAllocation<?>>();
            for (ModifiedAllocation modifiedAllocation : collection) {
                result.add(modifiedAllocation.getModification());
            }
            return result;
        }

        private final ResourceAllocation<?> original;

        private final ResourceAllocation<?> modification;

        public ModifiedAllocation(ResourceAllocation<?> original,
                ResourceAllocation<?> modification) {
            Validate.notNull(original);
            Validate.notNull(modification);
            this.original = original;
            this.modification = modification;
        }

        public ResourceAllocation<?> getOriginal() {
            return original;
        }

        public ResourceAllocation<?> getModification() {
            return modification;
        }

    }

    public void mergeAllocation(CalculatedValue calculatedValue,
            AggregateOfResourceAllocations aggregate,
            List<ResourceAllocation<?>> newAllocations,
            List<ModifiedAllocation> modifications,
            Collection<? extends ResourceAllocation<?>> toRemove) {
        if (aggregate.isEmpty()) {
            return;
        }
        final LocalDate start = aggregate.getStart();
        final LocalDate end = aggregate.getEnd();
        mergeAllocation(start, end, calculatedValue, newAllocations,
                modifications, toRemove);
    }

    private void mergeAllocation(final LocalDate start, final LocalDate end,
            CalculatedValue calculatedValue,
            List<ResourceAllocation<?>> newAllocations,
            List<ModifiedAllocation> modifications,
            Collection<? extends ResourceAllocation<?>> toRemove) {
        this.calculatedValue = calculatedValue;
        setStartDate(start.toDateTimeAtStartOfDay().toDate());
        setDaysDuration(Days.daysBetween(start, end).getDays());
        for (ModifiedAllocation pair : modifications) {
            Validate.isTrue(resourceAllocations.contains(pair.getOriginal()));
            pair.getOriginal().mergeAssignmentsAndResourcesPerDay(
                    pair.getModification());
        }
        remove(toRemove);
        addAllocations(newAllocations);
    }

    private void remove(Collection<? extends ResourceAllocation<?>> toRemove) {
        for (ResourceAllocation<?> resourceAllocation : toRemove) {
            removeResourceAllocation(resourceAllocation);
        }
    }

    private void addAllocations(List<ResourceAllocation<?>> newAllocations) {
        for (ResourceAllocation<?> resourceAllocation : newAllocations) {
            addResourceAllocation(resourceAllocation);
        }
    }

    public void explicityMoved(Date date) {
        getStartConstraint().explicityMovedTo(date);
    }

    public TaskStartConstraint getStartConstraint() {
        if (startConstraint == null) {
            startConstraint = new TaskStartConstraint();
        }
        return startConstraint;
    }

    private static abstract class AllocationModificationStrategy {

        public abstract List<ResourcesPerDayModification> getResourcesPerDayModified(
                List<ResourceAllocation<?>> allocations);

        public abstract List<HoursModification> getHoursModified(
                List<ResourceAllocation<?>> allocations);

    }

    private static class WithTheSameHoursAndResourcesPerDay extends
            AllocationModificationStrategy {

        @Override
        public List<HoursModification> getHoursModified(
                List<ResourceAllocation<?>> allocations) {
            return HoursModification.fromExistent(allocations);
        }

        @Override
        public List<ResourcesPerDayModification> getResourcesPerDayModified(
                List<ResourceAllocation<?>> allocations) {
            return ResourcesPerDayModification.fromExistent(allocations);
        }

    }

    private static class WithAnotherResources extends
            AllocationModificationStrategy {
        private final IResourceDAO resourceDAO;

        WithAnotherResources(IResourceDAO resourceDAO) {
            this.resourceDAO = resourceDAO;
        }

        @Override
        public List<HoursModification> getHoursModified(
                List<ResourceAllocation<?>> allocations) {
            return HoursModification.withNewResources(allocations, resourceDAO);
        }

        @Override
        public List<ResourcesPerDayModification> getResourcesPerDayModified(
                List<ResourceAllocation<?>> allocations) {
            return ResourcesPerDayModification.withNewResources(allocations,
                    resourceDAO);
        }
    }

    @Override
    protected void moveAllocations() {
        reassign(new WithTheSameHoursAndResourcesPerDay());
    }

    public void reassignAllocationsWithNewResources(IResourceDAO resourceDAO) {
        reassign(new WithAnotherResources(resourceDAO));
    }

    private void reassign(AllocationModificationStrategy strategy) {
        List<ModifiedAllocation> copied = ModifiedAllocation
                .copy(getResourceAllocations());
        List<ResourceAllocation<?>> toBeModified = ModifiedAllocation
                .modified(copied);
        List<ResourcesPerDayModification> allocations = strategy
                .getResourcesPerDayModified(toBeModified);
        if (allocations.isEmpty()) {
            return;
        }
        switch (calculatedValue) {
        case NUMBER_OF_HOURS:
            ResourceAllocation.allocating(allocations)
                              .allocateOnTaskLength();
            break;
        case END_DATE:
            LocalDate end = ResourceAllocation
                                .allocating(allocations)
                                .untilAllocating(getAssignedHours());
            setEndDate(end.toDateTimeAtStartOfDay().toDate());
            break;
        case RESOURCES_PER_DAY:
            List<HoursModification> hoursModified = strategy
                    .getHoursModified(toBeModified);
            ResourceAllocation.allocatingHours(hoursModified)
                              .allocateUntil(new LocalDate(getEndDate()));
            break;
        default:
            throw new RuntimeException("cant handle: " + calculatedValue);
        }
        updateDerived(copied);
        mergeAllocation(asLocalDate(getStartDate()), asLocalDate(getEndDate()),
                calculatedValue, Collections
                        .<ResourceAllocation<?>> emptyList(), copied,
                Collections.<ResourceAllocation<?>> emptyList());
    }

    private void updateDerived(List<ModifiedAllocation> allocations) {
        for (ModifiedAllocation each : allocations) {
            ResourceAllocation<?> original = each.getOriginal();
            if (!original.getDerivedAllocations().isEmpty()) {
                IWorkerFinder workersFinder = createFromExistentDerivedAllocationsFinder(original);
                each.getModification().createDerived(workersFinder);
            }
        }
    }

    private IWorkerFinder createFromExistentDerivedAllocationsFinder(
            ResourceAllocation<?> original) {
        Set<DerivedAllocation> derivedAllocations = original
                .getDerivedAllocations();
        final Set<Worker> allWorkers = new HashSet<Worker>();
        for (DerivedAllocation each : derivedAllocations) {
            allWorkers.addAll(Resource.workers(each.getResources()));
        }
        return new IWorkerFinder() {

            @Override
            public Collection<Worker> findWorkersMatching(
                    Collection<? extends Criterion> requiredCriterions) {
                if (requiredCriterions.isEmpty()) {
                    return new ArrayList<Worker>();
                }
                Collection<Worker> result = new ArrayList<Worker>();
                for (Worker each : allWorkers) {
                    if (each.satisfiesCriterions(requiredCriterions)) {
                        result.add(each);
                    }
                }
                return result;
            }
        };
    }


    private LocalDate asLocalDate(Date date) {
        return new LocalDate(date.getTime());
    }

    public List<AggregatedHoursGroup> getAggregatedByCriterions() {
        return getTaskSource().getAggregatedByCriterions();
    }

    public void setSubcontractedTaskData(SubcontractedTaskData subcontractedTaskData) {
        this.subcontractedTaskData = subcontractedTaskData;
    }

    public SubcontractedTaskData getSubcontractedTaskData() {
        return subcontractedTaskData;
    }

    public void removeAllResourceAllocations() {
        Set<ResourceAllocation<?>> resourceAllocations = getResourceAllocations();
        for (ResourceAllocation<?> resourceAllocation : resourceAllocations) {
            removeResourceAllocation(resourceAllocation);
        }
    }

    public boolean isSubcontracted() {
        return (subcontractedTaskData != null);
    }

}
