/*
 * Copyright (c) 2024 Republic of Reinvention BV <info@reinvention.be>. All Rights Reserved.
 * This file is part of project "cinematek-filmout-site" and can not be copied and/or distributed without
 * the express permission and written consent of the legal responsible of Republic of Reinvention BV.
 */

package be.goudvuur.base.bbor62.jackson;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.cfg.SerializerFactoryConfig;
import com.fasterxml.jackson.databind.introspect.AnnotatedMember;
import com.fasterxml.jackson.databind.introspect.BeanPropertyDefinition;
import com.fasterxml.jackson.databind.jsontype.TypeSerializer;
import com.fasterxml.jackson.databind.ser.SerializerFactory;
import com.fasterxml.jackson.databind.ser.impl.PropertySerializerMap;
import com.fasterxml.jackson.databind.ser.std.BeanSerializerBase;
import com.fasterxml.jackson.databind.util.Annotations;

/**
 * Created by bram on Dec 12, 2024
 */
public class Custom
{
    public static class MyPropertyBuilder extends com.fasterxml.jackson.databind.ser.PropertyBuilder
    {
        public MyPropertyBuilder(SerializationConfig config, BeanDescription beanDesc)
        {
            super(config, beanDesc);
        }

        @Override
        protected com.fasterxml.jackson.databind.ser.BeanPropertyWriter _constructPropertyWriter(BeanPropertyDefinition propDef,
                                                                                                 AnnotatedMember member, Annotations contextAnnotations,
                                                                                                 JavaType declaredType,
                                                                                                 JsonSerializer<?> ser, TypeSerializer typeSer, JavaType serType,
                                                                                                 boolean suppressNulls, Object suppressableValue,
                                                                                                 Class<?>[] includeInViews)
                        throws JsonMappingException
        {
            return new MyBeanPropertyWriter(propDef, member, contextAnnotations, declaredType,
                                            ser, typeSer, serType, suppressNulls, suppressableValue, includeInViews);
        }
    }

    public static class MyBeanSerializerFactory extends com.fasterxml.jackson.databind.ser.BeanSerializerFactory
    {
        public MyBeanSerializerFactory(SerializerFactoryConfig config)
        {
            super(config);
        }

        @Override
        protected com.fasterxml.jackson.databind.ser.PropertyBuilder constructPropertyBuilder(SerializationConfig config, BeanDescription beanDesc)
        {
            return new MyPropertyBuilder(config, beanDesc);
        }

        /**
         * This is just a copy/paste of the superclass method, with the exception commented out because we're just a wrapper
         */
        @Override
        public SerializerFactory withConfig(SerializerFactoryConfig config)
        {
            if (_factoryConfig == config) {
                return this;
            }
            //            if (getClass() != BeanSerializerFactory.class) {
            //                throw new IllegalStateException("Subtype of BeanSerializerFactory ("+getClass().getName()
            //                                                +") has not properly overridden method 'withAdditionalSerializers': cannot instantiate subtype with "
            //                                                +"additional serializer definitions");
            //            }
            return new MyBeanSerializerFactory(config);
        }
    }

    public static class MyBeanPropertyWriter extends com.fasterxml.jackson.databind.ser.BeanPropertyWriter
    {
        public MyBeanPropertyWriter(BeanPropertyDefinition propDef,
                                    AnnotatedMember member, Annotations contextAnnotations,
                                    JavaType declaredType,
                                    JsonSerializer<?> ser, TypeSerializer typeSer, JavaType serType,
                                    boolean suppressNulls, Object suppressableValue,
                                    Class<?>[] includeInViews)
        {
            super(propDef, member, contextAnnotations, declaredType, ser, typeSer, serType, suppressNulls, suppressableValue, includeInViews);
        }

        /**
         * Tests if the field of the given bean will get serialized as a property.
         * This is basically a copy/paste of BeanPropertyWriter.serializeAsField(), but:
         * - every place where JsonGenerator was referenced, we changed it by "return true",
         * - normal returns were replaced by "return false";
         * - the _handleSelfReference() call was inlined and adapted according to the above rules
         */
        public boolean willSerializeAsField(Object bean, SerializerProvider prov) throws Exception
        {
            Object value = this.get(bean);

            // Null handling is bit different, check that first
            if (value == null) {
                // 20-Jun-2022, tatu: Defer checking of null, see [databind#3481]
                if ((_suppressableValue != null)
                    && prov.includeFilterSuppressNulls(_suppressableValue)) {
                    return false;
                }
                if (_nullSerializer != null) {
                    return true;
                }
                return false;
            }
            // then find serializer to use
            JsonSerializer<Object> ser = _serializer;
            if (ser == null) {
                Class<?> cls = value.getClass();
                PropertySerializerMap m = _dynamicSerializers;
                ser = m.serializerFor(cls);
                if (ser == null) {
                    ser = _findAndAddDynamic(m, cls, prov);
                }
            }
            // and then see if we must suppress certain values (default, empty)
            if (_suppressableValue != null) {
                if (MARKER_FOR_EMPTY == _suppressableValue) {
                    if (ser.isEmpty(prov, value)) {
                        return false;
                    }
                }
                else if (_suppressableValue.equals(value)) {
                    return false;
                }
            }
            // For non-nulls: simple check for direct cycles
            if (value == bean) {
                if (!ser.usesObjectId()) {
                    if (prov.isEnabled(SerializationFeature.FAIL_ON_SELF_REFERENCES)) {
                        // 05-Feb-2013, tatu: Usually a problem, but NOT if we are handling
                        // object id; this may be the case for BeanSerializers at least.
                        // 13-Feb-2014, tatu: another possible ok case: custom serializer
                        // (something OTHER than {@link BeanSerializerBase}
                        if (ser instanceof BeanSerializerBase) {
                            prov.reportBadDefinition(getType(), "Direct self-reference leading to cycle");
                        }
                    }
                    else if (prov.isEnabled(SerializationFeature.WRITE_SELF_REFERENCES_AS_NULL)) {
                        return true;
                    }
                }
                return false;
            }

            return true;
        }
    }
}
