package com.lyra.user.service.impl;

import com.lyra.spring.BeanProcessor;
import com.lyra.spring.Component;

@Component("lyraBeanProcessor")
public class LyraBeanProcessor implements BeanProcessor {
    @Override
    public Object postProcessBeforeInitialization(Object bean, String beanName) {
        System.out.println("before");
        return bean;
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) {
        System.out.println("after");
        return bean;
    }
}
