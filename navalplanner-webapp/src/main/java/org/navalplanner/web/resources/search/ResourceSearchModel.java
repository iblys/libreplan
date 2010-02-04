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

package org.navalplanner.web.resources.search;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang.StringUtils;
import org.navalplanner.business.resources.daos.ICriterionDAO;
import org.navalplanner.business.resources.daos.IMachineDAO;
import org.navalplanner.business.resources.daos.IResourceDAO;
import org.navalplanner.business.resources.daos.IWorkerDAO;
import org.navalplanner.business.resources.entities.Criterion;
import org.navalplanner.business.resources.entities.CriterionType;
import org.navalplanner.business.resources.entities.Resource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Scope(BeanDefinition.SCOPE_PROTOTYPE)
/**
 * @author Diego Pino Garcia <dpino@igalia.com>
 */
public class ResourceSearchModel implements IResourceSearchModel {

    @Autowired
    private IWorkerDAO workerDAO;

    @Autowired
    private IMachineDAO machineDAO;

    @Autowired
    private IResourceDAO resourceDAO;

    @Autowired
    private ICriterionDAO criterionDAO;

    @Override
    @Transactional(readOnly = true)
    public Map<CriterionType, Set<Criterion>> getCriterions() {
        HashMap<CriterionType, Set<Criterion>> result = new HashMap<CriterionType, Set<Criterion>>();

        List<Criterion> criterions = criterionDAO.getAll();
        for (Criterion criterion : criterions) {
            CriterionType key = criterion.getType();
            Set<Criterion> values = (!result.containsKey(key)) ?
                    new HashSet<Criterion>() : (Set<Criterion>) result.get(key);
            values.add(criterion);
            result.put(key, values);
        }
        return result;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Resource> findResources(String name, List<Criterion> criterions) {
        return findByNameAndCriterions(name, criterions);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Resource> findResources(String name) {
        return findByNameAndCriterions(name, Collections
                .<Criterion> emptyList());
    }

    private List<Resource> findByNameAndCriterions(String name,
            List<Criterion> criterions) {
        final boolean emptyName = StringUtils.isEmpty(name);
        if (criterions.isEmpty() && emptyName) {
            return resourceDAO.list(Resource.class);
        }
        Set<Resource> resourcesMatchingCriterions = null;
        if (!criterions.isEmpty()) {
            resourcesMatchingCriterions = new HashSet<Resource>(resourceDAO
                    .findAllSatisfyingCriterions(criterions));
            if (resourcesMatchingCriterions.isEmpty()) {
                return new ArrayList<Resource>();
            }
        }
        if (emptyName) {
            return new ArrayList<Resource>(resourcesMatchingCriterions);
        }
        Set<Resource> result = intersect(workerDAO.findByNameSubpartOrNifCaseInsensitive(name),
                resourcesMatchingCriterions);
        result.addAll(intersect(machineDAO.findByNameOrCode(name),
                resourcesMatchingCriterions));
        return new ArrayList<Resource>(result);
    }

    private static Set<Resource> intersect(List<? extends Resource> all,
            Set<Resource> filteringBy) {
        if (filteringBy == null) {
            return new HashSet<Resource>(all);
        }
        Set<Resource> result = new HashSet<Resource>(filteringBy);
        result.retainAll(all);
        return result;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Resource> getAllResources() {
        return resourceDAO.getResources();
    }
}
