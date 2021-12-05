/*
 * Copyright 2021 Apollo Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package com.fxz.fuled.config.starter.annotation;



import com.fxz.fuled.config.starter.spring.property.*;
import com.fxz.fuled.config.starter.spring.util.SpringInjector;
import com.google.common.collect.LinkedListMultimap;
import com.google.common.collect.Multimap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.context.annotation.Bean;

import java.beans.PropertyDescriptor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Set;

/**
 * Spring value processor of field or method which has @Value and xml config placeholders.
 *
 * @author github.com/zhegexiaohuozi  seimimaster@gmail.com
 * @since 2017/12/20.
 */
public class SpringValueProcessor extends DimaondProcessor implements BeanFactoryPostProcessor, BeanFactoryAware {

    private static final Logger logger = LoggerFactory.getLogger(SpringValueProcessor.class);

    //  private final ConfigUtil configUtil;
    private final PlaceholderHelper placeholderHelper;
    private final SpringValueRegistry springValueRegistry;

    private BeanFactory beanFactory;
    private Multimap<String, SpringValueDefinition> beanName2SpringValueDefinitions;

    public SpringValueProcessor() {
//    configUtil = ApolloInjector.getInstance(ConfigUtil.class);
        placeholderHelper = SpringInjector.getInstance(PlaceholderHelper.class);
        springValueRegistry = SpringInjector.getInstance(SpringValueRegistry.class);
        beanName2SpringValueDefinitions = LinkedListMultimap.create();
    }

    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory)
            throws BeansException {
        if (beanFactory instanceof BeanDefinitionRegistry) {
            beanName2SpringValueDefinitions = SpringValueDefinitionProcessor
                    .getBeanName2SpringValueDefinitions((BeanDefinitionRegistry) beanFactory);
        }
    }

    @Override
    public Object postProcessBeforeInitialization(Object bean, String beanName)
            throws BeansException {
        super.postProcessBeforeInitialization(bean, beanName);
        processBeanPropertyValues(bean, beanName);
        return bean;
    }


    @Override
    protected void processField(Object bean, String beanName, Field field) {
        // register @Value on field
        Value value = field.getAnnotation(Value.class);
        if (value == null) {
            return;
        }
        Set<String> keys = placeholderHelper.extractPlaceholderKeys(value.value());

        if (keys.isEmpty()) {
            return;
        }

        for (String key : keys) {
            SpringValue springValue = new SpringValue(key, value.value(), bean, beanName, field, false);
            springValueRegistry.register(beanFactory, key, springValue);
            logger.debug("Monitoring {}", springValue);
        }
    }

    @Override
    protected void processMethod(Object bean, String beanName, Method method) {
        //register @Value on method
        Value value = method.getAnnotation(Value.class);
        if (value == null) {
            return;
        }
        //skip Configuration bean methods
        if (method.getAnnotation(Bean.class) != null) {
            return;
        }
        if (method.getParameterTypes().length != 1) {
            logger.error("Ignore @Value setter {}.{}, expecting 1 parameter, actual {} parameters",
                    bean.getClass().getName(), method.getName(), method.getParameterTypes().length);
            return;
        }

        Set<String> keys = placeholderHelper.extractPlaceholderKeys(value.value());

        if (keys.isEmpty()) {
            return;
        }

        for (String key : keys) {
            SpringValue springValue = new SpringValue(key, value.value(), bean, beanName, method, false);
            springValueRegistry.register(beanFactory, key, springValue);
            logger.info("Monitoring {}", springValue);
        }
    }


    private void processBeanPropertyValues(Object bean, String beanName) {
        Collection<SpringValueDefinition> propertySpringValues = beanName2SpringValueDefinitions
                .get(beanName);
        if (propertySpringValues == null || propertySpringValues.isEmpty()) {
            return;
        }

        for (SpringValueDefinition definition : propertySpringValues) {
            try {
                PropertyDescriptor pd = BeanUtils
                        .getPropertyDescriptor(bean.getClass(), definition.getPropertyName());
                Method method = pd.getWriteMethod();
                if (method == null) {
                    continue;
                }
                SpringValue springValue = new SpringValue(definition.getKey(), definition.getPlaceholder(),
                        bean, beanName, method, false);
                springValueRegistry.register(beanFactory, definition.getKey(), springValue);
                logger.debug("Monitoring {}", springValue);
            } catch (Throwable ex) {
                logger.error("Failed to enable auto update feature for {}.{}", bean.getClass(),
                        definition.getPropertyName());
            }
        }

        // clear
        beanName2SpringValueDefinitions.removeAll(beanName);
    }

    @Override
    public void setBeanFactory(BeanFactory beanFactory) throws BeansException {
        this.beanFactory = beanFactory;
    }
}
