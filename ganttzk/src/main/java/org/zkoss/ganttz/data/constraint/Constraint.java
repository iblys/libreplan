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
package org.zkoss.ganttz.data.constraint;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import org.zkoss.ganttz.util.WeakReferencedListeners;
import org.zkoss.ganttz.util.WeakReferencedListeners.IListenerNotification;

/**
 * @author Óscar González Fernández <ogonzalez@igalia.com>
 *
 */
public abstract class Constraint<T> {

    public interface IConstraintViolationListener<T> {
        public void constraintViolated(Constraint<T> constraint, T value);
    }

    public static class ConstraintBuilder<T> {

        private final T value;

        private final List<Constraint<T>> constraints = new ArrayList<Constraint<T>>();

        public ConstraintBuilder(T value) {
            this.value = value;
        }

        public ConstraintBuilder<T> withConstraints(
                Constraint<T>... constraints) {
            return withConstraints(Arrays.asList(constraints));
        }

        public ConstraintBuilder<T> withConstraints(
                List<Constraint<T>> constraints) {
            this.constraints.addAll(constraints);
            return this;
        }

        public T apply() {
            return Constraint.apply(value, constraints);
        }

    }

    public static <T> ConstraintBuilder<T> initialValue(T value) {
        return new ConstraintBuilder<T>(value);
    }

    public static <T> T apply(T initialValue, Constraint<T>... constraints) {
        return apply(initialValue, Arrays.asList(constraints));
    }

    public static <T> T apply(T initialValue,
            Collection<Constraint<T>> constraints) {
        T result = initialValue;
        for (Constraint<T> each : constraints) {
            result = each.applyTo(result);
        }
        for (Constraint<T> each : constraints) {
            if (!each.isSatisfiedBy(result)) {
                each.fireNotSatisfied(result);
            }
        }
        return result;
    }

    private static final Constraint<Object> VOID_CONSTRAINT = new Constraint<Object>() {

        @Override
        protected Object applyConstraintTo(Object currentValue) {
            return currentValue;
        }

        @Override
        public boolean isSatisfiedBy(Object value) {
            return true;
        }
    };

    @SuppressWarnings("unchecked")
    public static <T> Constraint<T> voidConstraint() {
        return (Constraint<T>) VOID_CONSTRAINT;
    }

    private WeakReferencedListeners<IConstraintViolationListener<T>> weakListeners = WeakReferencedListeners
            .create();

    public final T applyTo(T currentValue) {
        T result = applyConstraintTo(currentValue);
        if (!isSatisfiedBy(result)) {
            throw new IllegalStateException(result
                    + " doesn't fulfill this constraint: " + this);
        }
        return result;
    }

    protected abstract T applyConstraintTo(T currentValue);

    public abstract boolean isSatisfiedBy(T value);

    private void fireNotSatisfied(final T value) {
        weakListeners
                .fireEvent(new IListenerNotification<IConstraintViolationListener<T>>() {

                    @Override
                    public void doNotify(
                            IConstraintViolationListener<T> listener) {
                        listener.constraintViolated(Constraint.this, value);
                    }
        });
    }

    public void addConstraintViolationListener(IConstraintViolationListener<T> listener) {
        weakListeners.addListener(listener);
    }

}
