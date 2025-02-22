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

package com.blazebit.persistence.view.impl.metamodel;

import com.blazebit.persistence.view.CascadeType;
import com.blazebit.persistence.view.InverseRemoveStrategy;
import com.blazebit.persistence.view.impl.collection.CollectionInstantiatorImplementor;
import com.blazebit.persistence.view.impl.collection.MapInstantiatorImplementor;
import com.blazebit.persistence.view.impl.objectbuilder.ContainerAccumulator;
import com.blazebit.persistence.view.metamodel.BasicType;
import com.blazebit.persistence.view.metamodel.FlatViewType;
import com.blazebit.persistence.view.metamodel.ManagedViewType;
import com.blazebit.persistence.view.metamodel.PluralAttribute;
import com.blazebit.persistence.view.metamodel.MethodSingularAttribute;
import com.blazebit.persistence.view.metamodel.Type;
import com.blazebit.persistence.view.spi.type.VersionBasicUserType;

import javax.persistence.metamodel.Attribute;
import javax.persistence.metamodel.ManagedType;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

/**
 *
 * @author Christian Beikov
 * @since 1.0.0
 */
public abstract class AbstractMethodSingularAttribute<X, Y> extends AbstractMethodAttribute<X, Y> implements MethodSingularAttribute<X, Y> {

    private final Type<Y> type;
    private final int dirtyStateIndex;
    private final String mappedBy;
    private final Map<String, String> writableMappedByMapping;
    private final InverseRemoveStrategy inverseRemoveStrategy;
    private final boolean updatable;
    private final boolean mutable;
    private final boolean disallowOwnedUpdatableSubview;
    private final boolean optimisticLockProtected;
    private final boolean persistCascaded;
    private final boolean updateCascaded;
    private final boolean deleteCascaded;
    private final boolean orphanRemoval;
    private final Set<Type<?>> readOnlySubtypes;
    private final Set<Type<?>> persistSubtypes;
    private final Set<Type<?>> updateSubtypes;
    private final Set<Class<?>> allowedSubtypes;
    private final Set<Class<?>> parentRequiringUpdateSubtypes;
    private final Set<Class<?>> parentRequiringCreateSubtypes;
    private final Map<ManagedViewType<? extends Y>, String> inheritanceSubtypes;
    private final boolean createEmptyFlatView;

    @SuppressWarnings("unchecked")
    public AbstractMethodSingularAttribute(ManagedViewTypeImplementor<X> viewType, MethodAttributeMapping mapping, MetamodelBuildingContext context, int attributeIndex, int dirtyStateIndex, EmbeddableOwner embeddableMapping) {
        super(viewType, mapping, attributeIndex, context, embeddableMapping);
        if (updateMappableAttribute != null && updateMappableAttribute.getPersistentAttributeType() == Attribute.PersistentAttributeType.EMBEDDED) {
            if (embeddableMapping == null) {
                embeddableMapping = new EmbeddableOwner(viewType.getJpaManagedType().getJavaType(), this.mapping);
            } else {
                embeddableMapping = embeddableMapping.withSubMapping(this.mapping);
            }
        } else {
            embeddableMapping = null;
        }
        this.type = (Type<Y>) mapping.getType(context, embeddableMapping);
        if (mapping.isVersion()) {
            if (!(type instanceof BasicType<?>) || !(((BasicType<?>) type).getUserType() instanceof VersionBasicUserType<?>)) {
                context.addError("Illegal non-version capable type '" + type + "' used for @Version attribute on the " + mapping.getErrorLocation() + "!");
            }
        }

        // The declaring type must be mutable, otherwise attributes can't be considered updatable
        if (mapping.getUpdatable() == null) {
            // Id and version are never updatable
            if (mapping.isId() || mapping.isVersion() || !declaringType.isUpdatable() && !declaringType.isCreatable()) {
                this.updatable = false;
            } else {
                this.updatable = determineUpdatable(type);
            }
        } else {
            this.updatable = mapping.getUpdatable();
            if (updatable) {
                if (mapping.isId()) {
                    context.addError("Illegal @UpdatableMapping along with @IdMapping on the " + mapping.getErrorLocation() + "!");
                } else if (mapping.isVersion()) {
                    context.addError("Illegal @UpdatableMapping along with @Version on the " + mapping.getErrorLocation() + "!");
                }
                if (!declaringType.isUpdatable() && !declaringType.isCreatable()) {
                    // Note that although orphanRemoval and delete cascading makes sense for read only views, we don't want to mix up concepts for now..
                    context.addError("Illegal occurrences of @UpdatableMapping for non-updatable and non-creatable view type '" + declaringType.getJavaType().getName() + "' on the " + mapping.getErrorLocation() + "!");
                }
            }
        }
        boolean definesDeleteCascading = mapping.getCascadeTypes().contains(CascadeType.DELETE);
        boolean allowsDeleteCascading = updatable || mapping.getCascadeTypes().contains(CascadeType.AUTO);

        this.readOnlySubtypes = (Set<Type<?>>) (Set) mapping.getReadOnlySubtypes(context, embeddableMapping);

        if (updatable) {
            Set<Type<?>> types = determinePersistSubtypeSet(type, mapping.getCascadeSubtypes(context, embeddableMapping), mapping.getCascadePersistSubtypes(context, embeddableMapping), context);
            this.persistCascaded = mapping.getCascadeTypes().contains(CascadeType.PERSIST)
                    || mapping.getCascadeTypes().contains(CascadeType.AUTO) && !types.isEmpty();
            if (persistCascaded) {
                this.persistSubtypes = types;
            } else {
                this.persistSubtypes = Collections.emptySet();
            }
        } else {
            this.persistCascaded = false;
            this.persistSubtypes = Collections.emptySet();
        }

        ManagedType<?> managedType = context.getEntityMetamodel().getManagedType(declaringType.getEntityClass());
        this.mappedBy = mapping.determineMappedBy(managedType, this.mapping, context, embeddableMapping);
        this.disallowOwnedUpdatableSubview = context.isDisallowOwnedUpdatableSubview() && mapping.determineDisallowOwnedUpdatableSubview(context, embeddableMapping, updateMappableAttribute) && type instanceof ManagedViewType<?> && mappedBy == null
                && updateMappableAttribute != null && updateMappableAttribute.getPersistentAttributeType() != javax.persistence.metamodel.Attribute.PersistentAttributeType.EMBEDDED;

        // The declaring type must be mutable, otherwise attributes can't have cascading
        if (mapping.isId() || mapping.isVersion() || !declaringType.isUpdatable() && !declaringType.isCreatable()) {
            this.updateCascaded = false;
            this.updateSubtypes = Collections.emptySet();
        } else {
            // TODO: maybe allow to override mutability?
            Set<Type<?>> updateCascadeAllowedSubtypes;
            // Don't initialize automatic cascade update mappings comprised of updatable types when we disallow updatable types
            // If the declared type is updatable, we still initialize the subtypes, as we will throw during the validation phase instead
            if (disallowOwnedUpdatableSubview && !mapping.hasExplicitCascades() && !((ManagedViewType<?>) type).isUpdatable()) {
                updateCascadeAllowedSubtypes = Collections.emptySet();
            } else {
                updateCascadeAllowedSubtypes = determineUpdateSubtypeSet(type, mapping.getCascadeSubtypes(context, embeddableMapping), mapping.getCascadeUpdateSubtypes(context, embeddableMapping), context);
            }
            boolean updateCascaded = mapping.getCascadeTypes().contains(CascadeType.UPDATE)
                    || mapping.getCascadeTypes().contains(CascadeType.AUTO) && (updateMappableAttribute != null || isCorrelated()) && !updateCascadeAllowedSubtypes.isEmpty();
            if (updateCascaded) {
                this.updateCascaded = true;
                this.updateSubtypes = updateCascadeAllowedSubtypes;
            } else {
                this.updateCascaded = false;
                this.updateSubtypes = Collections.emptySet();
            }
        }

        this.mutable = determineMutable(type);

        if (disallowOwnedUpdatableSubview && mapping.getCascadeTypes().contains(CascadeType.UPDATE)) {
            context.addError("UPDATE cascading configuration for owned relationship attribute '" + updateMappableAttribute.getName() + "' is illegal. Remove the definition found on the " + mapping.getErrorLocation() + " or use @AllowUpdatableEntityViews! " +
                    "For further information on this topic, please consult the documentation https://persistence.blazebit.com/documentation/entity-view/manual/en_US/index.html#updatable-mappings-subview");
        }

        if (!mapping.getCascadeTypes().contains(CascadeType.AUTO)) {
            if (type instanceof BasicType<?> && context.getEntityMetamodel().getEntity(type.getJavaType()) == null
                    || type instanceof FlatViewType<?>) {
                context.addError("Cascading configuration for basic, embeddable or flat view type attributes is not allowed. Invalid definition found on the " + mapping.getErrorLocation() + "!");
            }
        }
        if (!updatable && mapping.getCascadeTypes().contains(CascadeType.PERSIST)) {
            context.addError("Persist cascading for non-updatable attributes is not allowed. Invalid definition found on the " + mapping.getErrorLocation() + "!");
        }

        this.allowedSubtypes = createAllowedSubtypesSet();
        // We treat correlated attributes specially
        this.parentRequiringUpdateSubtypes = isCorrelated() ? Collections.<Class<?>>emptySet() : createParentRequiringUpdateSubtypesSet();
        this.parentRequiringCreateSubtypes = isCorrelated() ? Collections.<Class<?>>emptySet() : createParentRequiringCreateSubtypesSet();
        this.optimisticLockProtected = determineOptimisticLockProtected(mapping, context, mutable);
        this.inheritanceSubtypes = (Map<ManagedViewType<? extends Y>, String>) (Map<?, ?>) mapping.getInheritanceSubtypes(context, embeddableMapping);
        this.dirtyStateIndex = determineDirtyStateIndex(dirtyStateIndex);
        if (this.dirtyStateIndex == -1) {
            this.inverseRemoveStrategy = null;
            this.writableMappedByMapping = null;
        } else {
            if (this.mappedBy == null) {
                this.inverseRemoveStrategy = null;
                this.writableMappedByMapping = null;
            } else {
                this.inverseRemoveStrategy = mapping.getInverseRemoveStrategy() == null ? InverseRemoveStrategy.SET_NULL : mapping.getInverseRemoveStrategy();
                this.writableMappedByMapping = mapping.determineWritableMappedByMappings(managedType, mappedBy, context);
            }
        }

        if (this.inverseRemoveStrategy == null && mapping.getInverseRemoveStrategy() != null && this.dirtyStateIndex != -1) {
            context.addError("Found use of @MappingInverse on attribute that isn't an inverse relationship. Invalid definition found on the " + mapping.getErrorLocation() + "!");
        }

        if (Boolean.FALSE.equals(mapping.getOrphanRemoval())) {
            this.orphanRemoval = false;
        } else {
            // Determine orphan removal based on remove strategy
            this.orphanRemoval = inverseRemoveStrategy == InverseRemoveStrategy.REMOVE || Boolean.TRUE.equals(mapping.getOrphanRemoval());
        }

        // Orphan removal implies delete cascading, inverse attributes also always do delete cascading
        this.deleteCascaded = orphanRemoval || definesDeleteCascading || allowsDeleteCascading && inverseRemoveStrategy != null;

        if (updatable) {
            String mappingExpression = getMapping();
            if (mappingExpression != null) {
                boolean jpaOrphanRemoval = context.getJpaProvider().isOrphanRemoval(declaringType.getJpaManagedType(), mappingExpression);
                if (jpaOrphanRemoval && !orphanRemoval) {
                    context.addError("Orphan removal configuration via @UpdatableMapping must be defined if entity attribute defines orphan removal. Invalid definition found on the  " + mapping.getErrorLocation() + "!");
                }
                boolean jpaDeleteCascaded = context.getJpaProvider().isDeleteCascaded(declaringType.getJpaManagedType(), mappingExpression);
                if (jpaDeleteCascaded && !deleteCascaded) {
                    context.addError("Delete cascading configuration via @UpdatableMapping must be defined if entity attribute defines delete cascading. Invalid definition found on the  " + mapping.getErrorLocation() + "!");
                }
            }
        }
        // Within flat views and creatable view we initialize flat view attributes with empty instances
        if ((viewType.getMappingType() == Type.MappingType.FLAT_VIEW || viewType.isCreatable()) && type.getMappingType() == Type.MappingType.FLAT_VIEW) {
            // Make sure the type has an empty constructor
            if (!((ManagedViewTypeImplementor<?>) type).hasEmptyConstructor()) {
                context.addError("The flat view type '" + type.getJavaType().getName() + "' must provide an empty constructor since empty instances might be created through the  " + mapping.getErrorLocation() + "!");
            }
        }
        if (type.getMappingType() == Type.MappingType.FLAT_VIEW && (mapping.getCreateEmptyFlatViews() == null && context.isCreateEmptyFlatViews() || Boolean.TRUE.equals(mapping.getCreateEmptyFlatViews()))) {
            this.createEmptyFlatView = true;
        } else {
            this.createEmptyFlatView = false;
        }
    }

    private boolean determineUpdatable(Type<?> elementType) {
        // Subquery and Parameter mappings are never considered updatable
        if (getMappingType() != MappingType.BASIC && getMappingType() != MappingType.CORRELATED) {
            return false;
        }

        // For a singular attribute being considered updatable, there must be a setter
        // If the type is a flat view, it must be updatable or creatable and have a setter
        if (elementType instanceof FlatViewType<?>) {
            FlatViewType<?> t = (FlatViewType<?>) elementType;
            return getSetterMethod() != null || t.isUpdatable() || t.isCreatable();
        }

        // We exclude entity types from this since there is no clear intent
        return getSetterMethod() != null;
    }

    @Override
    public boolean isCreateEmptyFlatView() {
        return createEmptyFlatView;
    }

    @Override
    protected boolean isDisallowOwnedUpdatableSubview() {
        return disallowOwnedUpdatableSubview;
    }

    @Override
    public int getDirtyStateIndex() {
        return dirtyStateIndex;
    }

    @Override
    public Map<String, String> getWritableMappedByMappings() {
        return writableMappedByMapping;
    }

    @Override
    public String getMappedBy() {
        return mappedBy;
    }

    @Override
    public InverseRemoveStrategy getInverseRemoveStrategy() {
        return inverseRemoveStrategy;
    }

    @Override
    public boolean isCollection() {
        return false;
    }

    @Override
    protected boolean isSorted() {
        return false;
    }

    @Override
    public boolean isIndexed() {
        return false;
    }

    @Override
    protected boolean isForcedUnique() {
        return false;
    }

    @Override
    protected boolean isElementCollectionOrdered() {
        return false;
    }

    @Override
    protected boolean isElementCollectionSorted() {
        return false;
    }

    @Override
    protected boolean isElementCollectionForcedUnique() {
        return false;
    }

    @Override
    public ContainerAccumulator<?> getContainerAccumulator() {
        throw new UnsupportedOperationException("Singular attribute");
    }

    @Override
    protected PluralAttribute.CollectionType getCollectionType() {
        throw new UnsupportedOperationException("Singular attribute");
    }

    @Override
    protected PluralAttribute.ElementCollectionType getElementCollectionType() {
        throw new UnsupportedOperationException("Singular attribute");
    }

    @Override
    public CollectionInstantiatorImplementor<?, ?> getCollectionInstantiator() {
        throw new UnsupportedOperationException("Singular attribute");
    }

    @Override
    public MapInstantiatorImplementor<?, ?> getMapInstantiator() {
        throw new UnsupportedOperationException("Singular attribute");
    }

    @Override
    public AttributeType getAttributeType() {
        return AttributeType.SINGULAR;
    }

    @Override
    public boolean isUpdatable() {
        return updatable;
    }

    @Override
    public boolean isMutable() {
        return mutable;
    }

    @Override
    public boolean isOptimisticLockProtected() {
        return optimisticLockProtected;
    }

    @Override
    public boolean isPersistCascaded() {
        return persistCascaded;
    }

    @Override
    public boolean isUpdateCascaded() {
        return updateCascaded;
    }

    @Override
    public boolean isDeleteCascaded() {
        return deleteCascaded;
    }

    @Override
    public boolean isOrphanRemoval() {
        return orphanRemoval;
    }

    @Override
    public Set<Type<?>> getReadOnlyAllowedSubtypes() {
        return readOnlySubtypes;
    }

    @Override
    public Set<Type<?>> getPersistCascadeAllowedSubtypes() {
        return persistSubtypes;
    }

    @Override
    public Set<Type<?>> getUpdateCascadeAllowedSubtypes() {
        return updateSubtypes;
    }

    @Override
    public Set<Class<?>> getAllowedSubtypes() {
        return allowedSubtypes;
    }

    @Override
    public Set<Class<?>> getParentRequiringUpdateSubtypes() {
        return parentRequiringUpdateSubtypes;
    }

    @Override
    public Set<Class<?>> getParentRequiringCreateSubtypes() {
        return parentRequiringCreateSubtypes;
    }

    @Override
    public Type<Y> getType() {
        return type;
    }

    @Override
    public Type<?> getElementType() {
        return type;
    }

    @Override
    public Map<ManagedViewType<? extends Y>, String> getInheritanceSubtypeMappings() {
        return inheritanceSubtypes;
    }

    @SuppressWarnings("unchecked")
    protected Map<ManagedViewTypeImplementor<?>, String> elementInheritanceSubtypeMappings() {
        return (Map<ManagedViewTypeImplementor<?>, String>) (Map<?, ?>) inheritanceSubtypes;
    }

    protected Type<?> getKeyType() {
        return null;
    }

    protected Map<ManagedViewTypeImplementor<?>, String> keyInheritanceSubtypeMappings() {
        return null;
    }

    protected boolean isKeySubview() {
        return false;
    }

    @Override
    public boolean isSubview() {
        return type.getMappingType() != Type.MappingType.BASIC;
    }
}
