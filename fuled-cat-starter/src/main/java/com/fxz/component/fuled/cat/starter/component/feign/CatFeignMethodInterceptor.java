package com.fxz.component.fuled.cat.starter.component.feign;

import com.dianping.cat.Cat;
import com.dianping.cat.message.Transaction;
import com.fxz.component.fuled.cat.starter.annotation.IgnoreCatTracing;
import com.fxz.component.fuled.cat.starter.util.CatUtils;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.springframework.cloud.openfeign.FeignClient;

import java.lang.reflect.Method;
import java.util.Objects;

public class CatFeignMethodInterceptor implements MethodInterceptor {
    public CatFeignMethodInterceptor() {
    }

    @Override
    public Object invoke(MethodInvocation invocation) throws Throwable {
        Method method = invocation.getMethod();
        Class clazz = method.getDeclaringClass();
        if (clazz.isAssignableFrom(Object.class)) {
            return invocation.proceed();
        } else {
            FeignClient feignClient = (FeignClient) clazz.getAnnotation(FeignClient.class);
            IgnoreCatTracing ignoreCatTracing = (IgnoreCatTracing) method.getAnnotation(IgnoreCatTracing.class);
            if (ignoreCatTracing == null) {
                ignoreCatTracing = (IgnoreCatTracing) clazz.getAnnotation(IgnoreCatTracing.class);
            }

            if (!Objects.isNull(feignClient) && !Objects.nonNull(ignoreCatTracing)) {
                Transaction transaction = Cat.newTransaction("RemoteCall", clazz.getSimpleName() + "." + method.getName());
                CatUtils.createConsumerCross(transaction, feignClient.value(), "", "");
                CatUtils.createMessageTree();

                Object var8;
                try {
                    Object proceed = invocation.proceed();
                    transaction.setStatus("0");
                    var8 = proceed;
                } catch (Throwable var12) {
                    transaction.setStatus(var12);
                    throw var12;
                } finally {
                    transaction.complete();
                }

                return var8;
            } else {
                return invocation.proceed();
            }
        }
    }
}
