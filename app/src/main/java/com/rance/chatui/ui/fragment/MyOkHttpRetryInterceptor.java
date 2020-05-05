package com.rance.chatui.ui.fragment;

import java.io.IOException;
import java.io.InterruptedIOException;

import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;

/*
    OKHTTP失败重试和重定向需求
 */
public class MyOkHttpRetryInterceptor implements Interceptor {
    public int executionCount;//最大重试次数
    private long retryInterval;//重试的间隔
    MyOkHttpRetryInterceptor(Builder builder) {
        this.executionCount = builder.executionCount;
        this.retryInterval = builder.retryInterval;
    }
    public Response intercept(Chain chain) throws IOException {
        Request request = chain.request();
        Response response = doRequest(chain, request);
        int retryNum = 0;
        while ((response == null || !response.isSuccessful()) && retryNum <= executionCount) {
//            Log.info("intercept Request is not successful - {}",retryNum);
            final long nextInterval = getRetryInterval();
            try {
//                Log.info("Wait for {}",nextInterval);
                Thread.sleep(nextInterval);
            } catch (final InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new InterruptedIOException();
            }
            retryNum++;
            // retry the request
            response = doRequest(chain, request);
        }
        return response;
    }
    private Response doRequest(Chain chain, Request request) {
        Response response = null;
        try {
            response = chain.proceed(request);
        } catch (Exception e) {
        }
        return response;
    }
    /**
     * retry间隔时间
     */
    public long getRetryInterval() {
        return this.retryInterval;
    }
    public static final class Builder {
        private int executionCount;
        private long retryInterval;
        public Builder() {
            executionCount = 3;
            retryInterval = 1000;
        }
        public MyOkHttpRetryInterceptor.Builder executionCount(int executionCount){
            this.executionCount =executionCount;
            return this;
        }
        public MyOkHttpRetryInterceptor.Builder retryInterval(long retryInterval){
            this.retryInterval =retryInterval;
            return this;
        }
        public MyOkHttpRetryInterceptor build() {
            return new MyOkHttpRetryInterceptor(this);
        }
    }
}

