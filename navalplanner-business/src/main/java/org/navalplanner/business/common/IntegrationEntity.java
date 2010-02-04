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

package org.navalplanner.business.common;

import java.util.UUID;

import org.apache.commons.lang.StringUtils;
import org.hibernate.validator.AssertTrue;
import org.hibernate.validator.NotEmpty;
import org.navalplanner.business.common.daos.IIntegrationEntityDAO;
import org.navalplanner.business.common.exceptions.InstanceNotFoundException;

/**
 * Base class for all entities to be sent/received to/from other applications.
 * These entities have a "code" attribute, which unlike "id" is unique among
 * the applications to be integrated ("id" is only unique inside
 * "navalplanner").
 *
 * @author Fernando Bellas Permuy <fbellas@udc.es>
 */
public abstract class IntegrationEntity extends BaseEntity {

    private String code;

    @NotEmpty(message="code not specified")
    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    /**
     * This creation method must be used when we want to create an instance
     * specifying a specific code (e.g. provided by the end-user or an external
     * application).
     */
    protected static <T extends IntegrationEntity> T create(
        T integrationEntity, String code) {

        BaseEntity.create(integrationEntity);
        integrationEntity.code = code;

        return integrationEntity;

    }

    /**
     * This creation method must be used when we want the creation method to
     * automatically generate a code. This is a Template method which delegates
     * code generation by calling on <code>generateCode()</code>.
     */
    protected static <T extends IntegrationEntity> T create(
        T integrationEntity) {

        BaseEntity.create(integrationEntity);
        integrationEntity.code = generateCode();

        return integrationEntity;

    }

    /**
     * This method is called by <code>create(IntegrationEntity)</code>. It
     * returns an unique String UUID. The current implementation is good enough
     * for entities created in test classes. However, concrete classes
     * interested in automatically generating descriptive identifiers when
     * calling <code>create(IntegrationEntity)</code>, will probably redefine
     * this method.
     */
    protected static String generateCode() {
        return UUID.randomUUID().toString();
    }

    /**
     * It checks if there exists another integration entity of the same type
     * with the same code. This method is a Template method that calls on
     * the private method <code>findIntegrationEntityDAO</code>, which in turn
     * calls on the abstract method <code>getIntegrationEntityDAO()</code>.
     */
    @AssertTrue(message="code is already being used")
    public boolean checkConstraintUniqueCode() {

        /* Check if it makes sense to check the constraint .*/
        if (!iCodeSpecified()) {
            return true;
        }

        /* Check the constraint. */
        IIntegrationEntityDAO<? extends IntegrationEntity>
            integrationEntityDAO = findIntegrationEntityDAO();

        if (isNewObject()) {
            return !integrationEntityDAO.existsByCodeAnotherTransaction(code);
        } else {
            try {
                IntegrationEntity entity =
                    integrationEntityDAO.findByCodeAnotherTransaction(code);
                return entity.getId().equals(getId());
            } catch (InstanceNotFoundException e) {
                return true;
            }
        }

    }

    /**
     * It returns the DAO of this entity.
     */
    protected abstract IIntegrationEntityDAO<? extends IntegrationEntity>
        getIntegrationEntityDAO();

    private IIntegrationEntityDAO<? extends IntegrationEntity>
        findIntegrationEntityDAO() {

        IIntegrationEntityDAO<? extends IntegrationEntity>
            integrationEntityDAO = getIntegrationEntityDAO();

        if (!integrationEntityDAO.getEntityClass().equals(this.getClass())) {
            throw new RuntimeException(this.getClass().getName() + "::" +
                "getIntegrationEntityDAO returns an incompatible " +
                "DAO: " + integrationEntityDAO.getClass().getName());
        } else {
            return integrationEntityDAO;
        }

    }

    private boolean iCodeSpecified() {
        return !StringUtils.isBlank(code);
    }

}
