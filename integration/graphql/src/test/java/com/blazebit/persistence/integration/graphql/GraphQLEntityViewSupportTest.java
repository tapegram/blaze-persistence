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

package com.blazebit.persistence.integration.graphql;

import com.blazebit.persistence.CriteriaBuilder;
import com.blazebit.persistence.PaginatedCriteriaBuilder;
import com.blazebit.persistence.integration.graphql.views.AnimalView;
import com.blazebit.persistence.integration.graphql.views.DocumentView;
import com.blazebit.persistence.integration.graphql.views.PersonView;
import com.blazebit.persistence.view.EntityViewSetting;
import graphql.schema.DataFetchingEnvironment;
import graphql.schema.DataFetchingFieldSelectionSet;
import graphql.schema.GraphQLFieldDefinition;
import org.junit.Assert;
import org.junit.Test;

import java.util.Arrays;
import java.util.HashSet;

import static com.blazebit.persistence.integration.graphql.TestSchemaHelpers.animalUnionType;
import static com.blazebit.persistence.integration.graphql.TestSchemaHelpers.documentObjectType;
import static com.blazebit.persistence.integration.graphql.TestSchemaHelpers.getGraphQLEntityViewSupport;
import static com.blazebit.persistence.integration.graphql.TestSchemaHelpers.makeFieldDefinition;
import static com.blazebit.persistence.integration.graphql.TestSchemaHelpers.makeMockDataFetchingEnvironment;
import static com.blazebit.persistence.integration.graphql.TestSchemaHelpers.makeMockSelectionSet;
import static com.blazebit.persistence.integration.graphql.TestSchemaHelpers.makeRelayConnection;
import static com.blazebit.persistence.integration.graphql.TestSchemaHelpers.personObjectType;

/**
 * @author David Kubecka
 * @since 1.6.9
 */
public class GraphQLEntityViewSupportTest {

    GraphQLEntityViewSupport graphQLEntityViewSupport = getGraphQLEntityViewSupport();

    @Test
    public void testFetchesInSetting() {
        GraphQLFieldDefinition rootFieldDefinition = makeFieldDefinition("getDocument", documentObjectType);
        DataFetchingFieldSelectionSet selectionSet =
                makeMockSelectionSet("Document", "name", "owner", "owner/name", "Document.__typename");

        DataFetchingEnvironment dfe = makeMockDataFetchingEnvironment(rootFieldDefinition, selectionSet);

        EntityViewSetting<DocumentView, CriteriaBuilder<DocumentView>> setting = graphQLEntityViewSupport.createSetting(dfe);

        Assert.assertEquals(new HashSet<>(Arrays.asList("id", "name", "owner.name")), setting.getFetches());
    }

    @Test
    public void testFetchesInPaginatedSetting() {
        GraphQLFieldDefinition rootFieldDefinition = makeFieldDefinition("getDocument", makeRelayConnection(documentObjectType));
        DataFetchingFieldSelectionSet selectionSet =
                makeMockSelectionSet("Connection", "edges", "edges/node", "edges/node/owner", "edges/node/owner/name");

        DataFetchingEnvironment dfe = makeMockDataFetchingEnvironment(rootFieldDefinition, selectionSet);

        EntityViewSetting<DocumentView, PaginatedCriteriaBuilder<DocumentView>> paginatedSetting =
                graphQLEntityViewSupport.createPaginatedSetting(dfe);

        Assert.assertEquals(new HashSet<>(Arrays.asList("owner.name")), paginatedSetting.getFetches());
    }

    @Test
    public void testRootUnionInSelectionSet() {
        GraphQLFieldDefinition rootFieldDefinition = makeFieldDefinition("getAnimal", animalUnionType);
        DataFetchingFieldSelectionSet selectionSet = makeMockSelectionSet("Cat", "name", "[Cat,Animal].__typename");

        DataFetchingEnvironment dfe = makeMockDataFetchingEnvironment(rootFieldDefinition, selectionSet);

        EntityViewSetting<AnimalView, CriteriaBuilder<AnimalView>> setting = graphQLEntityViewSupport.createSetting(dfe);

        Assert.assertEquals(new HashSet<>(Arrays.asList("id", "name")), setting.getFetches());
    }

    @Test
    public void testNestedUnionInSelectionSet() {
        GraphQLFieldDefinition rootFieldDefinition = makeFieldDefinition("getPerson", personObjectType);
        DataFetchingFieldSelectionSet selectionSet = makeMockSelectionSet("Person", "name", "animal", "animal/Cat.name");

        DataFetchingEnvironment dfe = makeMockDataFetchingEnvironment(rootFieldDefinition, selectionSet);

        EntityViewSetting<PersonView, CriteriaBuilder<PersonView>> setting = graphQLEntityViewSupport.createSetting(dfe);

        Assert.assertEquals(new HashSet<>(Arrays.asList("name", "animal.name")), setting.getFetches());
    }
}
