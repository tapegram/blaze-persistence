/*
 * Copyright 2014 - 2023 Blazebit.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.blazebit.persistence.examples.itsm.model.ticket.view;

import java.util.Comparator;
import java.util.SortedSet;

import com.blazebit.persistence.examples.itsm.model.ticket.entity.TicketStatus;
import com.blazebit.persistence.view.CollectionMapping;
import com.blazebit.persistence.view.EntityView;
import com.blazebit.persistence.view.UpdatableEntityView;
import com.blazebit.persistence.view.UpdatableMapping;

/**
 * @author Giovanni Lovato
 * @since 1.4.0
 */
@EntityView(TicketStatus.class)
@UpdatableEntityView
public interface StatusWithNext extends StatusId {

    @UpdatableMapping
    @CollectionMapping(comparator = TicketStatusComparator.class)
    SortedSet<StatusBase> getNext();

    class TicketStatusComparator implements Comparator<StatusBase> {

        @Override
        public int compare(StatusBase o1, StatusBase o2) {
            Comparator<StatusBase> comparator = Comparator.comparing(StatusBase::getName,
                    Comparator.naturalOrder());
            return comparator.compare(o1, o2);
        }

    }

}
