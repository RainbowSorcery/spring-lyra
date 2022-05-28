package com.lyra.spring;

public interface InitializingBean {
    void afterPropertiesSet() throws Exception;
}
