# 执行流程
从上一章分析可得，spring创建bean的流程如下所示
获取class信息 -> 根据class信息调用构造方法创建对象 -> 判断成员变量中是否有依赖注入注解并进行注入操作 -> 初始化前(@PostConstruct) -> 初始化(实现InitializingBean接口) -> 初始化后(AOP) -> 生成代理对象 -> Bean

# 扫描bean
扫描bean主要根据包名获取类信息以及判断用户创建的类是否继承`BeanProcessor`接口以及将该接口放入到spring容器中，并将该信息存储到map集合中。
类信息包含bean名称、单例还是原型、是否懒加载。
BeanProcessor接口用于处理初始化前和初始化后的方法。

首先根据传入的类class判断该类是否有`ComponentScan`注解，如果有就根据该注解获取要扫描的包。
扫描类时加载的并不是.java文件而是编译后的二进制字节码class文件，想要加载该文件需要找到编译后的文件目录，可以根据应用程序类加载器获取该目录，调用resouces方法获取包目录下的url，获取前得先将包名`.`替换成'/'，这样才能表示目录，可以根据该url来创建file对象，之后判断file对象是否为目录，如果是目录就扫描该目录下的所有文件，如下代码所示
```java
        if (aClass.isAnnotationPresent(ComponentScan.class)) {
            ComponentScan componentScan = (ComponentScan) aClass.getAnnotation(ComponentScan.class);
            String packagePath = componentScan.value();
            packagePath = packagePath.replace(".", "/");

            URL resource = LyraApplicationContext.class.getClassLoader().getResource(packagePath);

            assert resource != null;
            File file = new File(URLDecoder.decode(resource.getFile(), StandardCharsets.UTF_8));

            if (file.isDirectory()) {
			}
	}
```
之后遍历该目录下的所有文件，然后判断文件中是否有`Component`注解，如果有该注解那么就表示这个类是一个bean，在然后判断包中的类是否实现`BeanProcessor`接口，如果有类实现了则将该类实例化并存储到map中。
```java
                 if (aClass1.isAnnotationPresent(Component.class)) {
                            if (BeanProcessor.class.isAssignableFrom(aClass1)) {
                                BeanProcessor instance = (BeanProcessor) aClass1.getDeclaredConstructor().newInstance();
                                beanProcessorList.add(instance);
                            }
                        }
```
之后就该获取类信息了，类信息所在类具体由下所示，类中存储的数据由bean的class、是否为单例和bean名称，单独抽出BeanDefinition的原因是在扫描阶段和获取bean阶段都需要扫描类从而获取类是否为单例，这样使代码重复且效率较低，不如创建个类信息类，在扫描类时获取类信息保存，之后获取bean时直接从BeanDefinition中获取即可。
```java
public class BeanDefinition {
    private Class<?> clazz;
    private BeanScopeConstant scope;

    private String beanName;

    @Override
    public String toString() {
        return "BeanDefinition{" +
                "clazz=" + clazz +
                ", scope=" + scope +
                ", beanName='" + beanName + '\'' +
                '}';
    }

    public String getBeanName() {
        return beanName;
    }

    public void setBeanName(String beanName) {
        this.beanName = beanName;
    }

    public Class<?> getClazz() {
        return clazz;
    }

    public void setClazz(Class<?> clazz) {
        this.clazz = clazz;
    }

    public BeanScopeConstant getScope() {
        return scope;
    }

    public void setScope(BeanScopeConstant scope) {
        this.scope = scope;
    }
}
```
获取类信息时判断是否有Scope注解，如果有该注解则将该注解的信息保存到BeanDefinition中，如果没有则默认为单例，之后将bean名称和class信息保存到BeanDefinition中即可，再然后将BeanDefinition保存到map中，map的key为beanName。bean扫描的整体代码如下所示
```java
    private void scanPackage() {
        // 判断类是否有componentScan注解
        if (aClass.isAnnotationPresent(ComponentScan.class)) {
            ComponentScan componentScan = (ComponentScan) aClass.getAnnotation(ComponentScan.class);
            String packagePath = componentScan.value();
            packagePath = packagePath.replace(".", "/");

            URL resource = LyraApplicationContext.class.getClassLoader().getResource(packagePath);

            assert resource != null;
            File file = new File(URLDecoder.decode(resource.getFile(), StandardCharsets.UTF_8));

            // 判断类文件是否为目录
            if (file.isDirectory()) {
                for (File file1 : Objects.requireNonNull(file.listFiles())) {
                    try {
                        // 将包名.替换为/
                        String componentPackagePath = file1.getAbsolutePath().substring(file1.getAbsolutePath().indexOf("com"), file1.getAbsolutePath().indexOf(".class")).replace("\\", ".");
                        BeanDefinition beanDefinition = new BeanDefinition();
                        // 获取类编译目录
                        Class<?> aClass1 = LyraApplicationContext.class.getClassLoader().loadClass(componentPackagePath);
                        // 加载类 判断类是否有component注解
                        if (aClass1.isAnnotationPresent(Component.class)) {
                            // 判断当前类是否实现了BeanProcessor接口
                            if (BeanProcessor.class.isAssignableFrom(aClass1)) {
                                BeanProcessor instance = (BeanProcessor) aClass1.getDeclaredConstructor().newInstance();
                                beanProcessorList.add(instance);
                            }
                            // 在类信息中保存类scope 单例/原型
                            if (aClass1.isAnnotationPresent(Scope.class)) {
                                Scope annotation = aClass1.getAnnotation(Scope.class);

                                beanDefinition.setScope(annotation.value());
                            } else {
                                beanDefinition.setScope(BeanScopeConstant.SINGLETON);
                            }
                            // 设置类信息
                            beanDefinition.setBeanName(aClass1.getAnnotation(Component.class).value());
                            beanDefinition.setClazz(aClass1);

                            beanDefinitionMap.put(aClass1.getAnnotation(Component.class).value(), beanDefinition);
                        }
                    } catch (ClassNotFoundException | NoSuchMethodException | IllegalAccessException |
                             InstantiationException | InvocationTargetException e) {
                        throw new RuntimeException(e);
                    }
                }
            }
        }
    }
```
# bean创建
## 单例
扫描类完毕之后会根据扫描类的scope来创建bean，如果类的scope为单例则立即创建则保存到保存到单例池中。
```java
    public LyraApplicationContext(Class<ApplicationConfig> applicationConfigClass) {
        this.aClass = applicationConfigClass;

        scanPackage();

        for (Map.Entry<String, BeanDefinition> stringBeanDefinitionEntry : beanDefinitionMap.entrySet()) {
            String beanName = stringBeanDefinitionEntry.getKey();
            BeanScopeConstant scope = stringBeanDefinitionEntry.getValue().getScope();

            if (scope == BeanScopeConstant.SINGLETON) {
                Object singletonBean = createBean(beanName, stringBeanDefinitionEntry.getValue());
                singletonMap.put(beanName, singletonBean);
            }
        }

    }
```
## 原型
如果类的scope为原型则每次调用 getBean方法都会创建不同的bean返回
获取bean中首先从bean信息map中根据bean名称获取bean信息，如果不存在则表示该类没有被扫描到容器中，获取单例bean时，如果单例bean还未创建则调用getbean创建bean并扫描到单例池中
```java
    public Object getBean(String beanName) {
        BeanDefinition beanDefinition = beanDefinitionMap.get(beanName);

        if (beanDefinition == null) {
            throw new NullPointerException();
        }

        if (beanDefinition.getScope() == BeanScopeConstant.PROTOTYPE) {
            return createBean(beanName, beanDefinition);
        } else {
            Object bean = singletonMap.get(beanName);
            if (bean == null) {
                bean = createBean(beanName, beanDefinition);
                singletonMap.put(beanName, bean);
            }
            return bean;
        }

    }
```

# 创建bean
创建bean的流程为
获取class信息 -> 根据class信息调用构造方法创建对象 -> 判断成员变量中是否有依赖注入注解并进行注入操作 -> 初始化前(@PostConstruct) -> 初始化(实现InitializingBean接口) -> 初始化后(AOP) -> 生成代理对象 -> Bean
1. 获取类信息
   扫描时已经将类信息保存到beanDefinition中，直接从map中获取beanDefinition即可
```java
       Class<?> clazz = beanDefinition.getClazz();
```
2. 利用反射调用构造方法创建bean
   这里调用的是无参构造方法，没有构造方法推理，还略微有些问题
```java
            Object instance = clazz.getConstructor().newInstance();
```
3. 查看bean字段中有没有被`Autowired`注解标识，如果有则调用geatBean方法为字段注入值，这里有个坑，如果扫描的bean为单例bean，虽然标识了Component注解，创建bean在注入之前，容器还没来得及创建就已经执行注入操作，肯定会注入失败的，这就在getBean方法获取单例bean时获取，如果单例bean还未创建则调用getbean创建bean并扫描到单例池中
```java
        for (Field field : clazz.getDeclaredFields()) {
                if (field.isAnnotationPresent(Autowired.class)) {
                    field.setAccessible(true);
                    field.set(instance, getBean(field.getName()));
                }
            }
```
4. 调用初始化前方法
   `BeanProcessor`中封装了两个方法，初始化前和初始化后的方法，这个接口很重要，因为AOP代理对象就是从这个接口的实现类创建的，通过对bean的处理，然后返回代理对象
```java
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
```
执行初始化前方法，这个list填充是由扫描bean时完成的，所以不为空
```java
            beanProcessorList.forEach((beanProcessor -> {
                beanProcessor.postProcessBeforeInitialization(instance, beanName);
            }));
```
5. 调用初始化方法
   初始化方法是由实现InitializingBean接口的bean完成的，直接判断当前bean是否实现了`InitializingBean`接口即可，如果实现了之江将bean强转为InitializingBean然后调用初始化方法即可。
```java
        if (instance instanceof InitializingBean) {
                ((InitializingBean) instance).afterPropertiesSet();
            }
```
6. 调用初始化后方法
```java

            beanProcessorList.forEach((beanProcessor -> {
                beanProcessor.postProcessAfterInitialization(instance, beanName);
            }));
```
创建bean完整代码
```java
    private Object createBean(String beanName, BeanDefinition beanDefinition) {
        Class<?> clazz = beanDefinition.getClazz();

        try {
            Object instance = clazz.getConstructor().newInstance();

            for (Field field : clazz.getDeclaredFields()) {
                if (field.isAnnotationPresent(Autowired.class)) {
                    field.setAccessible(true);
                    field.set(instance, getBean(field.getName()));
                }
            }

            beanProcessorList.forEach((beanProcessor -> {
                beanProcessor.postProcessBeforeInitialization(instance, beanName);
            }));

            if (instance instanceof InitializingBean) {
                ((InitializingBean) instance).afterPropertiesSet();
            }

            beanProcessorList.forEach((beanProcessor -> {
                beanProcessor.postProcessAfterInitialization(instance, beanName);
            }));


            return instance;

        } catch (Exception e) {
            throw new RuntimeException(e);
        }

    }
```