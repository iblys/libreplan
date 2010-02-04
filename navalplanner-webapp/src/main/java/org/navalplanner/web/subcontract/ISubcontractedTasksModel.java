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
package org.navalplanner.web.subcontract;

import java.util.List;

import org.navalplanner.business.common.exceptions.ValidationException;
import org.navalplanner.business.planner.entities.SubcontractedTaskData;
import org.navalplanner.web.subcontract.exceptions.ConnectionProblemsException;
import org.navalplanner.web.subcontract.exceptions.UnrecoverableErrorServiceException;

/**
 * Contract for {@link SubcontractedTasksModel}.
 *
 * @author Manuel Rego Casasnovas <mrego@igalia.com>
 */
public interface ISubcontractedTasksModel {

    List<SubcontractedTaskData> getSubcontractedTasks();

    String getOrderCode(SubcontractedTaskData subcontractedTaskData);

    void sendToSubcontractor(SubcontractedTaskData subcontractedTaskData)
            throws ValidationException, ConnectionProblemsException,
            UnrecoverableErrorServiceException;

    String exportXML(SubcontractedTaskData subcontractedTaskData);

}