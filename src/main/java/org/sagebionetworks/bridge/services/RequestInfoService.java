package org.sagebionetworks.bridge.services;

import static com.google.common.base.Preconditions.checkNotNull;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import org.sagebionetworks.bridge.cache.CacheProvider;
import org.sagebionetworks.bridge.dao.RequestInfoDao;
import org.sagebionetworks.bridge.models.RequestInfo;

@Component
public class RequestInfoService {
    
    private CacheProvider cacheProvider;
    private RequestInfoDao requestInfoDao;
    
    @Autowired
    final void setCacheProvider(CacheProvider cacheProvider) {
        this.cacheProvider = cacheProvider;
    }
    @Autowired
    final void setRequestInfoDao(RequestInfoDao requestInfoDao) {
        this.requestInfoDao = requestInfoDao;
    }
    
    public void updateRequestInfo(RequestInfo requestInfo) {
        checkNotNull(requestInfo);
        
        requestInfoDao.updateRequestInfo(requestInfo);
    }
    
    public RequestInfo getRequestInfo(String userId) {
        isNotBlank(userId);
        
        RequestInfo requestInfo = requestInfoDao.getRequestInfo(userId);
        if (requestInfo == null) {
            requestInfo = cacheProvider.getRequestInfo(userId);
            if (requestInfo != null) {
                requestInfoDao.updateRequestInfo(requestInfo);
            }
        }
        return requestInfo;
    }
    
    public void removeRequestInfo(String userId) {
        isNotBlank(userId);
        
        requestInfoDao.removeRequestInfo(userId);
        cacheProvider.removeRequestInfo(userId);
    }
}
