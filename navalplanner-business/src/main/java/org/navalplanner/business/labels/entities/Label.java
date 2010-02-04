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

package org.navalplanner.business.labels.entities;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import org.apache.commons.lang.Validate;
import org.hibernate.validator.NotEmpty;
import org.hibernate.validator.NotNull;
import org.navalplanner.business.common.IntegrationEntity;
import org.navalplanner.business.common.Registry;
import org.navalplanner.business.labels.daos.ILabelDAO;
import org.navalplanner.business.orders.entities.OrderElement;

/**
 * Label entity
 *
 * @author Diego Pino Garcia<dpino@igalia.com>
 *
 */
public class Label extends IntegrationEntity {

    @NotEmpty(message = "name not specified")
    private String name;

    @NotNull(message = "type not specified")
    private LabelType type;

    private Set<OrderElement> orderElements = new HashSet<OrderElement>();

    // Default constructor, needed by Hibernate
    protected Label() {

    }

    public static Label create(String name) {
        return create(new Label(name));
    }

    public static Label create(String code, String name) {
        return create(new Label(name), code);
    }

    protected Label(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public LabelType getType() {
        return type;
    }

    public void setType(LabelType type) {
        this.type = type;
    }

    public Set<OrderElement> getOrderElements() {
        return Collections.unmodifiableSet(orderElements);
    }

    public void addOrderElement(OrderElement orderElement) {
        Validate.notNull(orderElement);
        orderElements.add(orderElement);
    }

    public void removeOrderElement(OrderElement orderElement) {
        orderElements.add(orderElement);
    }

    public boolean isEqualTo(Label label) {
        if ((this.getName() != null) && (label.getName() != null)
                && (this.getType() != null) && (label.getType() != null)
                && (this.getType().getName() != null)
                && (label.getType().getName() != null)
                && this.getName().equals(label.getName())
                && this.getType().getName().equals(label.getType().getName())) {
            return true;
        }
        return false;
    }

    public String toString() {
        return name;
    }

    @Override
    protected ILabelDAO getIntegrationEntityDAO() {
        return Registry.getLabelDAO();
    }

}
