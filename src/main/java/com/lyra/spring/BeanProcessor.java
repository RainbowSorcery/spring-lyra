package com.lyra.spring;

public interface BeanProcessor {
    default Object postProcessBeforeInitialization(Object bean, String beanName) {
        System.out.println("before");
        return bean;
    }

    default Object postProcessAfterInitialization(Object bean, String beanName) {
        System.out.println("after");
        return bean;
    }
}
